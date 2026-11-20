package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Hardware3DType {
    CARTRIDGE,
    JEWEL_CASE,
    MINI_ARCADE,
}

fun resolveHardware3DType(systemId: String): Hardware3DType {
    return when (systemId) {
        "fbneo", "mame2003plus" -> Hardware3DType.MINI_ARCADE
        "psx", "psp", "nds", "segacd", "gc", "3ds" -> Hardware3DType.JEWEL_CASE
        else -> Hardware3DType.CARTRIDGE
    }
}

fun resolveHardwareShellColor(systemId: String): FloatArray {
    val argb =
        when (systemId) {
            // Cartridges
            "snes" -> 0xFFB8B8C0.toInt()
            "n64" -> 0xFF4A4E57.toInt()
            "md" -> 0xFF22242A.toInt()
            "nes" -> 0xFF9EA3AB.toInt()
            "gba" -> 0xFF3C4257.toInt()
            "gb" -> 0xFFC8C8C0.toInt()
            "gbc" -> 0xFF4B3D7A.toInt()
            "sms", "gg", "atari2600" -> 0xFF252830.toInt()
            "lynx" -> 0xFF323640.toInt()
            "pce" -> 0xFFE2E0D8.toInt()
            // Jewel case spine colors
            "psx" -> 0xFF151820.toInt()
            "psp" -> 0xFF1B2332.toInt()
            "nds" -> 0xFFECEFF4.toInt()
            "3ds" -> 0xFFF2F4F8.toInt()
            "segacd" -> 0xFF162547.toInt()
            "gc" -> 0xFF3F317A.toInt()
            // Mini arcade cabinet body
            "fbneo", "mame2003plus" -> 0xFF212634.toInt()
            else -> 0xFF454954.toInt()
        }
    return floatArrayOf(
        ((argb ushr 16) and 0xFF) / 255f,
        ((argb ushr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb ushr 24) and 0xFF) / 255f,
    )
}

/**
 * Real OpenGL ES 2.0 solid 3D hardware renderer for Lemuroid's `THREE_D` theme.
 *
 * Builds multi-part procedural 3D meshes for cartridges, jewel cases, and MiniFliper
 * upright arcade cabinets with directional key/fill/rim lighting, Blinn-Phong specular
 * highlights, and aspect-preserving UV-mapped cover textures.
 */
