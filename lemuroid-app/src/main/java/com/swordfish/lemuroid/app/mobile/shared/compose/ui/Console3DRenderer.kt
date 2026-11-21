package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reusable Compose 3D console turntable showcase powered by a single [GLSurfaceView]
 * and [Console3DRenderer]. Switching [systemKey] updates the 3D console mesh in-place
 * on the GL thread with zero EGL context recreation.
 */
@Composable
fun Console3DShowcaseView(
    systemKey: String,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val profile = rememberFrontend3DProfile()
    val renderer = remember { Console3DRenderer(initialSystemKey = systemKey, appContext = appContext) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(systemKey) {
        renderer.updateConsole(systemKey)
    }
    LaunchedEffect(profile) { renderer.setSpinning(profile != PerformanceProfile.PERFORMANCE) }
    Bind3DFrameClock(glSurfaceViewRef, listOf(systemKey, profile))

    Bind3DSurfaceLifecycle(glSurfaceViewRef)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glSurfaceViewRef = this
            }
        },
        update = { view ->
            if (glSurfaceViewRef !== view) {
                glSurfaceViewRef = view
            }
        },
        onRelease = { view ->
            view.onPause()
            if (glSurfaceViewRef === view) {
                glSurfaceViewRef = null
            }
        },
    )
}

/**
 * Full-screen Tomb Raider-style 3D circular console ring rendered inside ONE single
 * [GLSurfaceView]. All consoles in [consoleKeys] are rendered as real 3D models orbiting
 * smoothly around a glowing golden Locadora pedestal ring; only the selected front-center
 * console spins continuously while all other consoles stay at a fixed hero angle.
 */
@Composable
fun LocadoraRing3DView(
    consoleKeys: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val profile = rememberFrontend3DProfile()
    val renderer = remember { LocadoraRing3DRenderer(appContext, consoleKeys, selectedIndex) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(consoleKeys, selectedIndex) {
        renderer.updateState(consoleKeys, selectedIndex)
    }
    LaunchedEffect(profile) { renderer.setSpinning(profile != PerformanceProfile.PERFORMANCE) }
    Bind3DFrameClock(glSurfaceViewRef, listOf(consoleKeys, selectedIndex, profile))

    Bind3DSurfaceLifecycle(glSurfaceViewRef)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glSurfaceViewRef = this
            }
        },
        update = { view ->
            if (glSurfaceViewRef !== view) {
                glSurfaceViewRef = view
            }
        },
        onRelease = { view ->
            view.onPause()
            if (glSurfaceViewRef === view) {
                glSurfaceViewRef = null
            }
        },
    )
}

/**
 * Full-screen 3D 80s/90s Retro Gamer Bedroom rendered inside ONE single [GLSurfaceView].
 * All consoles in [consoleKeys] sit side-by-side along a warm wooden entertainment shelf
 * backed by a retro CRT TV, framed posters, and glowing neon trim. Changing [selectedIndex]
 * smoothly glides the 3D camera along the shelf to focus up close on the selected console.
 */
@Composable
fun ConsoleRoom3DView(
    consoleKeys: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val profile = rememberFrontend3DProfile()
    val renderer = remember { ConsoleRoom3DRenderer(appContext, consoleKeys, selectedIndex) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(consoleKeys, selectedIndex) {
        renderer.updateState(consoleKeys, selectedIndex)
    }
    LaunchedEffect(profile) { renderer.setSpinning(profile != PerformanceProfile.PERFORMANCE) }
    Bind3DFrameClock(glSurfaceViewRef, listOf(consoleKeys, selectedIndex, profile))

    Bind3DSurfaceLifecycle(glSurfaceViewRef)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glSurfaceViewRef = this
            }
        },
        update = { view ->
            if (glSurfaceViewRef !== view) {
                glSurfaceViewRef = view
            }
        },
        onRelease = { view ->
            view.onPause()
            if (glSurfaceViewRef === view) {
                glSurfaceViewRef = null
            }
        },
    )
}

/**
 * Real OpenGL ES 2.0 multi-part 3D retro console turntable renderer for Lemuroid's
 * Locadora 3D Console Ring.
 *
 * Renders recognizable procedural 3D hardware models for SNES, PlayStation, Nintendo 64,
 * Mega Drive / Genesis, NES, GBA, GB/GBC, Nintendo DS, PSP, Master System, Game Gear,
 * Arcade Cabinet, and a sleek retro console fallback.
 *
 * Uses `zNear = 1.0f, zFar = 20.0f` and strictly non-overlapping geometry with
 * `>= 0.018f` surface separation to prevent 16-bit depth-buffer Z-fighting on Android TV.
 */