class Hardware3DRenderer(
    hardwareType: Hardware3DType,
    shellColor: FloatArray,
    spinning: Boolean = true,
) : GLSurfaceView.Renderer {
    private val stateLock = Any()

    @Volatile
    private var pendingType: Hardware3DType = hardwareType

    @Volatile
    private var pendingShellColor: FloatArray = normalizeRgba(shellColor)

    @Volatile
    private var isSpinning: Boolean = spinning

    private var activeType: Hardware3DType? = null
    private var activeShellColor: FloatArray? = null
    private var meshDirty: Boolean = true

    private var pendingCoverBitmap: Bitmap? = null
    private var hasPendingBitmap: Boolean = false
    private var hasUploadedTexture: Boolean = false

    private var programId: Int = 0
    private var textureId: Int = 0

    // Shader locations
    private var aPositionLoc: Int = -1
    private var aNormalLoc: Int = -1
    private var aTexCoordLoc: Int = -1
    private var uMVPMatrixLoc: Int = -1
    private var uModelMatrixLoc: Int = -1
    private var uColorLoc: Int = -1
    private var uUseTextureLoc: Int = -1
    private var uShininessLoc: Int = -1
    private var uSpecStrengthLoc: Int = -1
    private var uEmissiveLoc: Int = -1
    private var uTextureLoc: Int = -1
    private var uCameraPosLoc: Int = -1

    // Matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewportAspect: Float = 1f
    private var angleY: Float = -22f
    private var lastFrameNanos: Long = 0L

    private var meshParts: List<MeshPart> = emptyList()

    /**
     * Thread-safely updates the target 3D hardware geometry and shell color.
     * The active mesh is rebuilt in-place on the GL thread in [onDrawFrame] without
     * recreating the OpenGL context or [GLSurfaceView].
     */
    fun updateHardware(
        type: Hardware3DType,
        shellColor: FloatArray,
    ) {
        val normalized = normalizeRgba(shellColor)
        synchronized(stateLock) {
            if (pendingType != type || !pendingShellColor.contentEquals(normalized)) {
                pendingType = type
                pendingShellColor = normalized
                meshDirty = true
            }
        }
    }

    fun setSpinning(spinning: Boolean) {
        synchronized(stateLock) {
            isSpinning = spinning
        }
    }

    /**
     * Thread-safely queues a cover [Bitmap] to be uploaded to OpenGL on the next frame.
     * The source bitmap is letterboxed onto a clean 512x512 dark canvas so cover artwork
     * is never stretched or cropped regardless of its original aspect ratio.
     * Any superseded pending letterboxed bitmap is immediately recycled to prevent leaks.
     */
    fun setCoverBitmap(bitmap: Bitmap?) {
        val prepared =
            if (bitmap != null && !bitmap.isRecycled) {
                prepareLetterboxedTexture(bitmap)
            } else {
                null
            }
        synchronized(stateLock) {
            val previous = pendingCoverBitmap
            pendingCoverBitmap = prepared
            hasPendingBitmap = true
            if (previous != null && previous !== prepared && !previous.isRecycled) {
                previous.recycle()
            }
        }
    }

    /**
     * Recycles any pending cover bitmap that has not yet been uploaded to OpenGL.
     * Call when the hosting [GLSurfaceView] is released.
     */
    fun release() {
        synchronized(stateLock) {
            pendingCoverBitmap?.let { bmp ->
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
            pendingCoverBitmap = null
            hasPendingBitmap = false
        }
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        GLES20.glClearColor(0.05f, 0.06f, 0.09f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
        uUseTextureLoc = GLES20.glGetUniformLocation(programId, "uUseTexture")
        uShininessLoc = GLES20.glGetUniformLocation(programId, "uShininess")
        uSpecStrengthLoc = GLES20.glGetUniformLocation(programId, "uSpecStrength")
        uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Upload a clean 4x4 dark fallback texture so the textured face never renders black/corrupt
        uploadSolidFallbackTexture()
        hasUploadedTexture = false

        synchronized(stateLock) {
            meshDirty = true
        }
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int,
    ) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, safeW, safeH)
        viewportAspect = safeW.toFloat() / safeH.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 36f, viewportAspect, 1.0f, 20.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val type: Hardware3DType
        val shellColor: FloatArray
        val spinning: Boolean
        val rebuildMesh: Boolean
        val bitmapToUpload: Bitmap?
        val shouldProcessBitmap: Boolean

        synchronized(stateLock) {
            type = pendingType
            shellColor = pendingShellColor
            spinning = isSpinning
            rebuildMesh =
                meshDirty ||
                activeType != type ||
                activeShellColor == null ||
                !activeShellColor!!.contentEquals(shellColor)
            meshDirty = false
            shouldProcessBitmap = hasPendingBitmap
            bitmapToUpload = pendingCoverBitmap
            pendingCoverBitmap = null
            hasPendingBitmap = false
        }

        if (rebuildMesh) {
            meshParts = buildMeshForType(type, shellColor)
            activeType = type
            activeShellColor = shellColor.copyOf()
        }

        if (shouldProcessBitmap) {
            if (bitmapToUpload != null && !bitmapToUpload.isRecycled) {
                try {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapToUpload, 0)
                    hasUploadedTexture = true
                } finally {
                    if (!bitmapToUpload.isRecycled) {
                        bitmapToUpload.recycle()
                    }
                }
            } else {
                uploadSolidFallbackTexture()
                hasUploadedTexture = false
            }
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
        }
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        val rotX: Float
        val rotY: Float
        if (spinning) {
            angleY = (angleY + dt * 52f) % 360f
            rotX = 12f
            rotY = angleY
        } else {
            rotX = 12f
            rotY = -22f
        }

        val baseCamZ =
            when (type) {
                Hardware3DType.JEWEL_CASE -> 2.95f
                Hardware3DType.CARTRIDGE -> 3.15f
                Hardware3DType.MINI_ARCADE -> 3.95f
            }
        val aspectComp = if (viewportAspect < 0.9f) 0.9f / viewportAspect else 1f
        val cameraZ = baseCamZ * aspectComp
        val cameraY = if (type == Hardware3DType.MINI_ARCADE) 0.12f else 0.0f

        Matrix.setLookAtM(
            viewMatrix,
            0,
            0f,
            cameraY,
            cameraZ,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        GLES20.glUseProgram(programId)
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uCameraPosLoc, 0f, cameraY, cameraZ)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        for (part in meshParts) {
            val buf = part.vertexBuffer
            if (part.vertexCount <= 0 || buf.limit() < 8) continue
            buf.position(0)
            GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buf)
            buf.position(3)
            GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, buf)
            buf.position(6)
            GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, buf)

            GLES20.glUniform4fv(uColorLoc, 1, part.color, 0)
            GLES20.glUniform1f(uUseTextureLoc, if (part.useTexture && hasUploadedTexture) 1.0f else 0.0f)
            GLES20.glUniform1f(uShininessLoc, part.shininess)
            GLES20.glUniform1f(uSpecStrengthLoc, part.specStrength)
            GLES20.glUniform1f(uEmissiveLoc, part.emissive)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, part.vertexCount)
        }

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aNormalLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }

    private fun uploadSolidFallbackTexture() {
        val fallback = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        fallback.eraseColor(Color.rgb(20, 24, 34))
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, fallback, 0)
        fallback.recycle()
    }

    private fun prepareLetterboxedTexture(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(TEX_SIZE, TEX_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(16, 18, 26))

        val srcW = source.width.coerceAtLeast(1).toFloat()
        val srcH = source.height.coerceAtLeast(1).toFloat()
        val scale = min(TEX_SIZE / srcW, TEX_SIZE / srcH)
        val dstW = srcW * scale
        val dstH = srcH * scale
        val left = (TEX_SIZE - dstW) * 0.5f
        val top = (TEX_SIZE - dstH) * 0.5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + dstW, top + dstH), paint)
        return out
    }

    // -------------------------------------------------------------------------
    // Procedural 3D Hardware Meshes
    // -------------------------------------------------------------------------

    private fun buildMeshForType(
        type: Hardware3DType,
        shellColor: FloatArray,
    ): List<MeshPart> {
        return when (type) {
            Hardware3DType.JEWEL_CASE -> buildJewelCaseMesh(shellColor)
            Hardware3DType.CARTRIDGE -> buildCartridgeMesh(shellColor)
            Hardware3DType.MINI_ARCADE -> buildMiniArcadeMesh(shellColor)
        }
    }

    /**
     * Solid 3D CD Jewel Case:
     * realistic slim proportions (width = 1.18f, height = 1.18f, depth = 0.085f),
     * crisp thin acrylic edge rails, distinct left spine, and non-overlapping
     * recessed front cover & rear disc-tray details with >= 0.018f Z-separation.
     */
    private fun buildJewelCaseMesh(spineColor: FloatArray): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val w = 1.18f
        val h = 1.18f
        val d = 0.085f
        val spineW = 0.11f
        val railThick = 0.024f

        val innerLeft = -w * 0.5f + spineW
        val innerRight = w * 0.5f - railThick
        val innerW = innerRight - innerLeft
        val innerCx = (innerLeft + innerRight) * 0.5f
        val innerH = h - railThick * 2f

        // 1. Dark molded main tray & back shell (fits strictly inside spine & acrylic rails,
        //    recessed 0.020f behind front rail edge so cover quad sits cleanly separated)
        val trayD = d - 0.020f
        val trayCz = -0.010f
        val trayFrontZ = trayCz + trayD * 0.5f
        val trayBackZ = trayCz - trayD * 0.5f
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = innerCx, cy = 0f, cz = trayCz, w = innerW, h = innerH, d = trayD)
        parts +=
            bodyVerts.toPart(
                color = argbToFloat4(0xFF161922.toInt()),
                shininess = 40f,
                specStrength = 0.35f,
            )

        // 2. Distinct left spine (system-colored, non-overlapping butt-joint against tray & rails)
        val spineVerts = MeshBuilder()
        spineVerts.addBox(cx = -w * 0.5f + spineW * 0.5f, cy = 0f, cz = 0f, w = spineW, h = h, d = d)
        parts +=
            spineVerts.toPart(
                color = spineColor.copyOf(),
                shininess = 48f,
                specStrength = 0.40f,
            )

        // 3. Crisp thin glossy acrylic edge rails (top, bottom, right — strictly non-overlapping)
        val acrylicVerts = MeshBuilder()
        // Top rail
        acrylicVerts.addBox(
            cx = spineW * 0.5f,
            cy = h * 0.5f - railThick * 0.5f,
            cz = 0f,
            w = w - spineW,
            h = railThick,
            d = d,
        )
        // Bottom rail
        acrylicVerts.addBox(
            cx = spineW * 0.5f,
            cy = -h * 0.5f + railThick * 0.5f,
            cz = 0f,
            w = w - spineW,
            h = railThick,
            d = d,
        )
        // Right rail (fits strictly between top and bottom rails)
        acrylicVerts.addBox(
            cx = w * 0.5f - railThick * 0.5f,
            cy = 0f,
            cz = 0f,
            w = railThick,
            h = innerH,
            d = d,
        )
        parts +=
            acrylicVerts.toPart(
                color = floatArrayOf(0.82f, 0.87f, 0.94f, 1.0f),
                shininess = 96f,
                specStrength = 0.80f,
            )

        // 4. Front cover UV-mapped artwork quad (0.018f in front of trayFrontZ, inside acrylic frame)
        val coverVerts = MeshBuilder()
        coverVerts.addFrontQuad(
            cx = innerCx,
            cy = 0f,
            z = trayFrontZ + 0.018f,
            w = innerW - 0.014f,
            h = innerH - 0.014f,
        )
        parts +=
            coverVerts.toPart(
                color = argbToFloat4(0xFF1A1E2A.toInt()),
                useTexture = true,
                shininess = 72f,
                specStrength = 0.32f,
                emissive = 0.14f,
            )

        // 5. Rear molded disc-tray inlay & hub detail (each layer protrudes 0.018f cleanly along -Z)
        val inlayD = 0.018f
        val inlayBackZ = trayBackZ - inlayD
        val hubD = 0.018f
        val backDetailVerts = MeshBuilder()
        backDetailVerts.addBox(
            cx = innerCx,
            cy = 0f,
            cz = trayBackZ - inlayD * 0.5f,
            w = innerW * 0.82f,
            h = innerH * 0.84f,
            d = inlayD,
        )
        backDetailVerts.addCylinderZ(
            cx = innerCx,
            cy = -0.02f,
            cz = inlayBackZ - hubD * 0.5f,
            radius = 0.13f,
            depth = hubD,
            segments = 16,
        )
        parts +=
            backDetailVerts.toPart(
                color = argbToFloat4(0xFF232836.toInt()),
                shininess = 28f,
                specStrength = 0.25f,
            )

        return parts
    }

    /**
     * Multi-part Solid 3D Game Cartridge:
     * main body (1.25 x 1.10 x 0.26), stepped top grip lip/ridges,
     * lower connector block (height 0.22, depth 0.18), recessed UV-mapped front label
     * surrounded by a 4-strip non-overlapping bezel frame (>= 0.018f Z-separation).
     */
    private fun buildCartridgeMesh(shellColor: FloatArray): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val mainColor = shellColor.copyOf()
        val darkerShell = scaleRgb(mainColor, 0.72f)
        val lighterShell = scaleRgb(mainColor, 1.12f)

        val mainW = 1.25f
        val mainH = 1.10f
        val mainD = 0.26f
        val mainCy = 0.08f
        val frontZ = mainD * 0.5f
        val backZ = -mainD * 0.5f

        // 1. Main cartridge body + stepped top crown lip + lower connector block (non-overlapping)
        val shellVerts = MeshBuilder()
        shellVerts.addBox(cx = 0f, cy = mainCy, cz = 0f, w = mainW, h = mainH, d = mainD)
        // Stepped top grip lip (sits cleanly on top of main body)
        shellVerts.addBox(cx = 0f, cy = mainCy + mainH * 0.5f + 0.035f, cz = 0f, w = mainW * 0.88f, h = 0.07f, d = mainD * 0.88f)
        // Lower narrower connector block (sits cleanly beneath main body)
        val connH = 0.22f
        val connD = 0.18f
        val connCy = mainCy - mainH * 0.5f - connH * 0.5f
        shellVerts.addBox(cx = 0f, cy = connCy, cz = 0f, w = mainW * 0.78f, h = connH, d = connD)
        parts +=
            shellVerts.toPart(
                color = mainColor,
                shininess = 34f,
                specStrength = 0.34f,
            )

        // 2. Top grip ridges (front & back), 4-strip label surround bezel, and rear screw bosses
        val ridgeVerts = MeshBuilder()
        val ridgeW = mainW * 0.72f
        val ridgeD = 0.022f
        for (i in 0 until 3) {
            val ry = mainCy + mainH * 0.38f - i * 0.055f
            ridgeVerts.addBox(cx = 0f, cy = ry, cz = frontZ + ridgeD * 0.5f, w = ridgeW, h = 0.024f, d = ridgeD)
            ridgeVerts.addBox(cx = 0f, cy = ry, cz = backZ - ridgeD * 0.5f, w = ridgeW, h = 0.024f, d = ridgeD)
        }
        // Recessed front label 4-strip bezel frame (zero overlap with front label quad)
        val labelCy = mainCy - 0.10f
        val labelW = 0.90f
        val labelH = 0.68f
        val frameThick = 0.032f
        val frameD = 0.024f
        val frameCz = frontZ + frameD * 0.5f
        // Top bezel strip
        ridgeVerts.addBox(
            cx = 0f,
            cy = labelCy + labelH * 0.5f + frameThick * 0.5f,
            cz = frameCz,
            w = labelW + frameThick * 2f,
            h = frameThick,
            d = frameD,
        )
        // Bottom bezel strip
        ridgeVerts.addBox(
            cx = 0f,
            cy = labelCy - labelH * 0.5f - frameThick * 0.5f,
            cz = frameCz,
            w = labelW + frameThick * 2f,
            h = frameThick,
            d = frameD,
        )
        // Left bezel strip
        ridgeVerts.addBox(
            cx = -labelW * 0.5f - frameThick * 0.5f,
            cy = labelCy,
            cz = frameCz,
            w = frameThick,
            h = labelH,
            d = frameD,
        )
        // Right bezel strip
        ridgeVerts.addBox(
            cx = labelW * 0.5f + frameThick * 0.5f,
            cy = labelCy,
            cz = frameCz,
            w = frameThick,
            h = labelH,
            d = frameD,
        )
        // Rear molded screw bosses (protruding 0.020f from backZ)
        val bossD = 0.020f
        ridgeVerts.addBox(cx = -0.44f, cy = mainCy + 0.22f, cz = backZ - bossD * 0.5f, w = 0.07f, h = 0.07f, d = bossD)
        ridgeVerts.addBox(cx = 0.44f, cy = mainCy + 0.22f, cz = backZ - bossD * 0.5f, w = 0.07f, h = 0.07f, d = bossD)
        ridgeVerts.addBox(cx = 0f, cy = mainCy - 0.42f, cz = backZ - bossD * 0.5f, w = 0.07f, h = 0.07f, d = bossD)
        parts +=
            ridgeVerts.toPart(
                color = darkerShell,
                shininess = 24f,
                specStrength = 0.22f,
            )

        // 3. Rear caution/serial embossed label plate (protruding 0.018f from backZ, clear of bosses)
        val rearLabelD = 0.018f
        val rearLabelVerts = MeshBuilder()
        rearLabelVerts.addBox(
            cx = 0f,
            cy = mainCy - 0.06f,
            cz = backZ - rearLabelD * 0.5f,
            w = 0.76f,
            h = 0.44f,
            d = rearLabelD,
        )
        parts +=
            rearLabelVerts.toPart(
                color = lighterShell,
                shininess = 20f,
                specStrength = 0.18f,
            )

        // 4. Bottom PCB edge connector slot & gold contacts (protruding 0.022f below connector)
        val pcbH = 0.022f
        val pcbVerts = MeshBuilder()
        pcbVerts.addBox(
            cx = 0f,
            cy = connCy - connH * 0.5f - pcbH * 0.5f,
            cz = 0f,
            w = mainW * 0.66f,
            h = pcbH,
            d = connD * 0.45f,
        )
        parts +=
            pcbVerts.toPart(
                color = argbToFloat4(0xFFC9A243.toInt()),
                shininess = 80f,
                specStrength = 0.70f,
            )

        // 5. Recessed front UV-mapped cover label quad (0.018f in front of frontZ, framed by bezel)
        val labelVerts = MeshBuilder()
        labelVerts.addFrontQuad(
            cx = 0f,
            cy = labelCy,
            z = frontZ + 0.018f,
            w = labelW - 0.010f,
            h = labelH - 0.010f,
        )
        parts +=
            labelVerts.toPart(
                color = argbToFloat4(0xFF141822.toInt()),
                useTexture = true,
                shininess = 56f,
                specStrength = 0.28f,
                emissive = 0.14f,
            )

        return parts
    }

    /**
     * Multi-part Solid 3D MiniFliper Upright Arcade Cabinet:
     * lower base with coin-door, protruding control deck with 3D joystick post + red ball +
     * colored buttons, upper screen housing with 4-strip recessed CRT bezel & UV-mapped screen quad,
     * and overhanging glowing top marquee box. All stacked parts are strictly non-overlapping
     * with >= 0.018f separation to eliminate 16-bit depth-buffer Z-fighting.
     */
    private fun buildMiniArcadeMesh(cabinetColor: FloatArray): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val cabColor = cabinetColor.copyOf()
        val deckColor = scaleRgb(cabColor, 1.35f)
        val darkMetal = argbToFloat4(0xFF10131C.toInt())

        // Vertical layout (strictly stacked, zero overlap):
        // Lower base:     y in [-0.90, -0.05] (h = 0.85, cy = -0.475)
        // Control deck:   y in [-0.05, +0.09] (h = 0.14, cy =  0.020)
        // Upper housing:  y in [+0.09, +0.84] (h = 0.75, cy =  0.465)
        // Marquee shell:  y in [+0.84, +1.08] (h = 0.24, cy =  0.960)

        // 1. Main cabinet structure: lower base + upper screen housing + overhanging marquee shell
        val cabVerts = MeshBuilder()
        // Lower cabinet base: w = 1.05, h = 0.85, d = 0.72 (z in [-0.36, +0.36])
        cabVerts.addBox(cx = 0f, cy = -0.475f, cz = 0f, w = 1.05f, h = 0.85f, d = 0.72f)
        // Upper screen housing: w = 1.05, h = 0.75, d = 0.64 (z in [-0.36, +0.28])
        cabVerts.addBox(cx = 0f, cy = 0.465f, cz = -0.04f, w = 1.05f, h = 0.75f, d = 0.64f)
        // Overhanging top marquee housing: w = 1.07, h = 0.24, d = 0.80 (z in [-0.38, +0.42])
        cabVerts.addBox(cx = 0f, cy = 0.96f, cz = 0.02f, w = 1.07f, h = 0.24f, d = 0.80f)
        parts +=
            cabVerts.toPart(
                color = cabColor,
                shininess = 32f,
                specStrength = 0.30f,
            )

        // 2. Protruding control deck (z in [-0.34, +0.56], cleanly inset from backZ = -0.36)
        val deckVerts = MeshBuilder()
        deckVerts.addBox(cx = 0f, cy = 0.02f, cz = 0.11f, w = 1.09f, h = 0.14f, d = 0.90f)
        parts +=
            deckVerts.toPart(
                color = deckColor,
                shininess = 42f,
                specStrength = 0.38f,
            )

        // 3. Red arcade T-molding side trim rails (outside x = ±0.525) + coin return accents
        val trimVerts = MeshBuilder()
        val trimX = 0.525f + 0.014f
        trimVerts.addBox(cx = -trimX, cy = -0.475f, cz = 0.366f, w = 0.028f, h = 0.85f, d = 0.028f)
        trimVerts.addBox(cx = trimX, cy = -0.475f, cz = 0.366f, w = 0.028f, h = 0.85f, d = 0.028f)
        trimVerts.addBox(cx = -trimX, cy = 0.465f, cz = 0.286f, w = 0.028f, h = 0.75f, d = 0.028f)
        trimVerts.addBox(cx = trimX, cy = 0.465f, cz = 0.286f, w = 0.028f, h = 0.75f, d = 0.028f)
        // Coin door glowing red reject buttons (protruding 0.020f ahead of coin door front at 0.396)
        trimVerts.addBox(cx = -0.09f, cy = -0.46f, cz = 0.406f, w = 0.065f, h = 0.075f, d = 0.020f)
        trimVerts.addBox(cx = 0.09f, cy = -0.46f, cz = 0.406f, w = 0.065f, h = 0.075f, d = 0.020f)
        parts +=
            trimVerts.toPart(
                color = argbToFloat4(0xFFE53935.toInt()),
                shininess = 64f,
                specStrength = 0.55f,
                emissive = 0.25f,
            )

        // 4. Coin-door front panel, 4-strip recessed CRT bezel, joystick dust washer, rear service door
        val metalVerts = MeshBuilder()
        // Coin door (protruding 0.036f from lower front z = 0.36 -> front at 0.396)
        metalVerts.addBox(cx = 0f, cy = -0.48f, cz = 0.378f, w = 0.44f, h = 0.38f, d = 0.036f)
        // 4-strip recessed CRT screen bezel (protruding 0.028f from upper front z = 0.28, zero overlap with screen)
        val screenCy = 0.465f
        val screenW = 0.84f
        val screenH = 0.56f
        val bezelThick = 0.042f
        val bezelD = 0.028f
        val bezelCz = 0.28f + bezelD * 0.5f
        metalVerts.addBox(
            cx = 0f,
            cy = screenCy + screenH * 0.5f + bezelThick * 0.5f,
            cz = bezelCz,
            w = screenW + bezelThick * 2f,
            h = bezelThick,
            d = bezelD,
        )
        metalVerts.addBox(
            cx = 0f,
            cy = screenCy - screenH * 0.5f - bezelThick * 0.5f,
            cz = bezelCz,
            w = screenW + bezelThick * 2f,
            h = bezelThick,
            d = bezelD,
        )
        metalVerts.addBox(
            cx = -screenW * 0.5f - bezelThick * 0.5f,
            cy = screenCy,
            cz = bezelCz,
            w = bezelThick,
            h = screenH,
            d = bezelD,
        )
        metalVerts.addBox(
            cx = screenW * 0.5f + bezelThick * 0.5f,
            cy = screenCy,
            cz = bezelCz,
            w = bezelThick,
            h = screenH,
            d = bezelD,
        )
        // Joystick dust washer base on control deck (deck top y = 0.09 -> washer top y = 0.108)
        metalVerts.addCylinderY(cx = -0.26f, cy = 0.099f, cz = 0.42f, radius = 0.075f, height = 0.018f, segments = 14)
        // Rear service door (protruding 0.020f behind lower back z = -0.36)
        metalVerts.addBox(cx = 0f, cy = -0.38f, cz = -0.370f, w = 0.62f, h = 0.54f, d = 0.020f)
        parts +=
            metalVerts.toPart(
                color = darkMetal,
                shininess = 28f,
                specStrength = 0.25f,
            )

        // 5. Chrome joystick post (sits cleanly on top of dust washer y = 0.108)
        val postVerts = MeshBuilder()
        postVerts.addCylinderY(cx = -0.26f, cy = 0.173f, cz = 0.42f, radius = 0.020f, height = 0.13f, segments = 12)
        parts +=
            postVerts.toPart(
                color = floatArrayOf(0.82f, 0.86f, 0.92f, 1.0f),
                shininess = 96f,
                specStrength = 0.85f,
            )

        // 6. Glossy 3D red joystick ball
        val ballVerts = MeshBuilder()
        ballVerts.addSphere(cx = -0.26f, cy = 0.255f, cz = 0.42f, radius = 0.056f, stacks = 10, slices = 14)
        parts +=
            ballVerts.toPart(
                color = argbToFloat4(0xFFE5192A.toInt()),
                shininess = 96f,
                specStrength = 0.90f,
                emissive = 0.08f,
            )

        // 7. Colorful 3D arcade buttons on the right of the control deck (deck top y = 0.09)
        val buttonSpecs =
            listOf(
                Triple(0.08f, 0.45f, 0xFFFF4D4D.toInt()),
                Triple(0.22f, 0.42f, 0xFF3D8BFF.toInt()),
                Triple(0.36f, 0.45f, 0xFFFFD54F.toInt()),
                Triple(0.22f, 0.30f, 0xFF4CE896.toInt()),
            )
        for ((bx, bz, colorInt) in buttonSpecs) {
            val btnVerts = MeshBuilder()
            btnVerts.addCylinderY(cx = bx, cy = 0.108f, cz = bz, radius = 0.044f, height = 0.036f, segments = 12)
            parts +=
                btnVerts.toPart(
                    color = argbToFloat4(colorInt),
                    shininess = 72f,
                    specStrength = 0.65f,
                    emissive = 0.12f,
                )
        }

        // 8. Front CRT screen quad (0.018f ahead of upper front z = 0.28, cleanly framed by 4-strip bezel)
        val screenVerts = MeshBuilder()
        screenVerts.addFrontQuad(
            cx = 0f,
            cy = screenCy,
            z = 0.28f + 0.018f,
            w = screenW - 0.010f,
            h = screenH - 0.010f,
        )
        parts +=
            screenVerts.toPart(
                color = argbToFloat4(0xFF101520.toInt()),
                useTexture = true,
                shininess = 88f,
                specStrength = 0.42f,
                emissive = 0.20f,
            )

        // 9. Overhanging glowing top marquee light box (protruding 0.024f ahead of marquee front z = 0.42)
        val marqueeVerts = MeshBuilder()
        marqueeVerts.addBox(cx = 0f, cy = 0.96f, cz = 0.432f, w = 0.96f, h = 0.17f, d = 0.024f)
        parts +=
            marqueeVerts.toPart(
                color = argbToFloat4(0xFF1CC6FF.toInt()),
                shininess = 64f,
                specStrength = 0.50f,
                emissive = 0.62f,
            )

        return parts
    }

    // -------------------------------------------------------------------------
    // Geometry & Shader Helpers
    // -------------------------------------------------------------------------

    private data class MeshPart(
        val vertexBuffer: FloatBuffer,
        val vertexCount: Int,
        val color: FloatArray,
        val useTexture: Boolean = false,
        val shininess: Float = 32f,
        val specStrength: Float = 0.35f,
        val emissive: Float = 0f,
    )

    private class MeshBuilder {
        private val data = ArrayList<Float>(256)

        fun addBox(
            cx: Float,
            cy: Float,
            cz: Float,
            w: Float,
            h: Float,
            d: Float,
        ) {
            val x0 = cx - w * 0.5f
            val x1 = cx + w * 0.5f
            val y0 = cy - h * 0.5f
            val y1 = cy + h * 0.5f
            val z0 = cz - d * 0.5f
            val z1 = cz + d * 0.5f

            // Front (+Z)
            addQuad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0f, 0f, 1f)
            // Back (-Z)
            addQuad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0f, 0f, -1f)
            // Right (+X)
            addQuad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1f, 0f, 0f)
            // Left (-X)
            addQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1f, 0f, 0f)
            // Top (+Y)
            addQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f)
            // Bottom (-Y)
            addQuad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f)
        }

        fun addFrontQuad(
            cx: Float,
            cy: Float,
            z: Float,
            w: Float,
            h: Float,
        ) {
            val x0 = cx - w * 0.5f
            val x1 = cx + w * 0.5f
            val y0 = cy - h * 0.5f
            val y1 = cy + h * 0.5f
            addQuad(x0, y1, z, x0, y0, z, x1, y0, z, x1, y1, z, 0f, 0f, 1f)
        }

        fun addCylinderY(
            cx: Float,
            cy: Float,
            cz: Float,
            radius: Float,
            height: Float,
            segments: Int,
        ) {
            val y0 = cy - height * 0.5f
            val y1 = cy + height * 0.5f
            val step = (2.0 * PI / segments).toFloat()

            for (i in 0 until segments) {
                val a0 = i * step
                val a1 = (i + 1) * step
                val c0 = cos(a0)
                val s0 = sin(a0)
                val c1 = cos(a1)
                val s1 = sin(a1)

                val x0 = cx + radius * c0
                val z0 = cz + radius * s0
                val x1 = cx + radius * c1
                val z1 = cz + radius * s1

                // Side quad (CCW viewed from outside: a0 -> a1 wraps around +Y, so outward CCW is (x1,y1),(x1,y0),(x0,y0),(x0,y1))
                val nx = (c0 + c1) * 0.5f
                val nz = (s0 + s1) * 0.5f
                val nLen = sqrt(nx * nx + nz * nz).coerceAtLeast(1e-5f)
                addQuad(
                    x1, y1, z1,
                    x1, y0, z1,
                    x0, y0, z0,
                    x0, y1, z0,
                    nx / nLen, 0f, nz / nLen,
                )

                // Top cap (+Y)
                addVertex(cx, y1, cz, 0f, 1f, 0f, 0.5f, 0.5f)
                addVertex(x1, y1, z1, 0f, 1f, 0f, 0.5f, 0.5f)
                addVertex(x0, y1, z0, 0f, 1f, 0f, 0.5f, 0.5f)

                // Bottom cap (-Y)
                addVertex(cx, y0, cz, 0f, -1f, 0f, 0.5f, 0.5f)
                addVertex(x0, y0, z0, 0f, -1f, 0f, 0.5f, 0.5f)
                addVertex(x1, y0, z1, 0f, -1f, 0f, 0.5f, 0.5f)
            }
        }

        fun addCylinderZ(
            cx: Float,
            cy: Float,
            cz: Float,
            radius: Float,
            depth: Float,
            segments: Int,
        ) {
            val z0 = cz - depth * 0.5f
            val z1 = cz + depth * 0.5f
            val step = (2.0 * PI / segments).toFloat()

            for (i in 0 until segments) {
                val a0 = i * step
                val a1 = (i + 1) * step
                val c0 = cos(a0)
                val s0 = sin(a0)
                val c1 = cos(a1)
                val s1 = sin(a1)

                val x0 = cx + radius * c0
                val y0 = cy + radius * s0
                val x1 = cx + radius * c1
                val y1 = cy + radius * s1

                // Back cap (-Z)
                addVertex(cx, cy, z0, 0f, 0f, -1f, 0.5f, 0.5f)
                addVertex(x1, y1, z0, 0f, 0f, -1f, 0.5f, 0.5f)
                addVertex(x0, y0, z0, 0f, 0f, -1f, 0.5f, 0.5f)

                // Front cap (+Z)
                addVertex(cx, cy, z1, 0f, 0f, 1f, 0.5f, 0.5f)
                addVertex(x0, y0, z1, 0f, 0f, 1f, 0.5f, 0.5f)
                addVertex(x1, y1, z1, 0f, 0f, 1f, 0.5f, 0.5f)
            }
        }

        fun addSphere(
            cx: Float,
            cy: Float,
            cz: Float,
            radius: Float,
            stacks: Int,
            slices: Int,
        ) {
            for (i in 0 until stacks) {
                val phi0 = (PI * i / stacks).toFloat()
                val phi1 = (PI * (i + 1) / stacks).toFloat()
                val y0 = cos(phi0)
                val r0 = sin(phi0)
                val y1 = cos(phi1)
                val r1 = sin(phi1)

                for (j in 0 until slices) {
                    val theta0 = (2.0 * PI * j / slices).toFloat()
                    val theta1 = (2.0 * PI * (j + 1) / slices).toFloat()

                    val nx00 = r0 * cos(theta0)
                    val nz00 = r0 * sin(theta0)
                    val nx01 = r0 * cos(theta1)
                    val nz01 = r0 * sin(theta1)
                    val nx10 = r1 * cos(theta0)
                    val nz10 = r1 * sin(theta0)
                    val nx11 = r1 * cos(theta1)
                    val nz11 = r1 * sin(theta1)

                    // Tri 1
                    addVertex(cx + radius * nx01, cy + radius * y0, cz + radius * nz01, nx01, y0, nz01, 0f, 0f)
                    addVertex(cx + radius * nx11, cy + radius * y1, cz + radius * nz11, nx11, y1, nz11, 0f, 0f)
                    addVertex(cx + radius * nx10, cy + radius * y1, cz + radius * nz10, nx10, y1, nz10, 0f, 0f)

                    // Tri 2
                    addVertex(cx + radius * nx01, cy + radius * y0, cz + radius * nz01, nx01, y0, nz01, 0f, 0f)
                    addVertex(cx + radius * nx10, cy + radius * y1, cz + radius * nz10, nx10, y1, nz10, 0f, 0f)
                    addVertex(cx + radius * nx00, cy + radius * y0, cz + radius * nz00, nx00, y0, nz00, 0f, 0f)
                }
            }
        }

        private fun addQuad(
            x0: Float,
            y0: Float,
            z0: Float,
            x1: Float,
            y1: Float,
            z1: Float,
            x2: Float,
            y2: Float,
            z2: Float,
            x3: Float,
            y3: Float,
            z3: Float,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            // Tri 1: TL (0,0), BL (0,1), BR (1,1)
            addVertex(x0, y0, z0, nx, ny, nz, 0f, 0f)
            addVertex(x1, y1, z1, nx, ny, nz, 0f, 1f)
            addVertex(x2, y2, z2, nx, ny, nz, 1f, 1f)

            // Tri 2: TL (0,0), BR (1,1), TR (1,0)
            addVertex(x0, y0, z0, nx, ny, nz, 0f, 0f)
            addVertex(x2, y2, z2, nx, ny, nz, 1f, 1f)
            addVertex(x3, y3, z3, nx, ny, nz, 1f, 0f)
        }

        private fun addVertex(
            x: Float,
            y: Float,
            z: Float,
            nx: Float,
            ny: Float,
            nz: Float,
            u: Float,
            v: Float,
        ) {
            data.add(x)
            data.add(y)
            data.add(z)
            data.add(nx)
            data.add(ny)
            data.add(nz)
            data.add(u)
            data.add(v)
        }

        fun toPart(
            color: FloatArray,
            useTexture: Boolean = false,
            shininess: Float = 32f,
            specStrength: Float = 0.35f,
            emissive: Float = 0f,
        ): MeshPart {
            val arr = FloatArray(data.size) { data[it] }
            val buf =
                ByteBuffer.allocateDirect(arr.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(arr)
                        position(0)
                    }
            return MeshPart(
                vertexBuffer = buf,
                vertexCount = arr.size / FLOATS_PER_VERTEX,
                color = color,
                useTexture = useTexture,
                shininess = shininess,
                specStrength = specStrength,
                emissive = emissive,
            )
        }
    }

    private fun normalizeRgba(color: FloatArray): FloatArray {
        return floatArrayOf(
            color.getOrElse(0) { 0f }.coerceIn(0f, 1f),
            color.getOrElse(1) { 0f }.coerceIn(0f, 1f),
            color.getOrElse(2) { 0f }.coerceIn(0f, 1f),
            color.getOrElse(3) { 1f }.coerceIn(0f, 1f),
        )
    }

    private fun argbToFloat4(argb: Int): FloatArray {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }

    private fun scaleRgb(
        rgba: FloatArray,
        factor: Float,
    ): FloatArray {
        return floatArrayOf(
            (rgba[0] * factor).coerceIn(0f, 1f),
            (rgba[1] * factor).coerceIn(0f, 1f),
            (rgba[2] * factor).coerceIn(0f, 1f),
            rgba[3],
        )
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    companion object {
        private const val TEX_SIZE = 512
        private const val FLOATS_PER_VERTEX = 8
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;

            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vTexCoord;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                vTexCoord = aTexCoord;
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec4 uColor;
            uniform float uUseTexture;
            uniform float uShininess;
            uniform float uSpecStrength;
            uniform float uEmissive;
            uniform sampler2D uTexture;
            uniform vec3 uCameraPos;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vTexCoord;

            void main() {
                vec3 N = normalize(vNormal);
                vec3 V = normalize(uCameraPos - vWorldPos);

                vec4 baseColor = uColor;
                if (uUseTexture > 0.5) {
                    vec4 texColor = texture2D(uTexture, vTexCoord);
                    baseColor = vec4(texColor.rgb, 1.0);
                }

                // Directional key light (studio softbox top-right-front)
                vec3 keyDir = normalize(vec3(0.55, 0.75, 0.85));
                float keyDiff = max(dot(N, keyDir), 0.0);
                vec3 keyHalf = normalize(keyDir + V);
                float keySpec = pow(max(dot(N, keyHalf), 0.0), uShininess) * uSpecStrength;

                // Soft cool fill light
                vec3 fillDir = normalize(vec3(-0.75, -0.25, 0.55));
                float fillDiff = max(dot(N, fillDir), 0.0) * 0.28;

                // Rim backlight + Fresnel edge highlight
                vec3 rimDir = normalize(vec3(-0.45, 0.65, -0.85));
                float rimDiff = max(dot(N, rimDir), 0.0) * 0.36;
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.28;

                vec3 ambient = baseColor.rgb * 0.28;
                vec3 diffuse = baseColor.rgb * (keyDiff * 0.78 + fillDiff)
                             + baseColor.rgb * vec3(0.75, 0.88, 1.0) * rimDiff;
                vec3 specular = vec3(1.0, 0.99, 0.97) * keySpec
                              + vec3(0.65, 0.85, 1.0) * fresnel * uSpecStrength;
                vec3 emissive = baseColor.rgb * uEmissive;

                vec3 finalRgb = ambient + diffuse + specular + emissive;
                gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), baseColor.a);
            }
        """
    }
}