class Console3DRenderer(
    initialSystemKey: String = "snes",
    private val appContext: Context? = null,
) : GLSurfaceView.Renderer {
    private val stateLock = Any()

    @Volatile
    private var pendingSystemKey: String = canonicalRoomKey(initialSystemKey)

    @Volatile private var spinning: Boolean = true
    fun setSpinning(value: Boolean) { spinning = value }

    private var activeSystemKey: String? = null
    private var meshDirty: Boolean = true

    private val assetProgram = GlProgramHelper(appContext)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewportAspect: Float = 1f
    private var angleY: Float = -24f
    private var lastFrameNanos: Long = 0L

    private var meshParts: List<MeshPart> = emptyList()
    private var assetCenterY = 0f
    private var activeCameraSpec: CameraSpec = CameraSpec(pitchX = 24f, cameraZ = 3.15f, cameraY = 0.08f)

    /**
     * Thread-safely requests a switch to another console mesh without recreating the EGL context.
     */
    fun updateConsole(systemKey: String) {
        val normalized = canonicalRoomKey(systemKey)
        synchronized(stateLock) {
            if (pendingSystemKey != normalized) {
                pendingSystemKey = normalized
                meshDirty = true
            }
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

        synchronized(stateLock) {
            meshDirty = true
        }
        assetProgram.init()
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

        val targetKey: String
        val rebuildMesh: Boolean
        synchronized(stateLock) {
            targetKey = pendingSystemKey
            rebuildMesh = meshDirty || activeSystemKey != targetKey
            meshDirty = false
        }

        if (rebuildMesh) {
            meshParts = loadConsoleMesh(appContext, targetKey)
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            meshParts.forEach { part ->
                val stride = if (part.hasUV) FLOATS_PER_VERTEX_UV else FLOATS_PER_VERTEX
                for (vertex in 0 until part.vertexCount) {
                    val y = part.vertexBuffer.get(vertex * stride + 1)
                    minY = kotlin.math.min(minY, y)
                    maxY = kotlin.math.max(maxY, y)
                }
            }
            assetCenterY = if (minY.isFinite() && maxY.isFinite()) (minY + maxY) * 0.5f else 0f
            activeCameraSpec = resolveCameraSpec(targetKey)
            activeSystemKey = targetKey
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
        }
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        if (spinning) angleY = (angleY + dt * 30f) % 360f

        val aspectComp = if (viewportAspect < 0.9f) 0.9f / viewportAspect else 1f
        val cameraZ = activeCameraSpec.cameraZ * aspectComp
        val cameraY = activeCameraSpec.cameraY

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
        Matrix.rotateM(modelMatrix, 0, activeCameraSpec.pitchX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, 0f, -assetCenterY, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        assetProgram.beginFrame(0f, cameraY, cameraZ)
        assetProgram.drawParts(meshParts, modelMatrix, mvpMatrix)
        assetProgram.endFrame()
    }

    // -------------------------------------------------------------------------
    // Console Dispatch & Camera Framing
    // -------------------------------------------------------------------------

    private data class CameraSpec(
        val pitchX: Float,
        val cameraZ: Float,
        val cameraY: Float = 0f,
    )

    private fun resolveCameraSpec(key: String): CameraSpec {
        return when (key) {
            "arcade", "fbneo", "mame2003plus" -> CameraSpec(pitchX = 12f, cameraZ = 3.95f, cameraY = 0.12f)
            "gb", "gbc" -> CameraSpec(pitchX = 14f, cameraZ = 3.30f, cameraY = 0.0f)
            "gba", "psp", "gg" -> CameraSpec(pitchX = 16f, cameraZ = 3.15f, cameraY = 0.0f)
            "nds" -> CameraSpec(pitchX = 18f, cameraZ = 3.45f, cameraY = 0.06f)
            else -> CameraSpec(pitchX = 24f, cameraZ = 3.25f, cameraY = 0.08f)
        }
    }
}

// =============================================================================
// Shared 3D Console Mesh Builders & OpenGL ES 2.0 Helpers
// =============================================================================

private fun loadConsoleMesh(
    context: Context?,
    key: String,
): List<MeshPart> {
    if (context != null) {
        val canonical = canonicalRoomKey(key)
        loadBlenderL3dFromAssets(context, canonical)?.let { return it }
    }
    return buildMeshForConsole(key)
}

internal fun loadBlenderL3dFromAssets(
    context: Context,
    canonicalKey: String,
): List<MeshPart>? =
    runCatching {
        val bytes = context.assets.open("models3d/$canonicalKey.l3d").use { it.readBytes() }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        val hasUV =
            when (magic) {
                0x3244334C -> true // L3D2 (px,py,pz,nx,ny,nz,u,v)
                0x3144334C -> false // L3D1 (px,py,pz,nx,ny,nz)
                else -> return null
            }
        val floatsPerVert = if (hasUV) FLOATS_PER_VERTEX_UV else FLOATS_PER_VERTEX
        val partCount = bb.int
        if (partCount <= 0 || partCount > 64) return null
        val parts = ArrayList<MeshPart>(partCount)
        for (p in 0 until partCount) {
            val r = bb.float
            val g = bb.float
            val b = bb.float
            val a = bb.float
            val shininess = bb.float
            val specStrength = bb.float
            val emissive = bb.float
            val vertexCount = bb.int
            if (vertexCount <= 0 || vertexCount > 36_000 || vertexCount % 3 != 0) return null
            val floatCount = vertexCount * floatsPerVert
            if (bb.remaining() < floatCount * 4) return null
            val direct =
                ByteBuffer.allocateDirect(floatCount * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            val tmp = FloatArray(floatCount)
            for (i in 0 until floatCount) {
                tmp[i] = bb.float
                if (!tmp[i].isFinite()) return null
            }
            direct.put(tmp)
            direct.position(0)
            parts.add(
                MeshPart(
                    vertexBuffer = direct,
                    vertexCount = vertexCount,
                    color = floatArrayOf(r, g, b, a),
                    shininess = shininess,
                    specStrength = specStrength,
                    emissive = emissive,
                    hasUV = hasUV,
                    hardwareAtlas = hasUV,
                ),
            )
        }
        parts.takeIf { it.isNotEmpty() && !bb.hasRemaining() }
    }.getOrNull()

private fun buildMeshForConsole(key: String): List<MeshPart> {
        return when (key) {
            "snes" -> buildSnesMesh()
            "psx" -> buildPsxMesh()
            "n64" -> buildN64Mesh()
            "md", "genesis" -> buildMegaDriveMesh()
            "segacd" -> buildSegaCdMesh()
            "nes" -> buildNesMesh()
            "gba" -> buildGbaMesh()
            "gb" -> buildGameBoyMesh(isColor = false)
            "gbc" -> buildGameBoyMesh(isColor = true)
            "nds" -> buildNdsMesh()
            "3ds" -> build3dsMesh()
            "psp" -> buildPspMesh()
            "sms" -> buildMasterSystemMesh()
            "gg" -> buildGameGearMesh()
            "atari2600" -> buildAtari2600Mesh()
            "atari7800" -> buildAtari7800Mesh()
            "pce" -> buildPcEngineMesh()
            "lynx" -> buildLynxMesh()
            "ngp", "ngc" -> buildNeoGeoPocketMesh(isColor = key == "ngc")
            "ws", "wsc" -> buildWonderSwanMesh(isColor = key == "wsc")
            "dos" -> buildDosPcMesh()
            "arcade", "fbneo", "mame2003plus" -> buildArcadeCabinetMesh()
            else -> buildRetroFallbackMesh()
        }
    }

    private fun buildAtari2600Mesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val black = MeshBuilder()
        black.addBox(0f, 0f, 0f, 1.55f, 0.24f, 0.92f)
        parts += black.toPart(argbToFloat4(0xFF1A1B1F.toInt()), shininess = 28f, specStrength = 0.25f)
        val wood = MeshBuilder()
        wood.addBox(0f, 0.01f, 0.44f, 1.50f, 0.19f, 0.08f)
        parts += wood.toPart(argbToFloat4(0xFF8B4F24.toInt()), shininess = 34f, specStrength = 0.28f)
        val chrome = MeshBuilder()
        for (i in -2..2) {
            chrome.addBox(i * 0.22f, 0.14f, 0.22f, 0.04f, 0.08f, 0.06f)
        }
        parts += chrome.toPart(argbToFloat4(0xFFDCE0E6.toInt()), shininess = 90f, specStrength = 0.80f)
        return parts
    }

    private fun buildAtari7800Mesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val black = MeshBuilder()
        black.addBox(0f, 0f, 0f, 1.48f, 0.22f, 0.90f)
        parts += black.toPart(argbToFloat4(0xFF18191D.toInt()), shininess = 32f, specStrength = 0.28f)
        val silver = MeshBuilder()
        silver.addBox(0f, 0.118f, 0.20f, 1.46f, 0.025f, 0.22f)
        parts += silver.toPart(argbToFloat4(0xFFC8CDD4.toInt()), shininess = 92f, specStrength = 0.78f)
        val rainbow = MeshBuilder()
        rainbow.addBox(0f, 0.132f, 0.16f, 1.38f, 0.012f, 0.04f)
        parts += rainbow.toPart(argbToFloat4(0xFFE53935.toInt()), shininess = 60f, specStrength = 0.45f, emissive = 0.25f)
        return parts
    }

    private fun buildSegaCdMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val body = MeshBuilder()
        body.addBox(-0.22f, 0f, 0f, 1.15f, 0.22f, 0.94f)
        body.addBox(0.54f, -0.02f, 0.02f, 0.56f, 0.18f, 0.90f)
        parts += body.toPart(argbToFloat4(0xFF15171C.toInt()), shininess = 36f, specStrength = 0.34f)
        val ring = MeshBuilder()
        ring.addCylinderY(-0.24f, 0.13f, -0.04f, 0.36f, 0.04f, 24)
        parts += ring.toPart(argbToFloat4(0xFF232730.toInt()), shininess = 50f, specStrength = 0.45f)
        val led = MeshBuilder()
        led.addBox(0.56f, 0.04f, 0.47f, 0.12f, 0.03f, 0.02f)
        parts += led.toPart(argbToFloat4(0xFF00E676.toInt()), shininess = 80f, specStrength = 0.60f, emissive = 0.80f)
        return parts
    }

    private fun buildPcEngineMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val shell = MeshBuilder()
        shell.addBox(0f, 0f, 0f, 0.92f, 0.20f, 0.92f)
        parts += shell.toPart(argbToFloat4(0xFFE6E4DC.toInt()), shininess = 42f, specStrength = 0.35f)
        val slot = MeshBuilder()
        slot.addBox(0f, 0.02f, 0.46f, 0.42f, 0.05f, 0.03f)
        parts += slot.toPart(argbToFloat4(0xFF2C2F36.toInt()), shininess = 28f, specStrength = 0.25f)
        val accent = MeshBuilder()
        accent.addBox(0.26f, 0.11f, -0.24f, 0.28f, 0.02f, 0.12f)
        parts += accent.toPart(argbToFloat4(0xFFD84315.toInt()), shininess = 48f, specStrength = 0.38f)
        return parts
    }

    private fun buildLynxMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val body = MeshBuilder()
        body.addBox(0f, 0f, 0f, 1.44f, 0.62f, 0.18f)
        parts += body.toPart(argbToFloat4(0xFF282B32.toInt()), shininess = 32f, specStrength = 0.28f)
        val screen = MeshBuilder()
        screen.addBox(0f, 0.02f, 0.10f, 0.60f, 0.42f, 0.02f)
        parts += screen.toPart(argbToFloat4(0xFF26A69A.toInt()), shininess = 84f, specStrength = 0.70f, emissive = 0.38f)
        val btns = MeshBuilder()
        btns.addBox(-0.52f, -0.04f, 0.10f, 0.18f, 0.18f, 0.03f)
        btns.addBox(0.52f, 0.04f, 0.10f, 0.14f, 0.14f, 0.03f)
        parts += btns.toPart(argbToFloat4(0xFF14161A.toInt()), shininess = 40f, specStrength = 0.35f)
        return parts
    }

    private fun buildNeoGeoPocketMesh(isColor: Boolean): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val body = MeshBuilder()
        body.addBox(0f, 0f, 0f, 1.12f, 0.64f, 0.16f)
        val shellColor = if (isColor) 0xFF1E4C8A.toInt() else 0xFF8E95A0.toInt()
        parts += body.toPart(argbToFloat4(shellColor), shininess = 42f, specStrength = 0.36f)
        val screen = MeshBuilder()
        screen.addBox(0f, 0.03f, 0.09f, 0.50f, 0.44f, 0.02f)
        parts += screen.toPart(argbToFloat4(0xFF3EC6E0.toInt()), shininess = 84f, specStrength = 0.70f, emissive = 0.35f)
        return parts
    }

    private fun buildWonderSwanMesh(isColor: Boolean): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val body = MeshBuilder()
        body.addBox(0f, 0f, 0f, 1.18f, 0.60f, 0.14f)
        val shellColor = if (isColor) 0xFF263859.toInt() else 0xFFD9DCE2.toInt()
        parts += body.toPart(argbToFloat4(shellColor), shininess = 44f, specStrength = 0.38f)
        val screen = MeshBuilder()
        screen.addBox(0.06f, 0.04f, 0.08f, 0.54f, 0.38f, 0.02f)
        parts += screen.toPart(argbToFloat4(0xFF4DD0E1.toInt()), shininess = 84f, specStrength = 0.70f, emissive = 0.32f)
        return parts
    }

    private fun build3dsMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val base = MeshBuilder()
        base.addBox(0f, -0.10f, 0.14f, 1.18f, 0.10f, 0.64f)
        base.addBox(0f, 0.28f, -0.18f, 1.18f, 0.66f, 0.08f)
        parts += base.toPart(argbToFloat4(0xFF9B1B30.toInt()), shininess = 68f, specStrength = 0.58f)
        val screens = MeshBuilder()
        screens.addBox(0f, 0.28f, -0.13f, 0.86f, 0.48f, 0.02f)
        screens.addBox(0f, -0.04f, 0.14f, 0.66f, 0.02f, 0.48f)
        parts += screens.toPart(argbToFloat4(0xFF29B6F6.toInt()), shininess = 90f, specStrength = 0.72f, emissive = 0.42f)
        return parts
    }

    private fun buildDosPcMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val beige = MeshBuilder()
        // Desktop horizontal PC chassis + chunky beige CRT monitor + mechanical keyboard
        beige.addBox(0f, -0.22f, -0.06f, 1.38f, 0.30f, 0.92f)
        beige.addBox(0f, 0.34f, -0.10f, 0.96f, 0.78f, 0.76f)
        beige.addBox(0f, -0.32f, 0.66f, 1.26f, 0.09f, 0.38f)
        parts += beige.toPart(argbToFloat4(0xFFD8D0BE.toInt()), shininess = 32f, specStrength = 0.25f)
        val screen = MeshBuilder()
        screen.addBox(0f, 0.36f, 0.29f, 0.74f, 0.56f, 0.03f)
        parts += screen.toPart(argbToFloat4(0xFF1B5E20.toInt()), shininess = 88f, specStrength = 0.70f, emissive = 0.52f)
        val keys = MeshBuilder()
        keys.addBox(0f, -0.265f, 0.66f, 1.12f, 0.03f, 0.26f)
        keys.addBox(0.36f, -0.20f, 0.41f, 0.34f, 0.05f, 0.03f) // 3.5" floppy drive slot
        parts += keys.toPart(argbToFloat4(0xFF45423B.toInt()), shininess = 24f, specStrength = 0.20f)
        return parts
    }

    // -------------------------------------------------------------------------
    // 1. Super Nintendo (SNES)
    // -------------------------------------------------------------------------

    private fun buildSnesMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val lightGrey = argbToFloat4(0xFFC8C8CE.toInt())
        val midGrey = argbToFloat4(0xFF9C9DA6.toInt())
        val darkGrey = argbToFloat4(0xFF3A3D47.toInt())
        val purple = argbToFloat4(0xFF6E49B8.toInt())
        val lavender = argbToFloat4(0xFF9575CD.toInt())

        // Lower chassis plinth: y in [-0.24, -0.08]
        val lowerVerts = MeshBuilder()
        lowerVerts.addBox(cx = 0f, cy = -0.16f, cz = 0f, w = 1.44f, h = 0.16f, d = 1.12f)
        parts += lowerVerts.toPart(color = midGrey, shininess = 28f, specStrength = 0.28f)

        // Upper boxy shell: y in [-0.08, +0.18]
        val upperVerts = MeshBuilder()
        upperVerts.addBox(cx = 0f, cy = 0.05f, cz = 0f, w = 1.50f, h = 0.26f, d = 1.18f)
        // Left & right raised side shoulders flanking the center bay
        upperVerts.addBox(cx = -0.56f, cy = 0.21f, cz = 0f, w = 0.34f, h = 0.06f, d = 1.06f)
        upperVerts.addBox(cx = 0.56f, cy = 0.21f, cz = 0f, w = 0.34f, h = 0.06f, d = 1.06f)
        parts += upperVerts.toPart(color = lightGrey, shininess = 34f, specStrength = 0.34f)

        // Purple center top accent strip & eject bar (sits cleanly on y = 0.18 -> top at 0.216)
        val purpleVerts = MeshBuilder()
        purpleVerts.addBox(cx = 0f, cy = 0.198f, cz = -0.12f, w = 0.72f, h = 0.036f, d = 0.72f)
        // Power & Reset sliders on front-left & front-right of top deck (clear of side shoulders)
        purpleVerts.addBox(cx = -0.28f, cy = 0.210f, cz = 0.38f, w = 0.12f, h = 0.060f, d = 0.16f)
        purpleVerts.addBox(cx = 0.28f, cy = 0.210f, cz = 0.38f, w = 0.12f, h = 0.060f, d = 0.16f)
        parts += purpleVerts.toPart(color = purple, shininess = 46f, specStrength = 0.42f)

        // Lavender center eject button (protruding 0.024f on top of purple strip y = 0.216)
        val ejectVerts = MeshBuilder()
        ejectVerts.addBox(cx = 0f, cy = 0.228f, cz = 0.14f, w = 0.28f, h = 0.024f, d = 0.10f)
        parts += ejectVerts.toPart(color = lavender, shininess = 40f, specStrength = 0.38f)

        // Dark recessed cartridge slot on purple bay (y = 0.216 -> top at 0.240) + front controller port panel
        val darkVerts = MeshBuilder()
        darkVerts.addBox(cx = 0f, cy = 0.228f, cz = -0.14f, w = 0.60f, h = 0.024f, d = 0.14f)
        // Front controller port faceplate (protruding 0.024f ahead of front z = 0.59)
        darkVerts.addBox(cx = 0f, cy = -0.14f, cz = 0.576f, w = 1.06f, h = 0.12f, d = 0.032f)
        // Port 1 & Port 2 sockets (protruding 0.020f ahead of faceplate)
        darkVerts.addBox(cx = -0.28f, cy = -0.14f, cz = 0.602f, w = 0.24f, h = 0.07f, d = 0.020f)
        darkVerts.addBox(cx = 0.28f, cy = -0.14f, cz = 0.602f, w = 0.24f, h = 0.07f, d = 0.020f)
        parts += darkVerts.toPart(color = darkGrey, shininess = 22f, specStrength = 0.20f)

        // Red power LED on front left
        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = -0.56f, cy = 0.06f, cz = 0.602f, w = 0.05f, h = 0.026f, d = 0.024f)
        parts += ledVerts.toPart(color = argbToFloat4(0xFFFF3B30.toInt()), shininess = 64f, specStrength = 0.60f, emissive = 0.70f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 2. PlayStation (PSX)
    // -------------------------------------------------------------------------

    private fun buildPsxMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val psxGrey = argbToFloat4(0xFFB8BBC2.toInt())
        val lidGrey = argbToFloat4(0xFFA6AAB3.toInt())
        val darkSlot = argbToFloat4(0xFF343842.toInt())
        val btnGrey = argbToFloat4(0xFF8C909A.toInt())

        // Main flat rectangular console chassis: y in [-0.18, +0.12]
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = -0.03f, cz = 0f, w = 1.56f, h = 0.30f, d = 1.14f)
        parts += bodyVerts.toPart(color = psxGrey, shininess = 36f, specStrength = 0.34f)

        // Raised circular CD disc lid on top (bottom at y = 0.12, top at y = 0.175)
        val lidVerts = MeshBuilder()
        lidVerts.addCylinderY(cx = 0f, cy = 0.148f, cz = -0.02f, radius = 0.46f, height = 0.056f, segments = 28)
        parts += lidVerts.toPart(color = lidGrey, shininess = 48f, specStrength = 0.44f)

        // Center PlayStation emblem disc on top of CD lid (bottom at 0.176, top at 0.198)
        val emblemVerts = MeshBuilder()
        emblemVerts.addCylinderY(cx = 0f, cy = 0.187f, cz = -0.02f, radius = 0.12f, height = 0.022f, segments = 20)
        parts += emblemVerts.toPart(color = darkSlot, shininess = 56f, specStrength = 0.50f)

        // Circular Power (left), Reset (small left-rear), and Open (right) buttons
        val btnVerts = MeshBuilder()
        btnVerts.addCylinderY(cx = -0.58f, cy = 0.140f, cz = 0.34f, radius = 0.085f, height = 0.040f, segments = 18)
        btnVerts.addCylinderY(cx = -0.58f, cy = 0.136f, cz = 0.12f, radius = 0.048f, height = 0.032f, segments = 16)
        btnVerts.addCylinderY(cx = 0.58f, cy = 0.140f, cz = 0.34f, radius = 0.085f, height = 0.040f, segments = 18)
        parts += btnVerts.toPart(color = btnGrey, shininess = 40f, specStrength = 0.38f)

        // Front two controller + memory card slots (protruding 0.024f ahead of front z = 0.57)
        val slotVerts = MeshBuilder()
        // Memory card slots (upper row)
        slotVerts.addBox(cx = -0.34f, cy = 0.03f, cz = 0.582f, w = 0.34f, h = 0.055f, d = 0.024f)
        slotVerts.addBox(cx = 0.34f, cy = 0.03f, cz = 0.582f, w = 0.34f, h = 0.055f, d = 0.024f)
        // Controller ports (lower row)
        slotVerts.addBox(cx = -0.34f, cy = -0.07f, cz = 0.582f, w = 0.36f, h = 0.075f, d = 0.024f)
        slotVerts.addBox(cx = 0.34f, cy = -0.07f, cz = 0.582f, w = 0.36f, h = 0.075f, d = 0.024f)
        parts += slotVerts.toPart(color = darkSlot, shininess = 24f, specStrength = 0.20f)

        // Green Power LED
        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = -0.58f, cy = 0.134f, cz = 0.48f, w = 0.048f, h = 0.028f, d = 0.036f)
        parts += ledVerts.toPart(color = argbToFloat4(0xFF2ECC71.toInt()), shininess = 72f, specStrength = 0.60f, emissive = 0.72f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 3. Nintendo 64 (N64)
    // -------------------------------------------------------------------------

    private fun buildN64Mesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val charcoal = argbToFloat4(0xFF343842.toInt())
        val darkCharcoal = argbToFloat4(0xFF20232B.toInt())
        val expRed = argbToFloat4(0xFFC62828.toInt())
        val portGrey = argbToFloat4(0xFF565C6B.toInt())

        // Main wide charcoal body: y in [-0.16, +0.10]
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = -0.03f, cz = -0.06f, w = 1.56f, h = 0.26f, d = 1.00f)
        // Stepped/curved iconic front left & right feet/legs
        bodyVerts.addBox(cx = -0.52f, cy = -0.05f, cz = 0.54f, w = 0.46f, h = 0.22f, d = 0.20f)
        bodyVerts.addBox(cx = 0.52f, cy = -0.05f, cz = 0.54f, w = 0.46f, h = 0.22f, d = 0.20f)
        // Central bridge between legs
        bodyVerts.addBox(cx = 0f, cy = -0.07f, cz = 0.50f, w = 0.58f, h = 0.18f, d = 0.12f)
        // Central arched cartridge slot hump on top (y in [0.10, 0.24])
        bodyVerts.addBox(cx = 0f, cy = 0.17f, cz = -0.10f, w = 0.86f, h = 0.14f, d = 0.56f)
        // Stepped upper crown of hump (y in [0.24, 0.30])
        bodyVerts.addBox(cx = 0f, cy = 0.27f, cz = -0.10f, w = 0.70f, h = 0.06f, d = 0.38f)
        parts += bodyVerts.toPart(color = charcoal, shininess = 38f, specStrength = 0.36f)

        // Dark cartridge slot opening on top of hump + Power/Reset switches
        val darkVerts = MeshBuilder()
        darkVerts.addBox(cx = 0f, cy = 0.312f, cz = -0.10f, w = 0.56f, h = 0.024f, d = 0.12f)
        // Power switch (left) & Reset button (right)
        darkVerts.addBox(cx = -0.58f, cy = 0.118f, cz = 0.24f, w = 0.14f, h = 0.036f, d = 0.16f)
        darkVerts.addBox(cx = 0.58f, cy = 0.118f, cz = 0.24f, w = 0.14f, h = 0.036f, d = 0.12f)
        parts += darkVerts.toPart(color = darkCharcoal, shininess = 28f, specStrength = 0.24f)

        // Red/black Expansion Pak cover in front of the cartridge hump (y = 0.10 -> 0.136)
        val expVerts = MeshBuilder()
        expVerts.addBox(cx = 0f, cy = 0.118f, cz = 0.30f, w = 0.32f, h = 0.036f, d = 0.20f)
        parts += expVerts.toPart(color = expRed, shininess = 52f, specStrength = 0.45f)

        // 4 iconic front controller ports (protruding ahead of legs/bridge)
        val portVerts = MeshBuilder()
        val portXs = floatArrayOf(-0.56f, -0.22f, 0.22f, 0.56f)
        val portZs = floatArrayOf(0.652f, 0.572f, 0.572f, 0.652f)
        for (i in portXs.indices) {
            portVerts.addBox(cx = portXs[i], cy = -0.08f, cz = portZs[i], w = 0.14f, h = 0.085f, d = 0.024f)
        }
        parts += portVerts.toPart(color = portGrey, shininess = 30f, specStrength = 0.28f)

        // Glowing red power jewel LED on front center
        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = 0f, cy = 0.01f, cz = 0.572f, w = 0.055f, h = 0.055f, d = 0.024f)
        parts += ledVerts.toPart(color = argbToFloat4(0xFFFF3D3D.toInt()), shininess = 72f, specStrength = 0.65f, emissive = 0.72f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 4. Mega Drive / Genesis (MD)
    // -------------------------------------------------------------------------

    private fun buildMegaDriveMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val matteBlack = argbToFloat4(0xFF1E2026.toInt())
        val glossTrim = argbToFloat4(0xFF2D313C.toInt())
        val goldAccent = argbToFloat4(0xFFD4AF37.toInt())
        val redAccent = argbToFloat4(0xFFE53935.toInt())

        // Sleek low-profile matte black body: y in [-0.16, +0.10]
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = -0.03f, cz = 0f, w = 1.56f, h = 0.26f, d = 1.12f)
        // Left cartridge bay rise (y in [0.10, 0.16])
        bodyVerts.addBox(cx = -0.24f, cy = 0.13f, cz = -0.06f, w = 0.74f, h = 0.06f, d = 0.54f)
        parts += bodyVerts.toPart(color = matteBlack, shininess = 32f, specStrength = 0.30f)

        // Raised circular 16-BIT ring pad on top-right (y in [0.10, 0.15])
        val ringVerts = MeshBuilder()
        ringVerts.addCylinderY(cx = 0.34f, cy = 0.125f, cz = 0.06f, radius = 0.36f, height = 0.050f, segments = 26)
        parts += ringVerts.toPart(color = glossTrim, shininess = 68f, specStrength = 0.58f)

        // Gold 16-BIT badge bar on top of circular pad (y in [0.15, 0.172])
        val badgeVerts = MeshBuilder()
        badgeVerts.addBox(cx = 0.34f, cy = 0.161f, cz = 0.06f, w = 0.30f, h = 0.022f, d = 0.08f)
        parts += badgeVerts.toPart(color = goldAccent, shininess = 84f, specStrength = 0.72f)

        // Cartridge slot on top-left bay (y in [0.16, 0.184]) + front controller ports + volume slider track
        val slotVerts = MeshBuilder()
        slotVerts.addBox(cx = -0.24f, cy = 0.172f, cz = -0.06f, w = 0.56f, h = 0.024f, d = 0.11f)
        // Volume slider rail on front-left
        slotVerts.addBox(cx = -0.58f, cy = 0.112f, cz = 0.36f, w = 0.24f, h = 0.024f, d = 0.07f)
        // Front two 9-pin controller ports (protruding ahead of z = 0.56)
        slotVerts.addBox(cx = -0.28f, cy = -0.05f, cz = 0.572f, w = 0.22f, h = 0.08f, d = 0.024f)
        slotVerts.addBox(cx = 0.12f, cy = -0.05f, cz = 0.572f, w = 0.22f, h = 0.08f, d = 0.024f)
        parts += slotVerts.toPart(color = argbToFloat4(0xFF0E1015.toInt()), shininess = 22f, specStrength = 0.18f)

        // Red Power LED + Red Reset button + Volume knob
        val redVerts = MeshBuilder()
        redVerts.addBox(cx = -0.16f, cy = 0.114f, cz = 0.42f, w = 0.07f, h = 0.028f, d = 0.045f)
        redVerts.addBox(cx = 0.02f, cy = 0.118f, cz = 0.40f, w = 0.10f, h = 0.036f, d = 0.08f)
        parts += redVerts.toPart(color = redAccent, shininess = 72f, specStrength = 0.62f, emissive = 0.48f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 5. Nintendo Entertainment System (NES)
    // -------------------------------------------------------------------------

    private fun buildNesMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val lightGrey = argbToFloat4(0xFFC6C8CC.toInt())
        val darkGrey = argbToFloat4(0xFF5C6068.toInt())
        val blackStripe = argbToFloat4(0xFF14161C.toInt())
        val redAccent = argbToFloat4(0xFFD32F2F.toInt())

        // Lower dark grey VCR-style base: y in [-0.24, -0.04]
        val baseVerts = MeshBuilder()
        baseVerts.addBox(cx = 0f, cy = -0.14f, cz = 0f, w = 1.46f, h = 0.20f, d = 1.06f)
        parts += baseVerts.toPart(color = darkGrey, shininess = 26f, specStrength = 0.24f)

        // Upper light grey boxy shell: y in [-0.04, +0.24]
        val topVerts = MeshBuilder()
        topVerts.addBox(cx = 0f, cy = 0.10f, cz = 0f, w = 1.54f, h = 0.28f, d = 1.12f)
        parts += topVerts.toPart(color = lightGrey, shininess = 32f, specStrength = 0.30f)

        // Iconic vertical black stripe on the right side (protruding 0.020f on top & front)
        val stripeVerts = MeshBuilder()
        // Top stripe segment
        stripeVerts.addBox(cx = 0.44f, cy = 0.250f, cz = 0f, w = 0.24f, h = 0.020f, d = 1.12f)
        // Front vertical stripe segment
        stripeVerts.addBox(cx = 0.44f, cy = 0.10f, cz = 0.570f, w = 0.24f, h = 0.28f, d = 0.020f)
        parts += stripeVerts.toPart(color = blackStripe, shininess = 40f, specStrength = 0.35f)

        // Dark grey front cartridge flap door (protruding 0.022f ahead of front z = 0.56)
        val doorVerts = MeshBuilder()
        doorVerts.addBox(cx = -0.18f, cy = 0.10f, cz = 0.571f, w = 0.92f, h = 0.20f, d = 0.022f)
        // Power & Reset buttons on lower-left front
        doorVerts.addBox(cx = -0.52f, cy = -0.14f, cz = 0.542f, w = 0.14f, h = 0.07f, d = 0.024f)
        doorVerts.addBox(cx = -0.34f, cy = -0.14f, cz = 0.542f, w = 0.14f, h = 0.07f, d = 0.024f)
        parts += doorVerts.toPart(color = darkGrey, shininess = 28f, specStrength = 0.26f)

        // Red accent line & power LED
        val redVerts = MeshBuilder()
        redVerts.addBox(cx = -0.18f, cy = 0.03f, cz = 0.592f, w = 0.62f, h = 0.022f, d = 0.020f)
        redVerts.addBox(cx = -0.64f, cy = -0.14f, cz = 0.542f, w = 0.035f, h = 0.035f, d = 0.024f)
        parts += redVerts.toPart(color = redAccent, shininess = 60f, specStrength = 0.50f, emissive = 0.40f)

        // Two controller ports on lower-right front
        val portVerts = MeshBuilder()
        portVerts.addBox(cx = 0.24f, cy = -0.14f, cz = 0.542f, w = 0.16f, h = 0.09f, d = 0.024f)
        portVerts.addBox(cx = 0.48f, cy = -0.14f, cz = 0.542f, w = 0.16f, h = 0.09f, d = 0.024f)
        parts += portVerts.toPart(color = blackStripe, shininess = 22f, specStrength = 0.20f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 6. Game Boy Advance (GBA)
    // -------------------------------------------------------------------------

    private fun buildGbaMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val indigo = argbToFloat4(0xFF4B3E8E.toInt())
        val lightGrey = argbToFloat4(0xFFB5B9C4.toInt())
        val darkBezel = argbToFloat4(0xFF1D202A.toInt())
        val screenGlow = argbToFloat4(0xFF3E7CB1.toInt())

        // Wide indigo handheld body: w = 1.56, h = 0.86, d = 0.24 (z in [-0.12, +0.12])
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = 0f, cz = 0f, w = 1.56f, h = 0.86f, d = 0.24f)
        parts += bodyVerts.toPart(color = indigo, shininess = 42f, specStrength = 0.38f)

        // L & R shoulder bumpers + side grip trim (light grey)
        val bumperVerts = MeshBuilder()
        bumperVerts.addBox(cx = -0.56f, cy = 0.455f, cz = 0f, w = 0.40f, h = 0.050f, d = 0.20f)
        bumperVerts.addBox(cx = 0.56f, cy = 0.455f, cz = 0f, w = 0.40f, h = 0.050f, d = 0.20f)
        // Left & right ergonomic side grip bumpers
        bumperVerts.addBox(cx = -0.805f, cy = 0f, cz = 0f, w = 0.050f, h = 0.70f, d = 0.20f)
        bumperVerts.addBox(cx = 0.805f, cy = 0f, cz = 0f, w = 0.050f, h = 0.70f, d = 0.20f)
        // Grey D-Pad on left (protruding 0.028f from front z = 0.12, 3-piece non-overlapping)
        bumperVerts.addDPadZ(cx = -0.56f, cy = 0.10f, cz = 0.134f, armSpan = 0.20f, armWidth = 0.066f, depth = 0.028f)
        // Grey A & B buttons on right
        bumperVerts.addCylinderZ(cx = 0.62f, cy = 0.14f, cz = 0.134f, radius = 0.058f, depth = 0.028f, segments = 16)
        bumperVerts.addCylinderZ(cx = 0.48f, cy = 0.04f, cz = 0.134f, radius = 0.058f, depth = 0.028f, segments = 16)
        // Start & Select buttons bottom-left
        bumperVerts.addBox(cx = -0.54f, cy = -0.18f, cz = 0.132f, w = 0.10f, h = 0.036f, d = 0.024f)
        bumperVerts.addBox(cx = -0.54f, cy = -0.26f, cz = 0.132f, w = 0.10f, h = 0.036f, d = 0.024f)
        parts += bumperVerts.toPart(color = lightGrey, shininess = 36f, specStrength = 0.34f)

        // Dark screen bezel (protruding 0.020f ahead of z = 0.12 -> front at 0.140)
        val bezelVerts = MeshBuilder()
        bezelVerts.addBox(cx = 0f, cy = 0.04f, cz = 0.130f, w = 0.78f, h = 0.62f, d = 0.020f)
        parts += bezelVerts.toPart(color = darkBezel, shininess = 50f, specStrength = 0.42f)

        // Lit LCD screen (protruding 0.020f ahead of bezel front 0.140 -> front at 0.160)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0f, cy = 0.06f, cz = 0.150f, w = 0.66f, h = 0.44f, d = 0.020f)
        parts += screenVerts.toPart(color = screenGlow, shininess = 88f, specStrength = 0.65f, emissive = 0.36f)

        // Green power LED
        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = 0.48f, cy = 0.30f, cz = 0.132f, w = 0.036f, h = 0.036f, d = 0.024f)
        parts += ledVerts.toPart(color = argbToFloat4(0xFF4CE896.toInt()), shininess = 72f, specStrength = 0.60f, emissive = 0.75f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 7. Game Boy / Game Boy Color (GB / GBC)
    // -------------------------------------------------------------------------

    private fun buildGameBoyMesh(isColor: Boolean): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val bodyColor =
            if (isColor) {
                argbToFloat4(0xFF6B3FA0.toInt()) // Atomic Purple
            } else {
                argbToFloat4(0xFFCECEC4.toInt()) // Classic DMG Grey
            }
        val bezelColor = argbToFloat4(0xFF454954.toInt())
        val screenColor =
            if (isColor) {
                argbToFloat4(0xFF3E8E7E.toInt())
            } else {
                argbToFloat4(0xFF6B7F4E.toInt()) // Classic olive LCD
            }
        val dpadBlack = argbToFloat4(0xFF181A20.toInt())
        val btnColor =
            if (isColor) {
                argbToFloat4(0xFF1D2028.toInt())
            } else {
                argbToFloat4(0xFFA02058.toInt()) // Iconic DMG magenta A/B
            }

        // Tall vertical handheld body: w = 0.92, h = 1.44, d = 0.26 (z in [-0.13, +0.13])
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = 0f, cz = 0f, w = 0.92f, h = 1.44f, d = 0.26f)
        parts += bodyVerts.toPart(color = bodyColor, shininess = if (isColor) 64f else 30f, specStrength = if (isColor) 0.52f else 0.28f)

        // Upper screen bezel (protruding 0.020f ahead of z = 0.13 -> front at 0.150)
        val bezelVerts = MeshBuilder()
        bezelVerts.addBox(cx = 0f, cy = 0.30f, cz = 0.140f, w = 0.76f, h = 0.62f, d = 0.020f)
        parts += bezelVerts.toPart(color = bezelColor, shininess = 44f, specStrength = 0.36f)

        // Recessed-look olive/color LCD screen (protruding 0.020f ahead of bezel -> front at 0.170)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0.02f, cy = 0.30f, cz = 0.160f, w = 0.54f, h = 0.46f, d = 0.020f)
        parts += screenVerts.toPart(color = screenColor, shininess = 72f, specStrength = 0.45f, emissive = 0.24f)

        // Red battery LED on left of screen bezel
        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = -0.31f, cy = 0.34f, cz = 0.160f, w = 0.036f, h = 0.036f, d = 0.020f)
        parts += ledVerts.toPart(color = argbToFloat4(0xFFFF3B30.toInt()), shininess = 72f, specStrength = 0.60f, emissive = 0.75f)

        // Black D-Pad on lower-left + Start/Select pills + rear cartridge slot
        val blackVerts = MeshBuilder()
        blackVerts.addDPadZ(cx = -0.24f, cy = -0.26f, cz = 0.144f, armSpan = 0.22f, armWidth = 0.072f, depth = 0.028f)
        // Start & Select
        blackVerts.addBox(cx = -0.08f, cy = -0.52f, cz = 0.142f, w = 0.11f, h = 0.036f, d = 0.024f)
        blackVerts.addBox(cx = 0.08f, cy = -0.52f, cz = 0.142f, w = 0.11f, h = 0.036f, d = 0.024f)
        // Rear cartridge bay
        blackVerts.addBox(cx = 0f, cy = 0.32f, cz = -0.142f, w = 0.62f, h = 0.56f, d = 0.024f)
        parts += blackVerts.toPart(color = dpadBlack, shininess = 32f, specStrength = 0.30f)

        // Magenta (GB) or dark (GBC) A & B buttons on lower-right
        val btnVerts = MeshBuilder()
        btnVerts.addCylinderZ(cx = 0.28f, cy = -0.20f, cz = 0.144f, radius = 0.068f, depth = 0.028f, segments = 16)
        btnVerts.addCylinderZ(cx = 0.13f, cy = -0.30f, cz = 0.144f, radius = 0.068f, depth = 0.028f, segments = 16)
        parts += btnVerts.toPart(color = btnColor, shininess = 58f, specStrength = 0.50f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 8. Nintendo DS (Open 3D Clamshell Dual-Screen)
    // -------------------------------------------------------------------------

    private fun buildNdsMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val silverShell = argbToFloat4(0xFF9EA4B0.toInt())
        val darkInner = argbToFloat4(0xFF1A1D24.toInt())
        val screenGlow = argbToFloat4(0xFF3A82C4.toInt())
        val btnLight = argbToFloat4(0xFFD0D5DF.toInt())

        // Lower clamshell base: flat horizontal deck y in [-0.36, -0.22], z in [-0.36, +0.44]
        val baseVerts = MeshBuilder()
        baseVerts.addBox(cx = 0f, cy = -0.29f, cz = 0.04f, w = 1.46f, h = 0.14f, d = 0.80f)
        // Hinge barrel along back-top edge of base (sits cleanly on y = -0.22 -> y in [-0.22, -0.12])
        baseVerts.addBox(cx = 0f, cy = -0.17f, cz = -0.34f, w = 1.38f, h = 0.10f, d = 0.12f)
        // Angled upper clamshell lid tilted back 22° from vertical, hinged at (0, -0.14, -0.34)
        val lidAngleDeg = -22f
        val pivotY = -0.14f
        val pivotZ = -0.34f
        baseVerts.addBoxRotatedX(
            cx = 0f,
            localCy = 0.42f,
            localCz = 0f,
            w = 1.42f,
            h = 0.78f,
            d = 0.10f,
            pivotY = pivotY,
            pivotZ = pivotZ,
            angleDeg = lidAngleDeg,
        )
        parts += baseVerts.toPart(color = silverShell, shininess = 56f, specStrength = 0.48f)

        // Lower touchscreen bezel (top of base is y = -0.22 -> bezel top at -0.20)
        // + Upper screen bezel (front of lid is localZ = +0.05 -> bezel front at +0.07)
        val bezelVerts = MeshBuilder()
        bezelVerts.addBox(cx = 0f, cy = -0.210f, cz = 0.06f, w = 0.72f, h = 0.020f, d = 0.56f)
        bezelVerts.addBoxRotatedX(
            cx = 0f,
            localCy = 0.42f,
            localCz = 0.060f,
            w = 0.80f,
            h = 0.60f,
            d = 0.020f,
            pivotY = pivotY,
            pivotZ = pivotZ,
            angleDeg = lidAngleDeg,
        )
        parts += bezelVerts.toPart(color = darkInner, shininess = 42f, specStrength = 0.36f)

        // Glowing dual screens (lower + upper, each protruding 0.020f from its bezel)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0f, cy = -0.190f, cz = 0.06f, w = 0.64f, h = 0.020f, d = 0.48f)
        screenVerts.addBoxRotatedX(
            cx = 0f,
            localCy = 0.42f,
            localCz = 0.080f,
            w = 0.66f,
            h = 0.48f,
            d = 0.020f,
            pivotY = pivotY,
            pivotZ = pivotZ,
            angleDeg = lidAngleDeg,
        )
        parts += screenVerts.toPart(color = screenGlow, shininess = 88f, specStrength = 0.65f, emissive = 0.38f)

        // Lower deck controls: D-Pad on left, 4 ABXY buttons on right (sitting on y = -0.22)
        val ctrlVerts = MeshBuilder()
        ctrlVerts.addDPadY(cx = -0.54f, cy = -0.206f, cz = 0.08f, armSpan = 0.18f, armWidth = 0.06f, height = 0.028f)
        // 4 face buttons (diamond layout)
        ctrlVerts.addCylinderY(cx = 0.54f, cy = -0.206f, cz = -0.02f, radius = 0.038f, height = 0.028f, segments = 12)
        ctrlVerts.addCylinderY(cx = 0.54f, cy = -0.206f, cz = 0.14f, radius = 0.038f, height = 0.028f, segments = 12)
        ctrlVerts.addCylinderY(cx = 0.46f, cy = -0.206f, cz = 0.06f, radius = 0.038f, height = 0.028f, segments = 12)
        ctrlVerts.addCylinderY(cx = 0.62f, cy = -0.206f, cz = 0.06f, radius = 0.038f, height = 0.028f, segments = 12)
        parts += ctrlVerts.toPart(color = btnLight, shininess = 44f, specStrength = 0.40f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 9. PlayStation Portable (PSP)
    // -------------------------------------------------------------------------

    private fun buildPspMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val glossyBlack = argbToFloat4(0xFF181A20.toInt())
        val silverTrim = argbToFloat4(0xFFC4CAD4.toInt())
        val screenGlow = argbToFloat4(0xFF2874C8.toInt())
        val buttonTrim = argbToFloat4(0xFF3E4454.toInt())

        // Sleek glossy black widescreen body: w = 1.64, h = 0.76, d = 0.20 (z in [-0.10, +0.10])
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = 0f, cz = 0f, w = 1.64f, h = 0.76f, d = 0.20f)
        parts += bodyVerts.toPart(color = glossyBlack, shininess = 96f, specStrength = 0.72f)

        // Silver outer trim ring (top, bottom, left, right rails cleanly outside body edges)
        val trimVerts = MeshBuilder()
        trimVerts.addBox(cx = 0f, cy = 0.395f, cz = 0f, w = 1.64f, h = 0.030f, d = 0.16f)
        trimVerts.addBox(cx = 0f, cy = -0.395f, cz = 0f, w = 1.64f, h = 0.030f, d = 0.16f)
        trimVerts.addBox(cx = -0.835f, cy = 0f, cz = 0f, w = 0.030f, h = 0.76f, d = 0.16f)
        trimVerts.addBox(cx = 0.835f, cy = 0f, cz = 0f, w = 0.030f, h = 0.76f, d = 0.16f)
        // Rear UMD silver ring
        trimVerts.addCylinderZ(cx = 0f, cy = 0f, cz = -0.112f, radius = 0.24f, depth = 0.024f, segments = 22)
        parts += trimVerts.toPart(color = silverTrim, shininess = 96f, specStrength = 0.82f)

        // Wide central 16:9 screen (protruding 0.020f ahead of z = 0.10)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0f, cy = 0.03f, cz = 0.110f, w = 0.96f, h = 0.54f, d = 0.020f)
        parts += screenVerts.toPart(color = screenGlow, shininess = 92f, specStrength = 0.70f, emissive = 0.38f)

        // Left D-Pad + analog nub + Right 4 PlayStation face buttons
        val ctrlVerts = MeshBuilder()
        ctrlVerts.addDPadZ(cx = -0.64f, cy = 0.10f, cz = 0.114f, armSpan = 0.18f, armWidth = 0.056f, depth = 0.028f)
        // Analog nub below D-Pad
        ctrlVerts.addCylinderZ(cx = -0.64f, cy = -0.16f, cz = 0.114f, radius = 0.062f, depth = 0.028f, segments = 16)
        // 4 face buttons on right
        ctrlVerts.addCylinderZ(cx = 0.64f, cy = 0.19f, cz = 0.114f, radius = 0.042f, depth = 0.028f, segments = 12)
        ctrlVerts.addCylinderZ(cx = 0.64f, cy = 0.01f, cz = 0.114f, radius = 0.042f, depth = 0.028f, segments = 12)
        ctrlVerts.addCylinderZ(cx = 0.55f, cy = 0.10f, cz = 0.114f, radius = 0.042f, depth = 0.028f, segments = 12)
        ctrlVerts.addCylinderZ(cx = 0.73f, cy = 0.10f, cz = 0.114f, radius = 0.042f, depth = 0.028f, segments = 12)
        parts += ctrlVerts.toPart(color = buttonTrim, shininess = 64f, specStrength = 0.55f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 10. Sega Master System (SMS)
    // -------------------------------------------------------------------------

    private fun buildMasterSystemMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val matteBlack = argbToFloat4(0xFF1A1C22.toInt())
        val darkGrey = argbToFloat4(0xFF2D313B.toInt())
        val redAccent = argbToFloat4(0xFFD92B2B.toInt())
        val whiteGrid = argbToFloat4(0xFFD6DAE2.toInt())

        // Angular wide black chassis: y in [-0.16, +0.10]
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = -0.03f, cz = 0f, w = 1.62f, h = 0.26f, d = 1.06f)
        // Raised angular rear deck step (y in [0.10, 0.17])
        bodyVerts.addBox(cx = 0f, cy = 0.135f, cz = -0.18f, w = 1.54f, h = 0.07f, d = 0.62f)
        parts += bodyVerts.toPart(color = matteBlack, shininess = 34f, specStrength = 0.32f)

        // Iconic red horizontal accent strip across front-top slope (y = 0.10 -> 0.124)
        val redVerts = MeshBuilder()
        redVerts.addBox(cx = 0f, cy = 0.112f, cz = 0.30f, w = 1.54f, h = 0.024f, d = 0.24f)
        // Red power/pause accent buttons on right
        redVerts.addBox(cx = 0.52f, cy = 0.184f, cz = -0.04f, w = 0.16f, h = 0.028f, d = 0.10f)
        parts += redVerts.toPart(color = redAccent, shininess = 56f, specStrength = 0.48f)

        // Subtle white/silver schematic accent bar on top-right
        val gridVerts = MeshBuilder()
        gridVerts.addBox(cx = 0.44f, cy = 0.182f, cz = -0.28f, w = 0.42f, h = 0.024f, d = 0.22f)
        parts += gridVerts.toPart(color = whiteGrid, shininess = 42f, specStrength = 0.36f)

        // Cartridge slot on left-top + Sega Card slot on front + twin controller ports
        val slotVerts = MeshBuilder()
        slotVerts.addBox(cx = -0.26f, cy = 0.182f, cz = -0.18f, w = 0.62f, h = 0.024f, d = 0.16f)
        // Front Sega Card slot
        slotVerts.addBox(cx = -0.40f, cy = -0.02f, cz = 0.542f, w = 0.34f, h = 0.055f, d = 0.024f)
        // Front controller ports
        slotVerts.addBox(cx = 0.22f, cy = -0.04f, cz = 0.542f, w = 0.18f, h = 0.08f, d = 0.024f)
        slotVerts.addBox(cx = 0.48f, cy = -0.04f, cz = 0.542f, w = 0.18f, h = 0.08f, d = 0.024f)
        parts += slotVerts.toPart(color = darkGrey, shininess = 26f, specStrength = 0.22f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 11. Sega Game Gear (GG)
    // -------------------------------------------------------------------------

    private fun buildGameGearMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val darkGrey = argbToFloat4(0xFF2C3038.toInt())
        val bezelBlack = argbToFloat4(0xFF14171E.toInt())
        val screenGlow = argbToFloat4(0xFF3588B8.toInt())
        val blueStart = argbToFloat4(0xFF2979FF.toInt())

        // Wide chunky handheld body: w = 1.62, h = 0.88, d = 0.30 (z in [-0.15, +0.15])
        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = 0f, cz = 0f, w = 1.62f, h = 0.88f, d = 0.30f)
        parts += bodyVerts.toPart(color = darkGrey, shininess = 32f, specStrength = 0.28f)

        // Central dark screen bezel (protruding 0.020f ahead of z = 0.15 -> front at 0.170)
        val bezelVerts = MeshBuilder()
        bezelVerts.addBox(cx = 0f, cy = 0.02f, cz = 0.160f, w = 0.84f, h = 0.66f, d = 0.020f)
        // Left D-Pad & Right 1/2 buttons
        bezelVerts.addDPadZ(cx = -0.58f, cy = 0.02f, cz = 0.164f, armSpan = 0.20f, armWidth = 0.068f, depth = 0.028f)
        bezelVerts.addCylinderZ(cx = 0.64f, cy = 0.06f, cz = 0.164f, radius = 0.062f, depth = 0.028f, segments = 16)
        bezelVerts.addCylinderZ(cx = 0.50f, cy = -0.06f, cz = 0.164f, radius = 0.062f, depth = 0.028f, segments = 16)
        parts += bezelVerts.toPart(color = bezelBlack, shininess = 40f, specStrength = 0.34f)

        // Backlit central screen (protruding 0.020f ahead of bezel -> front at 0.190)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0f, cy = 0.04f, cz = 0.180f, w = 0.66f, h = 0.50f, d = 0.020f)
        parts += screenVerts.toPart(color = screenGlow, shininess = 84f, specStrength = 0.62f, emissive = 0.38f)

        // Blue Start button above 1/2 buttons
        val startVerts = MeshBuilder()
        startVerts.addBox(cx = 0.58f, cy = 0.26f, cz = 0.164f, w = 0.14f, h = 0.055f, d = 0.028f)
        parts += startVerts.toPart(color = blueStart, shininess = 56f, specStrength = 0.48f)

        // Iconic RGB three-pill Game Gear emblem on bottom-right of bezel (front at 0.190)
        val redPill = MeshBuilder().apply { addBox(cx = 0.18f, cy = -0.26f, cz = 0.180f, w = 0.055f, h = 0.042f, d = 0.020f) }
        val greenPill = MeshBuilder().apply { addBox(cx = 0.25f, cy = -0.26f, cz = 0.180f, w = 0.055f, h = 0.042f, d = 0.020f) }
        val bluePill = MeshBuilder().apply { addBox(cx = 0.32f, cy = -0.26f, cz = 0.180f, w = 0.055f, h = 0.042f, d = 0.020f) }
        parts += redPill.toPart(color = argbToFloat4(0xFFE53935.toInt()), shininess = 64f, specStrength = 0.55f, emissive = 0.35f)
        parts += greenPill.toPart(color = argbToFloat4(0xFF43A047.toInt()), shininess = 64f, specStrength = 0.55f, emissive = 0.35f)
        parts += bluePill.toPart(color = argbToFloat4(0xFF1E88E5.toInt()), shininess = 64f, specStrength = 0.55f, emissive = 0.35f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 12. Upright Arcade Cabinet (arcade / fbneo / mame2003plus)
    // -------------------------------------------------------------------------

    private fun buildArcadeCabinetMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val navyCab = argbToFloat4(0xFF1E263B.toInt())
        val deckColor = argbToFloat4(0xFF2B3550.toInt())
        val darkMetal = argbToFloat4(0xFF10131C.toInt())
        val redTrim = argbToFloat4(0xFFE53935.toInt())

        // Strictly stacked non-overlapping cabinet volumes:
        // Lower base:     y in [-0.90, -0.05] (h = 0.85, cy = -0.475)
        // Control deck:   y in [-0.05, +0.09] (h = 0.14, cy =  0.020)
        // Upper housing:  y in [+0.09, +0.84] (h = 0.75, cy =  0.465)
        // Marquee shell:  y in [+0.84, +1.08] (h = 0.24, cy =  0.960)
        val cabVerts = MeshBuilder()
        cabVerts.addBox(cx = 0f, cy = -0.475f, cz = 0f, w = 1.05f, h = 0.85f, d = 0.72f)
        cabVerts.addBox(cx = 0f, cy = 0.465f, cz = -0.04f, w = 1.05f, h = 0.75f, d = 0.64f)
        cabVerts.addBox(cx = 0f, cy = 0.96f, cz = 0.02f, w = 1.07f, h = 0.24f, d = 0.80f)
        parts += cabVerts.toPart(color = navyCab, shininess = 34f, specStrength = 0.32f)

        // Protruding control deck (z in [-0.34, +0.56])
        val deckVerts = MeshBuilder()
        deckVerts.addBox(cx = 0f, cy = 0.02f, cz = 0.11f, w = 1.09f, h = 0.14f, d = 0.90f)
        parts += deckVerts.toPart(color = deckColor, shininess = 44f, specStrength = 0.40f)

        // Red T-molding side trim rails + glowing coin reject buttons
        val trimVerts = MeshBuilder()
        val trimX = 0.525f + 0.014f
        trimVerts.addBox(cx = -trimX, cy = -0.475f, cz = 0.366f, w = 0.028f, h = 0.85f, d = 0.028f)
        trimVerts.addBox(cx = trimX, cy = -0.475f, cz = 0.366f, w = 0.028f, h = 0.85f, d = 0.028f)
        trimVerts.addBox(cx = -trimX, cy = 0.465f, cz = 0.286f, w = 0.028f, h = 0.75f, d = 0.028f)
        trimVerts.addBox(cx = trimX, cy = 0.465f, cz = 0.286f, w = 0.028f, h = 0.75f, d = 0.028f)
        trimVerts.addBox(cx = -0.09f, cy = -0.46f, cz = 0.406f, w = 0.065f, h = 0.075f, d = 0.020f)
        trimVerts.addBox(cx = 0.09f, cy = -0.46f, cz = 0.406f, w = 0.065f, h = 0.075f, d = 0.020f)
        parts += trimVerts.toPart(color = redTrim, shininess = 64f, specStrength = 0.55f, emissive = 0.25f)

        // Coin door, CRT bezel, joystick dust washer, rear service door
        val metalVerts = MeshBuilder()
        metalVerts.addBox(cx = 0f, cy = -0.48f, cz = 0.378f, w = 0.44f, h = 0.38f, d = 0.036f)
        metalVerts.addBox(cx = 0f, cy = 0.465f, cz = 0.292f, w = 0.90f, h = 0.62f, d = 0.024f)
        metalVerts.addCylinderY(cx = -0.26f, cy = 0.099f, cz = 0.42f, radius = 0.075f, height = 0.018f, segments = 14)
        metalVerts.addBox(cx = 0f, cy = -0.38f, cz = -0.370f, w = 0.62f, h = 0.54f, d = 0.020f)
        parts += metalVerts.toPart(color = darkMetal, shininess = 28f, specStrength = 0.25f)

        // Glowing CRT screen (0.020f ahead of bezel front 0.304 -> front at 0.324)
        val screenVerts = MeshBuilder()
        screenVerts.addBox(cx = 0f, cy = 0.465f, cz = 0.314f, w = 0.78f, h = 0.50f, d = 0.020f)
        parts += screenVerts.toPart(color = argbToFloat4(0xFF2882D8.toInt()), shininess = 90f, specStrength = 0.68f, emissive = 0.42f)

        // Chrome joystick post + glossy red ball
        val postVerts = MeshBuilder()
        postVerts.addCylinderY(cx = -0.26f, cy = 0.173f, cz = 0.42f, radius = 0.020f, height = 0.13f, segments = 12)
        parts += postVerts.toPart(color = floatArrayOf(0.84f, 0.88f, 0.94f, 1.0f), shininess = 96f, specStrength = 0.85f)

        val ballVerts = MeshBuilder()
        ballVerts.addSphere(cx = -0.26f, cy = 0.255f, cz = 0.42f, radius = 0.056f, stacks = 10, slices = 14)
        parts += ballVerts.toPart(color = argbToFloat4(0xFFE5192A.toInt()), shininess = 96f, specStrength = 0.90f, emissive = 0.08f)

        // Colorful arcade buttons
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
            parts += btnVerts.toPart(color = argbToFloat4(colorInt), shininess = 72f, specStrength = 0.65f, emissive = 0.12f)
        }

        // Glowing top marquee light box (0.024f ahead of marquee front z = 0.42)
        val marqueeVerts = MeshBuilder()
        marqueeVerts.addBox(cx = 0f, cy = 0.96f, cz = 0.432f, w = 0.96f, h = 0.17f, d = 0.024f)
        parts += marqueeVerts.toPart(color = argbToFloat4(0xFF1CC6FF.toInt()), shininess = 64f, specStrength = 0.50f, emissive = 0.64f)

        return parts
    }

    // -------------------------------------------------------------------------
    // 13. Clean Retro Console Fallback
    // -------------------------------------------------------------------------

    private fun buildRetroFallbackMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val graphite = argbToFloat4(0xFF323744.toInt())
        val silver = argbToFloat4(0xFFB4BCC8.toInt())
        val darkSlot = argbToFloat4(0xFF161922.toInt())
        val tealLed = argbToFloat4(0xFF00E5FF.toInt())

        val bodyVerts = MeshBuilder()
        bodyVerts.addBox(cx = 0f, cy = -0.02f, cz = 0f, w = 1.48f, h = 0.28f, d = 1.08f)
        parts += bodyVerts.toPart(color = graphite, shininess = 38f, specStrength = 0.36f)

        val trimVerts = MeshBuilder()
        trimVerts.addBox(cx = 0f, cy = 0.132f, cz = 0.26f, w = 1.38f, h = 0.024f, d = 0.12f)
        parts += trimVerts.toPart(color = silver, shininess = 72f, specStrength = 0.64f)

        val slotVerts = MeshBuilder()
        slotVerts.addBox(cx = 0f, cy = 0.132f, cz = -0.14f, w = 0.68f, h = 0.024f, d = 0.16f)
        slotVerts.addBox(cx = -0.32f, cy = -0.04f, cz = 0.552f, w = 0.24f, h = 0.08f, d = 0.024f)
        slotVerts.addBox(cx = 0.32f, cy = -0.04f, cz = 0.552f, w = 0.24f, h = 0.08f, d = 0.024f)
        parts += slotVerts.toPart(color = darkSlot, shininess = 24f, specStrength = 0.22f)

        val ledVerts = MeshBuilder()
        ledVerts.addBox(cx = 0f, cy = -0.02f, cz = 0.552f, w = 0.16f, h = 0.032f, d = 0.024f)
        parts += ledVerts.toPart(color = tealLed, shininess = 72f, specStrength = 0.60f, emissive = 0.72f)

        return parts
    }

    // -------------------------------------------------------------------------
    // Geometry & Shader Helpers
    // -------------------------------------------------------------------------

    internal data class MeshPart(
        val vertexBuffer: FloatBuffer,
        val vertexCount: Int,
        val color: FloatArray,
        val shininess: Float = 36f,
        val specStrength: Float = 0.35f,
        val emissive: Float = 0f,
        val hasUV: Boolean = false,
        val hardwareAtlas: Boolean = false,
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

        /**
         * Builds a 3-piece non-overlapping D-Pad facing +Z (horizontal bar + top & bottom arms)
         * so there are zero coplanar overlapping faces at the center intersection.
         */
        fun addDPadZ(
            cx: Float,
            cy: Float,
            cz: Float,
            armSpan: Float,
            armWidth: Float,
            depth: Float,
        ) {
            val stubLen = ((armSpan - armWidth) * 0.5f).coerceAtLeast(0.01f)
            val stubOffset = armWidth * 0.5f + stubLen * 0.5f
            addBox(cx = cx, cy = cy, cz = cz, w = armSpan, h = armWidth, d = depth)
            addBox(cx = cx, cy = cy + stubOffset, cz = cz, w = armWidth, h = stubLen, d = depth)
            addBox(cx = cx, cy = cy - stubOffset, cz = cz, w = armWidth, h = stubLen, d = depth)
        }

        /**
         * Builds a 3-piece non-overlapping D-Pad facing +Y (horizontal X bar + front & back Z arms)
         * for flat deck handhelds like Nintendo DS.
         */
        fun addDPadY(
            cx: Float,
            cy: Float,
            cz: Float,
            armSpan: Float,
            armWidth: Float,
            height: Float,
        ) {
            val stubLen = ((armSpan - armWidth) * 0.5f).coerceAtLeast(0.01f)
            val stubOffset = armWidth * 0.5f + stubLen * 0.5f
            addBox(cx = cx, cy = cy, cz = cz, w = armSpan, h = height, d = armWidth)
            addBox(cx = cx, cy = cy, cz = cz + stubOffset, w = armWidth, h = height, d = stubLen)
            addBox(cx = cx, cy = cy, cz = cz - stubOffset, w = armWidth, h = height, d = stubLen)
        }

        /**
         * Adds a solid box positioned at `(cx, localCy, localCz)` relative to `(0, pivotY, pivotZ)`
         * and rotated around the X axis by [angleDeg] degrees (used for open clamshell lids).
         */
        fun addBoxRotatedX(
            cx: Float,
            localCy: Float,
            localCz: Float,
            w: Float,
            h: Float,
            d: Float,
            pivotY: Float,
            pivotZ: Float,
            angleDeg: Float,
        ) {
            val rad = (angleDeg * PI / 180.0).toFloat()
            val c = cos(rad)
            val s = sin(rad)

            fun rotY(
                y: Float,
                z: Float,
            ): Float = pivotY + y * c - z * s

            fun rotZ(
                y: Float,
                z: Float,
            ): Float = pivotZ + y * s + z * c

            fun rotNy(
                ny: Float,
                nz: Float,
            ): Float = ny * c - nz * s

            fun rotNz(
                ny: Float,
                nz: Float,
            ): Float = ny * s + nz * c

            fun addRotatedQuad(
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
                addQuad(
                    x0, rotY(y0, z0), rotZ(y0, z0),
                    x1, rotY(y1, z1), rotZ(y1, z1),
                    x2, rotY(y2, z2), rotZ(y2, z2),
                    x3, rotY(y3, z3), rotZ(y3, z3),
                    nx, rotNy(ny, nz), rotNz(ny, nz),
                )
            }

            val x0 = cx - w * 0.5f
            val x1 = cx + w * 0.5f
            val y0 = localCy - h * 0.5f
            val y1 = localCy + h * 0.5f
            val z0 = localCz - d * 0.5f
            val z1 = localCz + d * 0.5f

            // Front (+Z)
            addRotatedQuad(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0f, 0f, 1f)
            // Back (-Z)
            addRotatedQuad(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0f, 0f, -1f)
            // Right (+X)
            addRotatedQuad(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1f, 0f, 0f)
            // Left (-X)
            addRotatedQuad(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1f, 0f, 0f)
            // Top (+Y)
            addRotatedQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f)
            // Bottom (-Y)
            addRotatedQuad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f)
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
                addVertex(cx, y1, cz, 0f, 1f, 0f)
                addVertex(x1, y1, z1, 0f, 1f, 0f)
                addVertex(x0, y1, z0, 0f, 1f, 0f)

                // Bottom cap (-Y)
                addVertex(cx, y0, cz, 0f, -1f, 0f)
                addVertex(x0, y0, z0, 0f, -1f, 0f)
                addVertex(x1, y0, z1, 0f, -1f, 0f)
            }
        }

        fun addRingY(
            cx: Float,
            cy: Float,
            cz: Float,
            innerRadius: Float,
            outerRadius: Float,
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

                val ix0 = cx + innerRadius * c0
                val iz0 = cz + innerRadius * s0
                val ix1 = cx + innerRadius * c1
                val iz1 = cz + innerRadius * s1

                val ox0 = cx + outerRadius * c0
                val oz0 = cz + outerRadius * s0
                val ox1 = cx + outerRadius * c1
                val oz1 = cz + outerRadius * s1

                val nx = (c0 + c1) * 0.5f
                val nz = (s0 + s1) * 0.5f
                val nLen = sqrt(nx * nx + nz * nz).coerceAtLeast(1e-5f)
                val ux = nx / nLen
                val uz = nz / nLen

                // Outer wall (+radial)
                addQuad(ox1, y1, oz1, ox1, y0, oz1, ox0, y0, oz0, ox0, y1, oz0, ux, 0f, uz)
                // Inner wall (-radial)
                addQuad(ix0, y1, iz0, ix0, y0, iz0, ix1, y0, iz1, ix1, y1, iz1, -ux, 0f, -uz)
                // Top face (+Y)
                addQuad(ix1, y1, iz1, ox1, y1, oz1, ox0, y1, oz0, ix0, y1, iz0, 0f, 1f, 0f)
                // Bottom face (-Y)
                addQuad(ix0, y0, iz0, ox0, y0, oz0, ox1, y0, oz1, ix1, y0, iz1, 0f, -1f, 0f)
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

                val nx = (c0 + c1) * 0.5f
                val ny = (s0 + s1) * 0.5f
                val nLen = sqrt(nx * nx + ny * ny).coerceAtLeast(1e-5f)
                addQuad(
                    x0, y0, z1,
                    x0, y0, z0,
                    x1, y1, z0,
                    x1, y1, z1,
                    nx / nLen, ny / nLen, 0f,
                )

                // Back cap (-Z)
                addVertex(cx, cy, z0, 0f, 0f, -1f)
                addVertex(x1, y1, z0, 0f, 0f, -1f)
                addVertex(x0, y0, z0, 0f, 0f, -1f)

                // Front cap (+Z)
                addVertex(cx, cy, z1, 0f, 0f, 1f)
                addVertex(x0, y0, z1, 0f, 0f, 1f)
                addVertex(x1, y1, z1, 0f, 0f, 1f)
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

                    addVertex(cx + radius * nx01, cy + radius * y0, cz + radius * nz01, nx01, y0, nz01)
                    addVertex(cx + radius * nx11, cy + radius * y1, cz + radius * nz11, nx11, y1, nz11)
                    addVertex(cx + radius * nx10, cy + radius * y1, cz + radius * nz10, nx10, y1, nz10)

                    addVertex(cx + radius * nx01, cy + radius * y0, cz + radius * nz01, nx01, y0, nz01)
                    addVertex(cx + radius * nx10, cy + radius * y1, cz + radius * nz10, nx10, y1, nz10)
                    addVertex(cx + radius * nx00, cy + radius * y0, cz + radius * nz00, nx00, y0, nz00)
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
            addVertex(x0, y0, z0, nx, ny, nz)
            addVertex(x1, y1, z1, nx, ny, nz)
            addVertex(x2, y2, z2, nx, ny, nz)

            addVertex(x0, y0, z0, nx, ny, nz)
            addVertex(x2, y2, z2, nx, ny, nz)
            addVertex(x3, y3, z3, nx, ny, nz)
        }

        private fun addVertex(
            x: Float,
            y: Float,
            z: Float,
            nx: Float,
            ny: Float,
            nz: Float,
        ) {
            data.add(x)
            data.add(y)
            data.add(z)
            data.add(nx)
            data.add(ny)
            data.add(nz)
        }

        fun toPart(
            color: FloatArray,
            shininess: Float = 36f,
            specStrength: Float = 0.35f,
            emissive: Float = 0f,
            atlasTile: Int = -1,
            uvScale: Float = 0.55f,
        ): MeshPart {
            val vertCount = data.size / FLOATS_PER_VERTEX
            if (atlasTile < 0) {
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
                    vertexCount = vertCount,
                    color = color,
                    shininess = shininess,
                    specStrength = specStrength,
                    emissive = emissive,
                    hasUV = false,
                )
            }
            // Box-project UV coordinates into the 4x2 Pixel-Art Texture Atlas (8 floats/vertex)
            val col = atlasTile % 4
            val row = atlasTile / 4
            val out = FloatArray(vertCount * FLOATS_PER_VERTEX_UV)
            for (v in 0 until vertCount) {
                val src = v * FLOATS_PER_VERTEX
                val dst = v * FLOATS_PER_VERTEX_UV
                val px = data[src]
                val py = data[src + 1]
                val pz = data[src + 2]
                val nx = data[src + 3]
                val ny = data[src + 4]
                val nz = data[src + 5]
                val ax = abs(nx)
                val ay = abs(ny)
                val az = abs(nz)
                val rawU: Float
                val rawV: Float
                when {
                    ay >= ax && ay >= az -> {
                        rawU = px * uvScale
                        rawV = pz * uvScale
                    }
                    ax >= az -> {
                        rawU = pz * uvScale
                        rawV = -py * uvScale
                    }
                    else -> {
                        rawU = px * uvScale
                        rawV = -py * uvScale
                    }
                }
                var lu = (((rawU % 1f) + 1f) % 1f).coerceIn(0.03f, 0.97f)
                var lv = (((rawV % 1f) + 1f) % 1f).coerceIn(0.03f, 0.97f)
                if (atlasTile >= 4) {
                    val faceStart = (v / 6) * 6
                    var minU = Float.POSITIVE_INFINITY; var maxU = Float.NEGATIVE_INFINITY
                    var minV = Float.POSITIVE_INFINITY; var maxV = Float.NEGATIVE_INFINITY
                    for (fv in faceStart until kotlin.math.min(faceStart + 6, vertCount)) {
                        val fi = fv * FLOATS_PER_VERTEX
                        val fu = if (ay >= ax && ay >= az) data[fi] else if (ax >= az) data[fi + 2] else data[fi]
                        val fvv = if (ay >= ax && ay >= az) data[fi + 2] else -data[fi + 1]
                        minU = kotlin.math.min(minU, fu); maxU = kotlin.math.max(maxU, fu)
                        minV = kotlin.math.min(minV, fvv); maxV = kotlin.math.max(maxV, fvv)
                    }
                    val uPos = if (ay >= ax && ay >= az) px else if (ax >= az) pz else px
                    val vPos = if (ay >= ax && ay >= az) pz else -py
                    lu = 0.03f + 0.94f * ((uPos - minU) / (maxU - minU).coerceAtLeast(0.001f))
                    lv = 0.03f + 0.94f * ((vPos - minV) / (maxV - minV).coerceAtLeast(0.001f))
                }
                out[dst] = px
                out[dst + 1] = py
                out[dst + 2] = pz
                out[dst + 3] = nx
                out[dst + 4] = ny
                out[dst + 5] = nz
                out[dst + 6] = col * 0.25f + lu * 0.25f
                out[dst + 7] = row * 0.50f + lv * 0.50f
            }
            val buf =
                ByteBuffer.allocateDirect(out.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(out)
                        position(0)
                    }
            return MeshPart(
                vertexBuffer = buf,
                vertexCount = vertCount,
                color = color,
                shininess = shininess,
                specStrength = specStrength,
                emissive = emissive,
                hasUV = true,
            )
        }
    }

    private fun normalizeKey(key: String): String = key.trim().lowercase(Locale.US)

    private fun argbToFloat4(argb: Int): FloatArray {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
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

private const val FLOATS_PER_VERTEX = 6
private const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
private const val FLOATS_PER_VERTEX_UV = 8
private const val STRIDE_BYTES_UV = FLOATS_PER_VERTEX_UV * 4

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
    uniform float uShininess;
    uniform float uSpecStrength;
    uniform float uEmissive;
    uniform vec3 uCameraPos;
    uniform sampler2D uTexture;
    uniform float uUseTexture;

    varying vec3 vWorldPos;
    varying vec3 vNormal;
    varying vec2 vTexCoord;

    void main() {
        vec3 N = normalize(vNormal);
        vec3 V = normalize(uCameraPos - vWorldPos);

        vec3 texRgb = (uUseTexture > 0.5) ? texture2D(uTexture, vTexCoord).rgb : vec3(1.0);
        vec3 baseColor = clamp(uColor.rgb * texRgb, 0.0, 1.0);

        // Warm 80s/90s cozy room key light + subtle retro stepped shading
        vec3 keyDir = normalize(vec3(0.45, 0.82, 0.72));
        float rawDiff = max(dot(N, keyDir), 0.0);
        float steppedDiff = mix(rawDiff, floor(rawDiff * 6.0 + 0.5) / 6.0, 0.30);
        vec3 keyHalf = normalize(keyDir + V);
        float keySpec = pow(max(dot(N, keyHalf), 0.0), uShininess) * uSpecStrength;

        // Warm amber bedside/lamp fill + soft moonlight bounce
        vec3 fillDir = normalize(vec3(-0.75, 0.15, 0.55));
        float fillDiff = max(dot(N, fillDir), 0.0) * 0.30;

        vec3 rimDir = normalize(vec3(-0.40, 0.65, -0.80));
        float rimDiff = max(dot(N, rimDir), 0.0) * 0.34;
        float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.24;

        vec3 ambient = baseColor * 0.38;
        vec3 diffuse = baseColor * vec3(1.04, 0.99, 0.93) * (steppedDiff * 0.74 + fillDiff)
                     + baseColor * vec3(0.75, 0.88, 1.0) * rimDiff;
        vec3 specular = vec3(1.0, 0.98, 0.94) * keySpec
                      + vec3(0.70, 0.86, 1.0) * fresnel * uSpecStrength;
        vec3 emissive = baseColor * uEmissive;

        vec3 finalRgb = ambient + diffuse + specular + emissive;
        gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), uColor.a);
    }
"""

/**
 * Generates ONE tiny 256x256 GL_NEAREST crisp Pixel-Art Texture Atlas (256 KB VRAM total!)
 * arranged in a 4x2 grid (each tile = 64x128 px):
 *   Row 0: [0] 90s Wood Wall Paneling | [1] Oak Floor Planks | [2] Retro Carpet Rug | [3] Hardware Pixel Grain
 *   Row 1: [4] CRT Pixel Game Screen  | [5] Retro Game Poster| [6] Moonlit Window   | [7] Neon Arcade Marquee
 */
internal fun createPixelArtAtlasTexture(
    context: Context? = null,
    assetPath: String = "textures/pixel_atlas_v2.png",
): Int {
    val atlas = context?.let {
        runCatching { it.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        } }.getOrNull()
    }
    if (atlas != null) {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlas, 0)
        atlas.recycle()
        return ids[0]
    }
    val w = 256
    val h = 256
    val pixels = IntArray(w * h)

    fun setPx(x: Int, y: Int, color: Int) {
        if (x in 0 until w && y in 0 until h) pixels[y * w + x] = color
    }

    fun hash(x: Int, y: Int, seed: Int): Int {
        var n = x * 374761393 + y * 668265263 + seed * 1442695041
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and 0xFF
    }

    fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    for (ty in 0 until 128) {
        for (tx in 0 until 64) {
            val px = tx / 2
            val py = ty / 2

            // Tile 0 (0,0): Warm 90s vertical wood wall paneling (like Reference Photo 2!)
            val seam = (px % 8 == 0)
            val grain = ((hash(px, py / 3, 1) - 128) / 14)
            val wBase = if (seam) 145 else 215 + grain
            setPx(tx, ty, rgb(wBase, (wBase * 0.88f).toInt(), (wBase * 0.76f).toInt()))

            // Tile 1 (64,0): Warm oak floor planks
            val plankSeam = (py % 8 == 0) || ((px + (py / 8) * 11) % 24 == 0)
            val fGrain = ((hash(px / 2, py, 2) - 128) / 14)
            val fBase = if (plankSeam) 140 else 218 + fGrain
            setPx(64 + tx, ty, rgb(fBase, (fBase * 0.86f).toInt(), (fBase * 0.74f).toInt()))

            // Tile 2 (128,0): Retro 80s/90s geometric pixel carpet rug
            val border = px < 3 || px >= 29 || py < 4 || py >= 60
            val diamond = ((px + py) % 6 == 0) || ((px - py + 64) % 6 == 0)
            val rugC =
                when {
                    border -> rgb(245, 200, 120)
                    diamond -> rgb(225, 165, 205)
                    else -> rgb(185, 145, 175)
                }
            setPx(128 + tx, ty, rugC)

            // Tile 3 (192,0): Crisp subtle 16-bit hardware pixel grain (multiplied by console uColor)
            val dither = if ((px + py) % 2 == 0) 6 else -4
            val hNoise = ((hash(px, py, 3) - 128) / 26)
            val gVal = (242 + dither + hNoise).coerceIn(210, 255)
            setPx(192 + tx, ty, rgb(gVal, gVal, gVal))

            // Tile 4 (0,128): Colorful 16-bit Retro Platformer CRT Game Screen
            val scan = if (ty % 2 == 0) 1.0f else 0.84f
            val scrC =
                when {
                    py < 8 -> rgb(20, 24, 52) // Top score HUD bar
                    py > 50 -> rgb(46, 176, 72) // Green pixel grass ground
                    py in 46..50 && (px % 4 < 3) -> rgb(68, 214, 92) // Grass highlight
                    py in 28..34 && px in 10..15 -> rgb(255, 204, 0) // Yellow ? block 1
                    py in 28..34 && px in 18..23 -> rgb(255, 204, 0) // Yellow ? block 2
                    py in 40..49 && px in 6..10 -> rgb(235, 48, 48) // Red hero sprite
                    py in 14..19 && px in 20..28 -> rgb(245, 248, 255) // White pixel cloud
                    else -> rgb(66, 165, 245) // Bright blue retro sky
                }
            val sr = (((scrC ushr 16) and 0xFF) * scan).toInt()
            val sg = (((scrC ushr 8) and 0xFF) * scan).toInt()
            val sb = ((scrC and 0xFF) * scan).toInt()
            setPx(tx, 128 + ty, rgb(sr, sg, sb))

            // Tile 5 (64,128): Classic 80s/90s Pixel-Art Game Poster
            val pC =
                when {
                    px < 2 || px >= 30 || py < 2 || py >= 62 -> rgb(25, 22, 34) // Dark frame
                    py < 14 -> rgb(255, 196, 0) // Bold game title logo banner
                    py in 16..34 && (px - 16) * (px - 16) + (py - 26) * (py - 26) < 64 -> rgb(255, 92, 64) // Retro sun/emblem
                    py > 42 && (px + py) % 4 == 0 -> rgb(0, 229, 255) // Outrun neon grid
                    py > 42 -> rgb(54, 24, 92)
                    else -> rgb(34, 44, 110)
                }
            setPx(64 + tx, 128 + ty, pC)

            // Tile 6 (128,128): Blue Moonlit Night Window (like Reference Photo 1!)
            val muntin = (px in 15..16) || (py in 31..32) || px < 2 || px >= 30 || py < 2 || py >= 62
            val moon = (px - 22) * (px - 22) + (py - 16) * (py - 16) < 22
            val star = hash(px, py, 9) > 250 && py < 46
            val winC =
                when {
                    muntin -> rgb(42, 34, 48)
                    moon -> rgb(255, 232, 130)
                    star -> rgb(225, 242, 255)
                    py > 48 -> rgb(14, 24, 52) // Night tree/city silhouette
                    else -> rgb(26, 62, 134) // Deep moonlight blue sky
                }
            setPx(128 + tx, 128 + ty, winC)

            // Tile 7 (192,128): Vibrant 80s/90s Neon Arcade Marquee & Side Art
            val diag = (px + py) % 10
            val arcC =
                when {
                    py < 6 || py >= 58 -> rgb(255, 23, 68)
                    diag < 2 -> rgb(0, 229, 255)
                    diag in 4..5 -> rgb(255, 214, 0)
                    else -> rgb(42, 22, 88)
                }
            setPx(192 + tx, 128 + ty, arcC)
        }
    }

    val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    val texIds = IntArray(1)
    GLES20.glGenTextures(1, texIds, 0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    bmp.recycle()
    return texIds[0]
}

// =============================================================================
// Shared Multi-Object OpenGL ES 2.0 Program Helper (UV Pixel-Art Atlas Enabled)
// =============================================================================

private class GlProgramHelper(private val context: Context? = null) {
    private var programId: Int = 0
    private var atlasTextureId: Int = 0
    private var hardwareTextureId: Int = 0
    private var aPositionLoc: Int = -1
    private var aNormalLoc: Int = -1
    private var aTexCoordLoc: Int = -1
    private var uMVPMatrixLoc: Int = -1
    private var uModelMatrixLoc: Int = -1
    private var uColorLoc: Int = -1
    private var uShininessLoc: Int = -1
    private var uSpecStrengthLoc: Int = -1
    private var uEmissiveLoc: Int = -1
    private var uCameraPosLoc: Int = -1
    private var uTextureLoc: Int = -1
    private var uUseTextureLoc: Int = -1

    fun init() {
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uColorLoc = GLES20.glGetUniformLocation(programId, "uColor")
        uShininessLoc = GLES20.glGetUniformLocation(programId, "uShininess")
        uSpecStrengthLoc = GLES20.glGetUniformLocation(programId, "uSpecStrength")
        uEmissiveLoc = GLES20.glGetUniformLocation(programId, "uEmissive")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
        uUseTextureLoc = GLES20.glGetUniformLocation(programId, "uUseTexture")
        atlasTextureId = createPixelArtAtlasTexture(context)
        hardwareTextureId = createPixelArtAtlasTexture(context, "textures/hardware_atlas_v3.png")
    }

    fun beginFrame(
        cameraX: Float,
        cameraY: Float,
        cameraZ: Float,
    ) {
        GLES20.glUseProgram(programId)
        GLES20.glUniform3f(uCameraPosLoc, cameraX, cameraY, cameraZ)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(uTextureLoc, 0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
    }

    fun drawParts(
        parts: List<MeshPart>,
        modelMatrix: FloatArray,
        mvpMatrix: FloatArray,
    ) {
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)

        for (part in parts) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (part.hardwareAtlas) hardwareTextureId else atlasTextureId)
            val buf = part.vertexBuffer
            val minFloats = if (part.hasUV) FLOATS_PER_VERTEX_UV else FLOATS_PER_VERTEX
            if (part.vertexCount <= 0 || buf.limit() < minFloats) continue

            val stride = if (part.hasUV) STRIDE_BYTES_UV else STRIDE_BYTES
            buf.position(0)
            GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(3)
            GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, stride, buf)
            if (part.hasUV && aTexCoordLoc >= 0) {
                GLES20.glEnableVertexAttribArray(aTexCoordLoc)
                buf.position(6)
                GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buf)
                GLES20.glUniform1f(uUseTextureLoc, 1.0f)
            } else {
                if (aTexCoordLoc >= 0) GLES20.glDisableVertexAttribArray(aTexCoordLoc)
                GLES20.glUniform1f(uUseTextureLoc, 0.0f)
            }

            GLES20.glUniform4fv(uColorLoc, 1, part.color, 0)
            GLES20.glUniform1f(uShininessLoc, part.shininess)
            GLES20.glUniform1f(uSpecStrengthLoc, part.specStrength)
            GLES20.glUniform1f(uEmissiveLoc, part.emissive)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, part.vertexCount)
        }
    }

    fun endFrame() {
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aNormalLoc)
        if (aTexCoordLoc >= 0) GLES20.glDisableVertexAttribArray(aTexCoordLoc)
    }
}

// =============================================================================
// 1. Locadora Ring 3D Renderer (Tomb Raider-style 3D Circular Console Ring)
// =============================================================================

private class LocadoraRing3DRenderer(
    private val appContext: Context,
    initialConsoleKeys: List<String>,
    initialSelectedIndex: Int,
) : GLSurfaceView.Renderer {
    private val stateLock = Any()
    @Volatile private var spinning: Boolean = true
    fun setSpinning(value: Boolean) { spinning = value }
    private var consoleKeys: List<String> = initialConsoleKeys.map { canonicalRoomKey(it) }
    private var targetSelectedIndex: Int =
        if (consoleKeys.isEmpty()) 0 else initialSelectedIndex.coerceIn(0, consoleKeys.lastIndex)

    private val program = GlProgramHelper(appContext)
    private val meshCache = HashMap<String, List<MeshPart>>()
    private var pedestalParts: List<MeshPart> = emptyList()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewportAspect: Float = 16f / 9f
    private var lastFrameNanos: Long = 0L

    private var phaseInitialized: Boolean = false
    private var lastKeyCount: Int = -1
    private var lastSelectedIndex: Int = -1
    private var currentPhase: Float = 0f
    private var selectedSpinDeg: Float = HERO_ANGLE_DEG

    fun updateState(
        newConsoleKeys: List<String>,
        newSelectedIndex: Int,
    ) {
        val normalized = newConsoleKeys.map { canonicalRoomKey(it) }
        val clamped = if (normalized.isEmpty()) 0 else newSelectedIndex.coerceIn(0, normalized.lastIndex)
        synchronized(stateLock) {
            consoleKeys = normalized
            targetSelectedIndex = clamped
        }
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        // Rich warm dark Locadora velvet/mahogany atmosphere
        GLES20.glClearColor(0.08f, 0.045f, 0.03f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        program.init()
        Matrix.setIdentityM(identityMatrix, 0)
        pedestalParts = buildLocadoraPedestalMesh()
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
        Matrix.perspectiveM(projectionMatrix, 0, 38f, viewportAspect, 0.5f, 45.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val keys: List<String>
        val selIdx: Int
        synchronized(stateLock) {
            keys = consoleKeys
            selIdx = targetSelectedIndex
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now
        }
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        val count = keys.size
        if (count > 0) {
            if (!phaseInitialized || count != lastKeyCount) {
                currentPhase = selIdx.toFloat()
                selectedSpinDeg = HERO_ANGLE_DEG
                lastSelectedIndex = selIdx
                lastKeyCount = count
                phaseInitialized = true
            } else {
                if (selIdx != lastSelectedIndex) {
                    selectedSpinDeg = HERO_ANGLE_DEG
                    lastSelectedIndex = selIdx
                }
                // Shortest circular path orbital interpolation
                var delta = (selIdx.toFloat() - currentPhase) % count
                if (delta > count * 0.5f) delta -= count
                if (delta < -count * 0.5f) delta += count
                val smoothFactor = 1f - exp(-dt * 8.5f)
                currentPhase = ((currentPhase + delta * smoothFactor) % count + count) % count
            }
            if (spinning) selectedSpinDeg = (selectedSpinDeg + dt * 30f) % 360f
        }

        val aspectComp = if (viewportAspect < 1.0f) 1.0f / viewportAspect else 1f
        val camY = 1.35f
        val camZ = 7.8f * aspectComp

        Matrix.setLookAtM(
            viewMatrix,
            0,
            0f,
            camY,
            camZ,
            0f,
            -1.05f,
            0f,
            0f,
            1f,
            0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        program.beginFrame(0f, camY, camZ)

        // Draw grounded golden Locadora ring pedestal plane
        program.drawParts(pedestalParts, identityMatrix, vpMatrix)

        // Draw every console in the ring as a real 3D model
        for (i in 0 until count) {
            val key = keys[i]
            val parts = meshCache.getOrPut(key) { loadConsoleMesh(appContext, key) }
            val angleRad = ((i - currentPhase) / count.toFloat()) * (2.0 * PI)
            val x = sin(angleRad).toFloat() * RING_RADIUS
            val z = cos(angleRad).toFloat() * RING_RADIUS

            val isSelected = i == selIdx
            if (!isSelected && cos(angleRad) < -0.20) continue
            val normScale = consoleRingNormScale(key)
            val scale = (if (isSelected) 1.30f else 0.85f) * normScale
            val y = if (isSelected) -0.45f else -0.64f
            val rotY = if (isSelected) selectedSpinDeg else HERO_ANGLE_DEG

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, x, y, z)
            Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            program.drawParts(parts, modelMatrix, mvpMatrix)
        }

        program.endFrame()
    }

    private fun consoleRingNormScale(key: String): Float {
        return when (key) {
            "arcade", "fbneo", "mame2003plus" -> 0.62f
            "gb", "gbc" -> 0.80f
            "gba", "gg", "psp", "nds" -> 0.90f
            else -> 1.0f
        }
    }

    private fun buildLocadoraPedestalMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val mahoganyFloor = argbToFloat4(0xFF22130D.toInt())
        val darkVelvet = argbToFloat4(0xFF170B08.toInt())
        val warmGold = argbToFloat4(0xFFD9A441.toInt())
        val brightGold = argbToFloat4(0xFFFFD56B.toInt())

        // Deep mahogany circular stage base: y in [-0.88, -0.74]
        val floorVerts = MeshBuilder()
        floorVerts.addCylinderY(cx = 0f, cy = -0.81f, cz = 0f, radius = 4.35f, height = 0.14f, segments = 48)
        parts += floorVerts.toPart(color = mahoganyFloor, shininess = 32f, specStrength = 0.25f)

        // Recessed velvet inner medallion disc: y in [-0.74, -0.71]
        val velvetVerts = MeshBuilder()
        velvetVerts.addCylinderY(cx = 0f, cy = -0.725f, cz = 0f, radius = 2.88f, height = 0.030f, segments = 44)
        parts += velvetVerts.toPart(color = darkVelvet, shininess = 18f, specStrength = 0.12f)

        // Main circular ring track beneath the consoles (radius ~ 3.4f): y in [-0.74, -0.68]
        val trackVerts = MeshBuilder()
        trackVerts.addRingY(
            cx = 0f,
            cy = -0.71f,
            cz = 0f,
            innerRadius = 2.94f,
            outerRadius = 3.86f,
            height = 0.060f,
            segments = 56,
        )
        parts += trackVerts.toPart(color = warmGold, shininess = 76f, specStrength = 0.62f, emissive = 0.20f)

        // Glowing golden outer & inner accent rings on top of the track: y in [-0.68, -0.648]
        val glowVerts = MeshBuilder()
        glowVerts.addRingY(
            cx = 0f,
            cy = -0.664f,
            cz = 0f,
            innerRadius = 3.74f,
            outerRadius = 3.84f,
            height = 0.032f,
            segments = 56,
        )
        glowVerts.addRingY(
            cx = 0f,
            cy = -0.664f,
            cz = 0f,
            innerRadius = 2.96f,
            outerRadius = 3.06f,
            height = 0.032f,
            segments = 56,
        )
        // Front-center golden spotlight pedestal ring under the selected console (0, y, +RING_RADIUS)
        glowVerts.addCylinderY(
            cx = 0f,
            cy = -0.664f,
            cz = RING_RADIUS,
            radius = 0.92f,
            height = 0.032f,
            segments = 32,
        )
        parts += glowVerts.toPart(color = brightGold, shininess = 92f, specStrength = 0.78f, emissive = 0.62f)

        return parts
    }

    companion object {
        private const val RING_RADIUS = 3.4f
        private const val HERO_ANGLE_DEG = 28f
    }
}

// =============================================================================
// 2. Console Room 3D Renderer (Full 360° Enclosed 80s/90s Retro Gamer Bedroom)
// =============================================================================

private data class RoomConsoleStation(
    val key: String,
    val x: Float,
    val y: Float, // Exact resting shelf/floor surface Y (all Blender .l3d models have bottom at y=0)
    val z: Float,
    val rotY: Float, // Degrees: 0=+Z (North wall facing South), -90=-X (East wall facing West), +90=+X (West wall facing East)
    val scale: Float,
    val lookHeightOffset: Float, // Vertical center of console to aim camera at
    val camDistance: Float,
    val camElevation: Float,
    val camSideAngleDeg: Float = 18f, // 3/4 hero offset so console front + side + top all shine!
)

private val ALL_ROOM_STATIONS: List<RoomConsoleStation> =
    listOf(
        // CENTER CARPET RUG IN FRONT OF CRT TV (just like Reference Photos 1 & 2! 2.70 units apart -> ZERO clipping!)
        RoomConsoleStation("snes", -1.35f, -1.35f, -1.85f, 12f, 0.85f, 0.16f, 1.95f, 0.90f, -16f),
        RoomConsoleStation("psx", 1.35f, -1.35f, -1.85f, -12f, 0.85f, 0.16f, 1.95f, 0.90f, 16f),

        // NORTH WALL — SPACIOUS 1-CONSOLE-PER-CUBBY 90s TV RACK (x in [-5.3, +3.7], 1.80–2.20 units apart -> ZERO clipping!)
        // Top open plank (y = -0.30, z = -4.55) flanking the Sony CRT TV (centered at x = -0.80, z = -5.12)
        RoomConsoleStation("nes", -4.35f, -0.30f, -4.55f, 6f, 0.78f, 0.16f, 2.00f, 0.84f, -16f),
        RoomConsoleStation("n64", -2.55f, -0.30f, -4.55f, 4f, 0.78f, 0.18f, 2.00f, 0.84f, -14f),
        RoomConsoleStation("md", 1.05f, -0.30f, -4.55f, -4f, 0.78f, 0.16f, 2.00f, 0.84f, 14f),
        RoomConsoleStation("segacd", 2.85f, -0.30f, -4.55f, -6f, 0.76f, 0.16f, 2.00f, 0.84f, 16f),
        // Middle OPEN cubbies (4 wide cubbies, 2.20 units apart, y = -0.96, z = -4.50, 100% open front!)
        RoomConsoleStation("sms", -4.15f, -0.96f, -4.50f, 4f, 0.76f, 0.14f, 2.05f, 0.62f, -14f),
        RoomConsoleStation("atari2600", -1.95f, -0.96f, -4.50f, 2f, 0.78f, 0.15f, 2.05f, 0.62f, -12f),
        RoomConsoleStation("atari7800", 0.25f, -0.96f, -4.50f, -2f, 0.78f, 0.14f, 2.05f, 0.62f, 12f),
        RoomConsoleStation("pce", 2.45f, -0.96f, -4.50f, -4f, 0.82f, 0.14f, 1.95f, 0.62f, 14f),

        // NE CORNER — VIP STANDING ARCADE CORNER (2.3+ units clear of all shelves, facing SW rotY = -45°!)
        RoomConsoleStation("arcade", 5.15f, -1.40f, -4.55f, -45f, 1.00f, 1.12f, 2.95f, 1.50f, 8f),

        // EAST WALL — 3-TIER WIDE-SPACED HANDHELD VITRINE (1.45 units apart horizontally, 0.82 vertically -> ZERO clipping!)
        // Top tier (y = +0.86, x = +5.90, facing West rotY = -90°)
        RoomConsoleStation("gb", 5.90f, 0.86f, -1.10f, -90f, 0.85f, 0.34f, 1.75f, 0.44f, 16f),
        RoomConsoleStation("gbc", 5.90f, 0.86f, 0.35f, -90f, 0.85f, 0.34f, 1.75f, 0.44f, 16f),
        RoomConsoleStation("gba", 5.90f, 0.86f, 1.80f, -90f, 0.90f, 0.25f, 1.75f, 0.42f, 16f),
        RoomConsoleStation("nds", 5.90f, 0.86f, 3.25f, -90f, 0.88f, 0.28f, 1.80f, 0.48f, 18f),
        // Middle tier (y = +0.04, x = +5.90)
        RoomConsoleStation("3ds", 5.90f, 0.04f, -1.10f, -90f, 0.88f, 0.28f, 1.80f, 0.46f, 18f),
        RoomConsoleStation("psp", 5.90f, 0.04f, 0.35f, -90f, 0.90f, 0.24f, 1.75f, 0.40f, 16f),
        RoomConsoleStation("gg", 5.90f, 0.04f, 1.80f, -90f, 0.88f, 0.26f, 1.75f, 0.40f, 16f),
        RoomConsoleStation("lynx", 5.90f, 0.04f, 3.25f, -90f, 0.88f, 0.24f, 1.75f, 0.40f, 16f),
        // Bottom tier (y = -0.78, x = +5.90)
        RoomConsoleStation("ngp", 5.90f, -0.78f, -1.10f, -90f, 0.88f, 0.24f, 1.75f, 0.42f, 16f),
        RoomConsoleStation("ngc", 5.90f, -0.78f, 0.35f, -90f, 0.88f, 0.24f, 1.75f, 0.42f, 16f),
        RoomConsoleStation("ws", 5.90f, -0.78f, 1.80f, -90f, 0.88f, 0.24f, 1.75f, 0.42f, 16f),
        RoomConsoleStation("wsc", 5.90f, -0.78f, 3.25f, -90f, 0.88f, 0.24f, 1.75f, 0.42f, 16f),

        // WEST WALL — 90s RETRO COMPUTER DESK + MOONLIT WINDOW (x = -5.55, facing East rotY = +90°)
        RoomConsoleStation("dos", -5.55f, -0.36f, -0.35f, 90f, 0.92f, 0.48f, 2.35f, 0.82f, -16f),
    )

private fun canonicalRoomKey(key: String): String =
    when (normalizeKey(key)) {
        "fbneo", "mame2003plus", "arcade" -> "arcade"
        "genesis", "md" -> "md"
        "scd", "segacd" -> "segacd"
        else -> normalizeKey(key)
    }

private class ConsoleRoom3DRenderer(
    private val appContext: Context,
    initialConsoleKeys: List<String>,
    initialSelectedIndex: Int,
) : GLSurfaceView.Renderer {
    @Volatile private var spinning: Boolean = true
    fun setSpinning(value: Boolean) { spinning = value }
    private val stateLock = Any()
    private var consoleKeys: List<String> = initialConsoleKeys.map { canonicalRoomKey(it) }
    private var targetSelectedIndex: Int =
        if (consoleKeys.isEmpty()) 0 else initialSelectedIndex.coerceIn(0, consoleKeys.lastIndex)

    private val program = GlProgramHelper(appContext)
    private val meshCache = HashMap<String, List<MeshPart>>()
    private var roomParts: List<MeshPart> = emptyList()
    private var pedestalPlateParts: List<MeshPart> = emptyList()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var viewportAspect: Float = 16f / 9f
    private var lastFrameNanos: Long = 0L

    private var cameraInitialized: Boolean = false
    private var camX: Float = 0f
    private var camY: Float = 0.65f
    private var camZ: Float = 0.4f
    private var lookX: Float = 0f
    private var lookY: Float = 0.05f
    private var lookZ: Float = -3.8f
    private var swayTimeSec: Float = 0f

    fun updateState(
        newConsoleKeys: List<String>,
        newSelectedIndex: Int,
    ) {
        val normalized = newConsoleKeys.map { canonicalRoomKey(it) }
        val clamped = if (normalized.isEmpty()) 0 else newSelectedIndex.coerceIn(0, normalized.lastIndex)
        synchronized(stateLock) {
            consoleKeys = normalized
            targetSelectedIndex = clamped
        }
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        GLES20.glClearColor(0.05f, 0.045f, 0.075f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        program.init()
        Matrix.setIdentityM(identityMatrix, 0)
        pedestalPlateParts = buildGlowPedestalPlateMesh()
        roomParts = buildEnclosedRetroBedroomMesh()
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
        val fov = if (viewportAspect < 1.0f) 52f else 42f
        Matrix.perspectiveM(projectionMatrix, 0, fov, viewportAspect, 0.25f, 40.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val keys: List<String>
        val selIdx: Int
        synchronized(stateLock) {
            keys = consoleKeys
            selIdx = targetSelectedIndex
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0f, 0.05f)
        lastFrameNanos = now
        if (spinning) swayTimeSec += dt

        val activeKey = keys.getOrNull(selIdx) ?: "snes"
        val targetStation =
            ALL_ROOM_STATIONS.firstOrNull { it.key == activeKey }
                ?: ALL_ROOM_STATIONS.first()

        // Compute ideal elevated 3/4 hero camera directly in front of the console's +Z front face!
        val viewAngleRad = ((targetStation.rotY + targetStation.camSideAngleDeg) * PI / 180.0)
        val portraitExtra = if (viewportAspect < 1.0f) 0.55f else 0f
        val dist = targetStation.camDistance + portraitExtra
        val desiredCamX = (targetStation.x + sin(viewAngleRad).toFloat() * dist).coerceIn(-5.0f, 5.0f)
        val desiredCamY = (targetStation.y + targetStation.camElevation).coerceIn(-0.4f, 2.8f)
        val desiredCamZ = (targetStation.z + cos(viewAngleRad).toFloat() * dist).coerceIn(-4.6f, 4.6f)
        val desiredLookY = targetStation.y + targetStation.lookHeightOffset

        if (!cameraInitialized) {
            camX = desiredCamX
            camY = desiredCamY
            camZ = desiredCamZ
            lookX = targetStation.x
            lookY = desiredLookY
            lookZ = targetStation.z
            cameraInitialized = true
        } else {
            val lerpFactor = 1f - exp(-dt * 5.2f)
            camX += (desiredCamX - camX) * lerpFactor
            camY += (desiredCamY - camY) * lerpFactor
            camZ += (desiredCamZ - camZ) * lerpFactor
            lookX += (targetStation.x - lookX) * lerpFactor
            lookY += (desiredLookY - lookY) * lerpFactor
            lookZ += (targetStation.z - lookZ) * lerpFactor
        }

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        program.beginFrame(camX, camY, camZ)

        // 1. Draw the full 360° enclosed 80s/90s gamer bedroom with 100% OPEN hollow shelves
        program.drawParts(roomParts, identityMatrix, vpMatrix)

        // 2. Draw glowing highlight disc flush on the shelf/floor right under the selected console
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, targetStation.x, targetStation.y + 0.008f, targetStation.z)
        val plateScale = if (targetStation.key == "arcade") 1.05f else 0.65f
        Matrix.scaleM(modelMatrix, 0, plateScale, 1f, plateScale)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        program.drawParts(pedestalPlateParts, modelMatrix, mvpMatrix)

        // 3. Draw EVERY Lemuroid console from Blender .l3d assets, resting flush on its shelf/floor!
        for (station in ALL_ROOM_STATIONS) {
            if (station.key != activeKey) {
                val sx = station.x - camX; val sz = station.z - camZ
                val fx = lookX - camX; val fz = lookZ - camZ
                if (sx * fx + sz * fz < -0.5f) continue
            }
            val parts = meshCache.getOrPut(station.key) { loadConsoleMesh(appContext, station.key) }
            val isSelected = station.key == activeKey
            val liftY = if (spinning && isSelected && station.key != "arcade") 0.045f + sin(swayTimeSec * 2.2f) * 0.015f else 0f
            val scale = station.scale * if (isSelected) 1.08f else 1.0f
            val rotY = station.rotY + if (spinning && isSelected && station.key != "arcade") sin(swayTimeSec * 1.4f) * 6f else 0f

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, station.x, station.y + liftY, station.z)
            Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

            program.drawParts(parts, modelMatrix, mvpMatrix)
        }

        program.endFrame()
    }

    private fun buildGlowPedestalPlateMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val softGold = argbToFloat4(0xFFFFCA64.toInt())
        val innerCyan = argbToFloat4(0xFF4BE8FF.toInt())

        val outerRing = MeshBuilder()
        outerRing.addCylinderY(cx = 0f, cy = 0.008f, cz = 0f, radius = 0.70f, height = 0.016f, segments = 28)
        parts += outerRing.toPart(color = softGold, shininess = 80f, specStrength = 0.65f, emissive = 0.65f)

        val innerDisc = MeshBuilder()
        innerDisc.addCylinderY(cx = 0f, cy = 0.020f, cz = 0f, radius = 0.52f, height = 0.014f, segments = 24)
        parts += innerDisc.toPart(color = innerCyan, shininess = 90f, specStrength = 0.70f, emissive = 0.50f)

        return parts
    }

    private fun buildEnclosedRetroBedroomMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val floorWood = argbToFloat4(0xFFD2C1A5.toInt())
        val woodWall90s = argbToFloat4(0xFFB6A48B.toInt()) // Warm 90s wood paneling (like Reference Photo 2!)
        val ceilingDark = argbToFloat4(0xFF1F1A26.toInt())
        val woodLight = argbToFloat4(0xFFCBB79C.toInt())
        val woodDark = argbToFloat4(0xFF94806B.toInt())
        val darkMetal = argbToFloat4(0xFF1F222A.toInt())
        val silver = argbToFloat4(0xFFC4CBD4.toInt())
        val white = argbToFloat4(0xFFFFFFFF.toInt())

        // 1. FLOOR (Tile 1 Oak Planks), CEILING & 4 WARM WOOD-PANELED WALLS (Tile 0 — like Photo 2!)
        val floor = MeshBuilder()
        floor.addBox(0f, -1.50f, 0f, 13.8f, 0.20f, 12.8f)
        parts += floor.toPart(floorWood, shininess = 24f, specStrength = 0.20f, atlasTile = 1, uvScale = 0.45f)

        val ceiling = MeshBuilder()
        ceiling.addBox(0f, 3.70f, 0f, 13.8f, 0.20f, 12.8f)
        parts += ceiling.toPart(ceilingDark, shininess = 10f, specStrength = 0.06f)

        val walls = MeshBuilder()
        walls.addBox(0f, 1.10f, -6.35f, 13.8f, 5.20f, 0.30f) // North Wall
        walls.addBox(0f, 1.10f, 6.35f, 13.8f, 5.20f, 0.30f) // South Wall
        walls.addBox(6.85f, 1.10f, 0f, 0.30f, 5.20f, 12.8f) // East Wall
        walls.addBox(-6.85f, 1.10f, 0f, 0.30f, 5.20f, 12.8f) // West Wall
        parts += walls.toPart(woodWall90s, shininess = 20f, specStrength = 0.14f, atlasTile = 0, uvScale = 0.42f)

        // Cozy Retro Carpet Rug in front of CRT TV (Tile 2 — SNES & PS1 sit on it like Photos 1 & 2!)
        val rug = MeshBuilder()
        rug.addBox(0f, -1.375f, -1.35f, 5.6f, 0.045f, 3.6f)
        parts += rug.toPart(argbToFloat4(0xFFB5C9C3.toInt()), shininess = 10f, specStrength = 0.06f, atlasTile = 2, uvScale = 0.35f)

        // 2. NORTH WALL — SPACIOUS 1-CONSOLE-PER-CUBBY OPEN RACK (x in [-5.35, +3.75], ZERO overlap!)
        val furnitureDark = MeshBuilder()
        val furnitureLight = MeshBuilder()
        furnitureDark.addBox(-0.80f, -0.85f, -5.75f, 9.10f, 1.10f, 0.08f) // Thin back board
        // Thin vertical cubby walls positioned cleanly BETWEEN consoles (-5.30, -3.05, -0.85, +1.35, +3.70)
        for (divX in listOf(-5.30f, -3.05f, -0.85f, 1.35f, 3.70f)) {
            furnitureDark.addBox(divX, -0.85f, -4.90f, 0.08f, 1.10f, 1.66f)
        }
        // Open horizontal wooden shelves: Base (-1.36), Middle cubby floor (top = -0.96), Top slab (top = -0.30)
        furnitureLight.addBox(-0.80f, -1.36f, -4.90f, 9.10f, 0.08f, 1.68f)
        furnitureLight.addBox(-0.80f, -1.00f, -4.90f, 9.00f, 0.08f, 1.62f) // Top surface = -0.96
        furnitureLight.addBox(-0.80f, -0.34f, -4.90f, 9.16f, 0.08f, 1.72f) // Top surface = -0.30

        // 3. EAST WALL — 3-TIER WIDE-SPACED OPEN HANDHELD VITRINE (x = +6.05, z in [-1.85, +4.15])
        furnitureDark.addBox(6.55f, 0.20f, 1.15f, 0.08f, 3.20f, 6.00f) // Thin back board against East wall
        furnitureDark.addBox(6.05f, 0.20f, -1.82f, 1.00f, 3.20f, 0.08f) // North end cheek
        furnitureDark.addBox(6.05f, 0.20f, 4.12f, 1.00f, 3.20f, 0.08f) // South end cheek
        // 3 Open horizontal planks: Top cap (+1.76), Upper (+0.86), Middle (+0.04), Lower (-0.78), Base (-1.36)
        furnitureLight.addBox(6.05f, 1.76f, 1.15f, 1.00f, 0.08f, 6.00f)
        furnitureLight.addBox(6.05f, 0.82f, 1.15f, 0.96f, 0.08f, 5.90f) // Top = +0.86
        furnitureLight.addBox(6.05f, 0.00f, 1.15f, 0.96f, 0.08f, 5.90f) // Top = +0.04
        furnitureLight.addBox(6.05f, -0.82f, 1.15f, 0.96f, 0.08f, 5.90f) // Top = -0.78
        furnitureLight.addBox(6.05f, -1.36f, 1.15f, 1.00f, 0.08f, 6.00f)

        // 4. WEST WALL — 90s COMPUTER DESK (x = -5.65, Tabletop top = -0.36)
        furnitureLight.addBox(-5.65f, -0.40f, -0.25f, 1.60f, 0.08f, 4.40f)
        furnitureDark.addBox(-5.65f, -0.90f, -2.25f, 1.48f, 0.96f, 0.24f)
        furnitureDark.addBox(-5.65f, -0.90f, 1.75f, 1.48f, 0.96f, 0.36f)

        // 5. SOUTH WALL — COZY 90s BED + NIGHTSTAND (closing 360° room cleanly)
        furnitureDark.addBox(-0.6f, -1.00f, 4.80f, 3.4f, 0.56f, 2.4f)
        furnitureLight.addBox(-0.6f, -0.25f, 5.95f, 3.4f, 1.25f, 0.16f)
        furnitureDark.addBox(2.3f, -0.92f, 5.25f, 1.15f, 0.96f, 1.05f)
        furnitureLight.addBox(2.3f, -0.42f, 5.25f, 1.22f, 0.06f, 1.12f)

        parts += furnitureDark.toPart(woodDark, shininess = 22f, specStrength = 0.18f, atlasTile = 0, uvScale = 0.65f)
        parts += furnitureLight.toPart(woodLight, shininess = 34f, specStrength = 0.26f, atlasTile = 1, uvScale = 0.65f)

        // Bed mattress, blue retro quilt & pillows
        val bedQuilt = MeshBuilder()
        bedQuilt.addBox(-0.6f, -0.64f, 4.65f, 3.25f, 0.26f, 2.15f)
        parts += bedQuilt.toPart(argbToFloat4(0xFF2E6291.toInt()), shininess = 12f, specStrength = 0.08f, atlasTile = 2, uvScale = 0.5f)
        val pillows = MeshBuilder()
        pillows.addBox(-1.35f, -0.44f, 5.52f, 1.10f, 0.22f, 0.55f)
        pillows.addBox(0.15f, -0.44f, 5.52f, 1.10f, 0.22f, 0.55f)
        parts += pillows.toPart(argbToFloat4(0xFFECE6D8.toInt()), shininess = 14f, specStrength = 0.08f, atlasTile = 3)

        // Big Classic Sony Trinitron CRT TV centered on North Rack (x = -0.80, z = -5.18, behind consoles!)
        val crtBody = MeshBuilder()
        crtBody.addBox(-0.80f, 0.38f, -5.28f, 1.70f, 1.36f, 0.84f)
        // Recessed 4:3 glass surrounded by a deep, solid bezel.
        crtBody.addBox(-0.80f, 0.97f, -4.82f, 1.62f, 0.10f, 0.08f)
        crtBody.addBox(-0.80f, -0.13f, -4.82f, 1.62f, 0.14f, 0.08f)
        crtBody.addBox(-1.55f, 0.42f, -4.82f, 0.10f, 1.00f, 0.08f)
        crtBody.addBox(-0.05f, 0.42f, -4.82f, 0.10f, 1.00f, 0.08f)
        crtBody.addBox(-0.80f, 0.38f, -5.66f, 1.02f, 0.86f, 0.20f)
        // Boombox on West Desk
        crtBody.addBox(-5.65f, -0.14f, -1.75f, 0.34f, 0.44f, 0.88f)
        parts += crtBody.toPart(darkMetal, shininess = 34f, specStrength = 0.28f, atlasTile = 3)

        // UV Pixel-Art CRT Game Screen (Tile 4!)
        val crtScreen = MeshBuilder()
        crtScreen.addBox(-0.80f, 0.42f, -4.84f, 1.24f, 0.92f, 0.04f)
        parts += crtScreen.toPart(white, shininess = 90f, specStrength = 0.72f, emissive = 0.65f, atlasTile = 4, uvScale = 0.78f)

        // UV Pixel-Art Blue Moonlit Night Window on West Wall (Tile 6 — like Photo 1!)
        val nightWindow = MeshBuilder()
        nightWindow.addBox(-6.62f, 1.55f, -0.20f, 0.08f, 1.85f, 2.25f)
        parts += nightWindow.toPart(white, shininess = 60f, specStrength = 0.45f, emissive = 0.55f, atlasTile = 6, uvScale = 0.46f)

        // UV Pixel-Art 80s/90s Game Posters on North & South Wood-Paneled Walls (Tile 5 — like Photos 1 & 2!)
        val posters = MeshBuilder()
        posters.addBox(-4.2f, 1.55f, -6.14f, 1.25f, 1.60f, 0.04f)
        posters.addBox(-2.5f, 1.65f, -6.14f, 1.15f, 1.45f, 0.04f)
        posters.addBox(1.2f, 1.65f, -6.14f, 1.15f, 1.45f, 0.04f)
        posters.addBox(2.9f, 1.55f, -6.14f, 1.25f, 1.60f, 0.04f)
        posters.addBox(-2.8f, 1.45f, 6.14f, 1.30f, 1.60f, 0.04f)
        posters.addBox(2.2f, 1.45f, 6.14f, 1.30f, 1.60f, 0.04f)
        parts += posters.toPart(white, shininess = 28f, specStrength = 0.22f, emissive = 0.22f, atlasTile = 5, uvScale = 0.64f)

        // Warm Golden Retro Lamps (like Reference Photo 2!) + Ceiling Lamp
        val lampGlow = MeshBuilder()
        lampGlow.addBox(0f, 3.52f, 0f, 1.2f, 0.10f, 1.2f)
        lampGlow.addBox(-5.65f, 0.48f, 1.25f, 0.44f, 0.34f, 0.44f) // West desk warm lamp
        lampGlow.addBox(2.3f, -0.05f, 5.15f, 0.40f, 0.32f, 0.40f) // Bedside warm lamp
        parts += lampGlow.toPart(argbToFloat4(0xFFFFCA68.toInt()), shininess = 60f, specStrength = 0.35f, emissive = 0.88f)

        // Subtle Neon Accent Strip along upper ceiling edge
        val neon = MeshBuilder()
        neon.addBox(0f, 3.05f, -6.14f, 11.6f, 0.06f, 0.06f)
        neon.addBox(6.64f, 3.05f, 0f, 0.06f, 0.06f, 10.6f)
        parts += neon.toPart(argbToFloat4(0xFF00E5FF.toInt()), shininess = 76f, specStrength = 0.55f, emissive = 0.72f)

        // Silver hardware trim (lamp poles, boombox speakers)
        val silverTrim = MeshBuilder()
        silverTrim.addBox(-5.65f, 0.00f, 1.25f, 0.06f, 0.72f, 0.06f)
        silverTrim.addBox(2.3f, -0.24f, 5.15f, 0.06f, 0.36f, 0.06f)
        silverTrim.addBox(-5.46f, -0.14f, -2.00f, 0.03f, 0.24f, 0.24f)
        silverTrim.addBox(-5.46f, -0.14f, -1.50f, 0.03f, 0.24f, 0.24f)
        parts += silverTrim.toPart(silver, shininess = 88f, specStrength = 0.72f, atlasTile = 3)

        return parts
    }
}

/**
 * Genuine OpenGL ES 2.0 3D CRT Tube TV chassis for the Retro Tube TV theme.
 * Renders a sculpted 1980s/90s walnut woodgrain CRT television with deep cathode-ray tube back,
 * recessed screen bezel, 3D speaker grille, glowing power LED, rabbit-ear V-antennas,
 * and physically rotating VHF/UHF tuning dials driven by [channelIndex].
 * Ultra-lightweight (~320 triangles, single 256x256 pixel-art UV atlas).
 */
@Composable
fun RetroCrtTv3DView(
    channelIndex: Int,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val renderer = remember { RetroCrtTv3DRenderer(initialChannelIndex = channelIndex, appContext = appContext) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(channelIndex) {
        renderer.updateChannel(channelIndex)
    }
    Bind3DFrameClock(glSurfaceViewRef, channelIndex)

    Bind3DSurfaceLifecycle(glSurfaceViewRef)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                glSurfaceViewRef = this
            }
        },
        update = { view ->
            if (glSurfaceViewRef !== view) {
                glSurfaceViewRef = view
            }
        },
        onRelease = { view ->
            view.onPause()
            if (glSurfaceViewRef === view) {
                glSurfaceViewRef = null
            }
        },
    )
}

private class RetroCrtTv3DRenderer(
    initialChannelIndex: Int,
    private val appContext: Context? = null,
) : GLSurfaceView.Renderer {
    private val stateLock = Any()
    private var targetChannelIndex: Int = initialChannelIndex
    private var currentDialAngleDeg: Float = (initialChannelIndex * 32f) % 360f
    private var lastFrameNanos: Long = 0L

    private val program = GlProgramHelper(appContext)
    private var staticBodyParts: List<MeshPart> = emptyList()
    private var vhfKnobParts: List<MeshPart> = emptyList()
    private var uhfKnobParts: List<MeshPart> = emptyList()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    fun updateChannel(channelIndex: Int) {
        synchronized(stateLock) {
            targetChannelIndex = channelIndex
        }
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) {
        GLES20.glClearColor(0.095f, 0.058f, 0.038f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program.init()
        staticBodyParts = buildCrtTvStaticBody()
        vhfKnobParts = buildRotaryKnobMesh()
        uhfKnobParts = buildRotaryKnobMesh()
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
        val aspect = safeW.toFloat() / safeH.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 32f, aspect, 0.4f, 30.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val chIdx: Int
        synchronized(stateLock) {
            chIdx = targetChannelIndex
        }

        val now = System.nanoTime()
        if (lastFrameNanos == 0L) lastFrameNanos = now
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        val targetAngle = (chIdx * 32f)
        val smooth = 1f - exp(-dt * 12f)
        currentDialAngleDeg += (targetAngle - currentDialAngleDeg) * smooth

        // Fixed, rock-solid 3/4 front camera so the CRT never drifts or jumps
        val camX = -0.32f
        val camY = 0.24f
        val camZ = 4.65f
        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, 0f, 0.02f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        program.beginFrame(camX, camY, camZ)

        // Draw static 3D CRT TV cabinet + tabletop + rabbit-ear antennas
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        program.drawParts(staticBodyParts, modelMatrix, mvpMatrix)

        // Draw physically rotating 3D VHF dial (upper right control panel)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 1.07f, 0.44f, 0.54f)
        Matrix.rotateM(modelMatrix, 0, currentDialAngleDeg, 0f, 0f, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        program.drawParts(vhfKnobParts, modelMatrix, mvpMatrix)

        // Draw physically rotating 3D UHF dial (below VHF)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 1.07f, 0.05f, 0.54f)
        Matrix.rotateM(modelMatrix, 0, currentDialAngleDeg * 1.45f + 45f, 0f, 0f, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        program.drawParts(uhfKnobParts, modelMatrix, mvpMatrix)

        program.endFrame()
    }

    private fun buildCrtTvStaticBody(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val walnutWood = argbToFloat4(0xFF8C5A34.toInt())
        val darkWood = argbToFloat4(0xFF472A16.toInt())
        val charcoal = argbToFloat4(0xFF23262D.toInt())
        val recessedBlack = argbToFloat4(0xFF0A0C10.toInt())
        val brushedChrome = argbToFloat4(0xFFC5CDD8.toInt())
        val speakerSlit = argbToFloat4(0xFF14161A.toInt())
        val ledRed = argbToFloat4(0xFFFF2C2C.toInt())
        val amberBadge = argbToFloat4(0xFFFFB74D.toInt())

        // 1. Wooden TV stand tabletop underneath (grounded 3D perspective)
        val table = MeshBuilder()
        table.addBox(0f, -1.06f, -0.05f, 3.80f, 0.14f, 2.20f)
        parts += table.toPart(darkWood, shininess = 24f, specStrength = 0.22f, atlasTile = 1, uvScale = 0.65f)

        // 2. Rubber feet supporting the CRT TV
        val feet = MeshBuilder()
        feet.addBox(-1.05f, -0.95f, 0.32f, 0.24f, 0.09f, 0.24f)
        feet.addBox(1.05f, -0.95f, 0.32f, 0.24f, 0.09f, 0.24f)
        feet.addBox(-1.05f, -0.95f, -0.58f, 0.24f, 0.09f, 0.24f)
        feet.addBox(1.05f, -0.95f, -0.58f, 0.24f, 0.09f, 0.24f)
        parts += feet.toPart(recessedBlack, shininess = 18f, specStrength = 0.15f)

        // 3. Main Walnut Woodgrain CRT TV Outer Cabinet Shell (hollowed front)
        val woodCabinet = MeshBuilder()
        woodCabinet.addBox(0f, 0.87f, -0.10f, 2.74f, 0.14f, 1.32f) // Top panel
        woodCabinet.addBox(0f, -0.85f, -0.10f, 2.74f, 0.14f, 1.32f) // Bottom panel
        woodCabinet.addBox(-1.30f, 0.01f, -0.10f, 0.14f, 1.72f, 1.32f) // Left side panel
        woodCabinet.addBox(1.30f, 0.01f, -0.10f, 0.14f, 1.72f, 1.32f) // Right side panel
        woodCabinet.addBox(0f, 0.01f, -0.72f, 2.60f, 1.66f, 0.12f) // Back wood frame
        parts += woodCabinet.toPart(walnutWood, shininess = 32f, specStrength = 0.30f, atlasTile = 0, uvScale = 0.60f)

        // 4. Deep Cathode-Ray Tube Rear Bulb Housing (true 3D CRT silhouette!)
        val rearTube = MeshBuilder()
        rearTube.addBox(0f, 0.02f, -1.04f, 1.92f, 1.24f, 0.56f)
        rearTube.addBox(0f, 0.02f, -1.42f, 1.05f, 0.72f, 0.34f)
        parts += rearTube.toPart(charcoal, shininess = 22f, specStrength = 0.20f, atlasTile = 3)

        // 5. Front Metallic Recessed Bezel Frame around the CRT screen
        val bezel = MeshBuilder()
        bezel.addBox(-0.24f, 0.74f, 0.48f, 1.98f, 0.14f, 0.18f) // Top bezel
        bezel.addBox(-0.24f, -0.72f, 0.48f, 1.98f, 0.14f, 0.18f) // Bottom bezel
        bezel.addBox(-1.17f, 0.01f, 0.48f, 0.14f, 1.48f, 0.18f) // Left bezel
        bezel.addBox(0.70f, 0.01f, 0.48f, 0.16f, 1.48f, 0.18f) // Divider between screen and dials
        // Right control panel backing
        bezel.addBox(1.03f, 0.01f, 0.44f, 0.50f, 1.54f, 0.18f)
        parts += bezel.toPart(charcoal, shininess = 46f, specStrength = 0.40f, atlasTile = 3)

        // 6. Deep dark CRT Screen Backing (recessed behind front bezel)
        val screenBack = MeshBuilder()
        screenBack.addBox(-0.24f, 0.01f, 0.34f, 1.84f, 1.38f, 0.06f)
        parts += screenBack.toPart(recessedBlack, shininess = 80f, specStrength = 0.55f)

        // 7. Speaker Grille horizontal louvers (bottom right control panel)
        val grille = MeshBuilder()
        for (i in 0 until 6) {
            val gy = -0.26f - i * 0.08f
            grille.addBox(1.03f, gy, 0.54f, 0.38f, 0.032f, 0.04f)
        }
        parts += grille.toPart(speakerSlit, shininess = 18f, specStrength = 0.12f)

        // 8. Brushed Chrome Trim & Rabbit-Ear V-Antennas on top of CRT!
        val chrome = MeshBuilder()
        chrome.addBox(0f, 0.96f, 0.10f, 0.42f, 0.07f, 0.24f) // Antenna swivel base
        // Left tilted V-antenna rod (built from stepped 3D segments for clean silhouette)
        for (s in 0 until 8) {
            val t = s / 7f
            chrome.addBox(-0.10f - t * 0.72f, 1.00f + t * 0.58f, 0.10f - t * 0.18f, 0.05f, 0.05f, 0.05f)
            chrome.addBox(0.10f + t * 0.72f, 1.00f + t * 0.58f, 0.10f - t * 0.18f, 0.05f, 0.05f, 0.05f)
        }
        // Subtle chrome trim border around front bezel
        chrome.addBox(0f, 0.80f, 0.56f, 2.52f, 0.025f, 0.025f)
        chrome.addBox(0f, -0.78f, 0.56f, 2.52f, 0.025f, 0.025f)
        parts += chrome.toPart(brushedChrome, shininess = 92f, specStrength = 0.80f)

        // 9. Glowing Red Power LED + Amber Brand Badge
        val led = MeshBuilder()
        led.addBox(1.14f, -0.68f, 0.55f, 0.06f, 0.06f, 0.04f)
        parts += led.toPart(ledRed, shininess = 60f, specStrength = 0.45f, emissive = 0.95f)

        val badge = MeshBuilder()
        badge.addBox(-0.24f, -0.73f, 0.57f, 0.34f, 0.055f, 0.02f)
        parts += badge.toPart(amberBadge, shininess = 70f, specStrength = 0.65f, emissive = 0.28f)

        return parts
    }

    private fun buildRotaryKnobMesh(): List<MeshPart> {
        val parts = mutableListOf<MeshPart>()
        val knobBody = MeshBuilder()
        knobBody.addBox(0f, 0f, 0.04f, 0.22f, 0.22f, 0.10f)
        parts += knobBody.toPart(argbToFloat4(0xFF3A404B.toInt()), shininess = 72f, specStrength = 0.65f)

        val pointerBar = MeshBuilder()
        pointerBar.addBox(0f, 0.04f, 0.10f, 0.045f, 0.16f, 0.04f)
        parts += pointerBar.toPart(argbToFloat4(0xFFFFD56B.toInt()), shininess = 80f, specStrength = 0.70f, emissive = 0.25f)
        return parts
    }
}
