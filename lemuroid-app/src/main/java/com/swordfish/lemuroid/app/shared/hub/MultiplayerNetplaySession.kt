package com.swordfish.lemuroid.app.shared.hub

import android.util.Base64
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.Inflater

data class MultiplayerSessionConfig(
    val isHost: Boolean,
    val hostIp: String = "",
    val port: Int = DEFAULT_GAME_PORT,
    val mitmIp: String = "",
    val mitmPort: Int = DEFAULT_GAME_PORT,
    val mitmSession: String = "",
    val hostMethod: Int = 0,
    val hostFrontend: String = "",
    val mitmHandle: String = "saopaulo",
    val relayHost: String = "southamerica-east1.relay.retroarch.com",
    val relayPort: Int = DEFAULT_GAME_PORT,
    val hostDisplayName: String = "VideoGame",
    val gameTitle: String = "",
    val romFileName: String = "",
    val gameCrcHex: String = "",
    val consoleId: String = "",
    val coreName: String = "",
    val coreVersion: String = "1.0",
) : Serializable {
    companion object {
        const val DEFAULT_GAME_PORT = 55435
        const val LAN_DISCOVERY_PORT = 55436
    }
}

/**
 * Audited RetroArch Netplay Protocol (v5-v8) + Official MITM Relay (`RATS`/`RATL`/`RATP`)
 * + Public Lobby (`lobby.libretro.com/add`) engine for VideoGame.
 */
class MultiplayerNetplaySession(
    private val config: MultiplayerSessionConfig,
    private val onStatusNotice: (String) -> Unit,
    private val onRemoteKeyEvent: (action: Int, keyCode: Int, port: Int) -> Unit,
    private val onRemoteMotionEvent: (source: Int, x: Float, y: Float, port: Int) -> Unit,
    private val onRequestSerializeState: () -> ByteArray?,
    private val onApplyRemoteState: (ByteArray) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    @Volatile
    private var activeSocket: Socket? = null

    @Volatile
    private var outputStream: DataOutputStream? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var relayControlSocket: Socket? = null

    @Volatile
    private var lanBeaconSocket: DatagramSocket? = null

    @Volatile
    var isPeerConnected: Boolean = false
        private set

    @Volatile
    var localPortIndex: Int = if (config.isHost) 0 else 1
        private set

    @Volatile
    private var myClientNum: Int = if (config.isHost) 0 else 1

    @Volatile
    private var peerUsesZlib: Boolean = true

    @Volatile
    private var negotiatedProtocol: Int = HIGH_PROTOCOL_VERSION

    @Volatile
    private var clientInputWords: Int = 1

    @Volatile
    private var clientIsPlaying: Boolean = false

    @Volatile
    private var isRemotePaused: Boolean = false

    val isPlayingLocally: Boolean
        get() = config.isHost || clientIsPlaying

    private val hostFrame = AtomicInteger(0)
    private val clientSendFrame = AtomicInteger(0)
    private val localJoypadMask = AtomicInteger(0)
    private val remoteJoypadMasks = IntArray(4)

    fun start() {
        if (config.isHost) {
            startPublicAndLanHost()
        } else {
            startRetroArchClient()
        }
    }

    // =========================================================================
    // HOST MODE (Public RetroArch MITM Relay + Lobby Announce + LAN)
    // =========================================================================

    private fun startPublicAndLanHost() {
        // 1. Local TCP server + LAN UDP broadcast responder
        scope.launch {
            runCatching {
                val server = runCatching { ServerSocket(config.port) }.getOrElse { ServerSocket(0) }
                serverSocket = server
                startLanDiscoveryResponder(server.localPort)
                while (!closed.get() && scope.isActive) {
                    val client =
                        try {
                            server.accept()
                        } catch (_: Exception) {
                            break
                        }
                    if (closed.get()) {
                        runCatching { client.close() }
                        break
                    }
                    scope.launch { runHostPeerSession(client) }
                }
            }
        }

        // 2. Public RetroArch MITM Relay + lobby.libretro.com/add announcement
        if (config.relayHost.isNotBlank()) {
            scope.launch {
                runPublicMitmRelayHost()
            }
        } else {
            val localIp = resolveLocalIpv4()
            onStatusNotice(
                if (localIp != null) {
                    "Sala LAN criada em $localIp:${config.port}! Aguardando Jogador 2..."
                } else {
                    "Sala LAN criada! Aguardando Jogador 2..."
                },
            )
        }
    }

    private suspend fun runPublicMitmRelayHost() {
        var announceJob: Job? = null
        try {
            onStatusNotice("Conectando ao servidor público (${config.mitmHandle})...")
            val ctrl = Socket()
            ctrl.tcpNoDelay = true
            ctrl.connect(InetSocketAddress(config.relayHost, config.relayPort), 6000)
            relayControlSocket = ctrl
            val input = DataInputStream(BufferedInputStream(ctrl.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(ctrl.getOutputStream()))

            // Request new MITM session: [MITM_SESSION_MAGIC][12 zero bytes]
            output.writeInt(MITM_SESSION_MAGIC)
            output.write(ByteArray(12))
            output.flush()

            ctrl.soTimeout = 6000
            val respMagic = input.readInt()
            val sessionUnique = ByteArray(12)
            input.readFully(sessionUnique)
            if (respMagic != MITM_SESSION_MAGIC) {
                onStatusNotice("Servidor público recusou sessão. Sala ativa na Rede Local.")
                return
            }
            ctrl.soTimeout = 0

            val mitmSessionB64 = Base64.encodeToString(sessionUnique, Base64.NO_WRAP)
            Timber.i("MultiplayerNetplay: Public MITM session granted: %s", mitmSessionB64)
            onStatusNotice("Sala pública online! Aguardando jogadores no lobby...")

            // Announce room to http://lobby.libretro.com/add every 30s
            announceJob =
                scope.launch {
                    while (!closed.get() && scope.isActive) {
                        announceToPublicLobby(mitmSessionB64)
                        delay(30_000L)
                    }
                }

            // Listen for incoming MITM control packets:
            // - MITM_PING_MAGIC (RATP): 4 bytes total (NO 12-byte payload!)
            // - MITM_LINK_MAGIC (RATL): 4 bytes magic + 12 bytes linkId
            // - MITM_ADDR_MAGIC (RATA): 4 bytes magic + 12 bytes + 16 bytes addr
            val linkPayload = ByteArray(12)
            val addrSkip = ByteArray(28)
            while (!closed.get() && scope.isActive) {
                val magic = input.readInt()
                when (magic) {
                    MITM_PING_MAGIC -> {
                        synchronized(ctrl) {
                            output.writeInt(MITM_PING_MAGIC)
                            output.flush()
                        }
                    }
                    MITM_LINK_MAGIC -> {
                        input.readFully(linkPayload)
                        val linkId = linkPayload.copyOf()
                        scope.launch {
                            runCatching {
                                val dataSock = Socket()
                                dataSock.tcpNoDelay = true
                                dataSock.connect(InetSocketAddress(config.relayHost, config.relayPort), 6000)
                                val dOut = DataOutputStream(BufferedOutputStream(dataSock.getOutputStream()))
                                dOut.writeInt(MITM_LINK_MAGIC)
                                dOut.write(linkId)
                                dOut.flush()
                                runHostPeerSession(dataSock)
                            }
                        }
                    }
                    MITM_ADDR_MAGIC -> {
                        input.readFully(addrSkip)
                    }
                    else -> {
                        Timber.w("MultiplayerNetplay: Unknown MITM control magic 0x%08X", magic)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Public MITM relay host ended")
            if (!closed.get()) {
                onStatusNotice("Relay público indisponível. Sala continua ativa na Rede Local.")
            }
        } finally {
            announceJob?.cancel()
            runCatching { relayControlSocket?.close() }
            relayControlSocket = null
        }
    }

    private fun announceToPublicLobby(mitmSessionB64: String) {
        runCatching {
            val crcInt = parseCrc32(config.gameCrcHex)
            val coreName = normalizeRetroArchCoreName(config.coreName, config.consoleId)
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            val form =
                buildString {
                    append("username=").append(enc(config.hostDisplayName.take(31)))
                    append("&core_name=").append(enc(coreName))
                    append("&core_version=").append(enc(config.coreVersion.ifBlank { "1.0" }))
                    append("&game_name=").append(enc(config.gameTitle.ifBlank { "Game" }))
                    append("&game_crc=").append(String.format(Locale.US, "%08X", crcInt))
                    append("&port=").append(config.port)
                    append("&mitm_server=").append(enc(config.mitmHandle.ifBlank { "saopaulo" }))
                    append("&has_password=0")
                    append("&has_spectate_password=0")
                    append("&force_mitm=1")
                    append("&retroarch_version=1.19.1")
                    append("&frontend=android")
                    append("&subsystem_name=N%2FA")
                    append("&mitm_session=").append(enc(mitmSessionB64))
                    append("&mitm_custom_addr=")
                    append("&mitm_custom_port=0")
                    append("&player_count=").append(if (isPeerConnected) 2 else 1)
                    append("&spectator_count=0")
                }
            val conn =
                (URL("http://lobby.libretro.com/add").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "RetroArch/1.19.1")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
            try {
                conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                Timber.i("MultiplayerNetplay: Lobby announce HTTP %d", code)
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun runHostPeerSession(socket: Socket) {
        var inputPumpJob: Job? = null
        var resyncJob: Job? = null
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 7000
            activeSocket = socket
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            outputStream = output

            // 1. Read client's 24-byte RANP pre-header
            val cHeader = IntArray(6) { input.readInt() }
            if (cHeader[0] != NETPLAY_MAGIC) return
            peerUsesZlib = (cHeader[2] and NETPLAY_COMPRESSION_ZLIB) != 0
            val clientMaxProto = if (cHeader[3] != 0) cHeader[3] else cHeader[4]
            val proto = clientMaxProto.coerceIn(LOW_PROTOCOL_VERSION, HIGH_PROTOCOL_VERSION)
            negotiatedProtocol = proto

            // Reply with host's 24-byte RANP pre-header
            writePacket(output) {
                writeInt(NETPLAY_MAGIC)
                writeInt(PLATFORM_MAGIC_32)
                writeInt(NETPLAY_COMPRESSION_ZLIB)
                writeInt(0) // no password
                writeInt(proto)
                writeInt(IMPL_MAGIC)
            }

            // 2. Read client NICK, reply with host NICK
            val nickCmd = input.readInt()
            val nickSize = input.readInt()
            if (nickCmd != CMD_NICK || nickSize != NICK_LEN) return
            val clientNickBytes = ByteArray(NICK_LEN)
            input.readFully(clientNickBytes)
            val clientNick = decodeCString(clientNickBytes).ifBlank { "Jogador 2" }

            writePacket(output) {
                writeInt(CMD_NICK)
                writeInt(NICK_LEN)
                write(encodeCString(config.hostDisplayName, NICK_LEN))
            }

            // 3. Send host INFO, read client INFO
            val crcInt = parseCrc32(config.gameCrcHex)
            val coreName = normalizeRetroArchCoreName(config.coreName, config.consoleId)
            val coreVer = config.coreVersion.ifBlank { "1.0" }
            writePacket(output) {
                writeInt(CMD_INFO)
                writeInt(4 + NICK_LEN + NICK_LEN)
                writeInt(crcInt)
                write(encodeCString(coreName, NICK_LEN))
                write(encodeCString(coreVer, NICK_LEN))
            }

            val infoCmd = input.readInt()
            val infoSize = input.readInt()
            if (infoCmd != CMD_INFO || infoSize < 4 + NICK_LEN + NICK_LEN) return
            val infoPayload = ByteArray(infoSize)
            input.readFully(infoPayload)

            // 4. Send SYNC (assign client_num = 1, device 0 = host, device 1 = open for client)
            val syncPayloadSize = 8 + (MAX_INPUT_DEVICES * 4) + MAX_INPUT_DEVICES + (MAX_INPUT_DEVICES * 4) + NICK_LEN
            val configDevices = IntArray(MAX_INPUT_DEVICES) { if (it < 2) RETRO_DEVICE_JOYPAD else 0 }
            writePacket(output) {
                writeInt(CMD_SYNC)
                writeInt(syncPayloadSize)
                writeInt(hostFrame.get())
                writeInt(1) // client_num = 1
                for (i in 0 until MAX_INPUT_DEVICES) writeInt(configDevices[i])
                write(ByteArray(MAX_INPUT_DEVICES))
                // Only host (client 0) starts playing on port 0; client starts spectating until CMD_PLAY.
                for (i in 0 until MAX_INPUT_DEVICES) {
                    writeInt(if (i == 0) (1 shl 0) else 0)
                }
                write(encodeCString(clientNick, NICK_LEN))
            }

            socket.soTimeout = 0
            localPortIndex = 0
            myClientNum = 0
            clientInputWords = 1
            isPeerConnected = true
            onStatusNotice("$clientNick entrou na sala!")

            // Send initial savestate snapshot right away
            delay(250L)
            sendSavestatePacket(output)

            // Playing host sends CMD_INPUT (client_num = 0) at ~60Hz, NOT CMD_NOINPUT
            inputPumpJob =
                scope.launch {
                    while (!closed.get() && isPeerConnected && scope.isActive) {
                        delay(17L)
                        val f = hostFrame.incrementAndGet()
                        val mask = localJoypadMask.get()
                        writePacket(output) {
                            writeInt(CMD_INPUT)
                            writeInt(12)
                            writeInt(f)
                            writeInt(0) // host client_num = 0
                            writeInt(mask)
                        }
                    }
                }
            resyncJob =
                scope.launch {
                    while (!closed.get() && isPeerConnected && scope.isActive) {
                        delay(7000L)
                        sendSavestatePacket(output)
                    }
                }

            readCommandLoop(input, output, isHostSide = true, remoteNick = clientNick, configDevices = configDevices)
        } catch (e: Exception) {
            Timber.w(e, "Host peer session ended")
            if (!closed.get() && isPeerConnected) {
                onStatusNotice("Jogador 2 desconectou. Aguardando outro jogador...")
            }
        } finally {
            inputPumpJob?.cancel()
            resyncJob?.cancel()
            isPeerConnected = false
            outputStream = null
            releaseAllRemoteKeys()
            runCatching { socket.close() }
            if (activeSocket === socket) activeSocket = null
        }
    }

    // =========================================================================
    // CLIENT MODE (Public RetroArch MITM Relay or Direct TCP)
    // =========================================================================

    private fun startRetroArchClient() {
        scope.launch {
            val useMitm =
                (config.hostMethod == 3 || config.mitmSession.isNotBlank()) &&
                    config.mitmIp.isNotBlank() &&
                    config.mitmPort > 0
            val targetHost = if (useMitm) config.mitmIp else config.hostIp
            val targetPort = if (useMitm) config.mitmPort else config.port

            if (targetHost.isBlank()) {
                onStatusNotice("Endereço da sala inválido. Jogando localmente.")
                return@launch
            }

            onStatusNotice(
                if (useMitm) {
                    "Conectando à sala pública via relay..."
                } else {
                    "Conectando à sala $targetHost:$targetPort..."
                },
            )

            val initialPlatform = inferPlatformMagicFromFrontend(config.hostFrontend)
            val retryPlatform = runClientAttempt(targetHost, targetPort, useMitm, initialPlatform, allowRetry = true)
            if (retryPlatform != null && !closed.get()) {
                Timber.i("MultiplayerNetplay: Retrying with host platform magic 0x%08X", retryPlatform)
                runClientAttempt(targetHost, targetPort, useMitm, retryPlatform, allowRetry = false)
            }
        }
    }

    /**
     * Runs one RetroArch client connection attempt.
     * Returns a non-null host `platformMagic` if the host closed right after `RANP` due to
     * platform magic mismatch (same endianness) and a single retry should be attempted.
     */
    private suspend fun runClientAttempt(
        targetHost: String,
        targetPort: Int,
        useMitm: Boolean,
        platformMagic: Int,
        allowRetry: Boolean,
    ): Int? {
        var inputPumpJob: Job? = null
        var hostPlatformMagic = 0
        var handshakeStage = "connect"
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(targetHost, targetPort), 6000)
            socket.soTimeout = 7000
            activeSocket = socket
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            outputStream = output

            // 0. If MITM relay room, send 16-byte RATS session header first
            if (useMitm) {
                handshakeStage = "mitm"
                val sessionRaw = decodeMitmSession(config.mitmSession)
                if (sessionRaw == null || sessionRaw.size != 12) {
                    onStatusNotice("Sessão MITM inválida. Jogando localmente.")
                    return null
                }
                output.writeInt(MITM_SESSION_MAGIC)
                output.write(sessionRaw)
                output.flush()
            }

            // 1. Send client's 24-byte RANP pre-header
            handshakeStage = "preheader"
            writePacket(output) {
                writeInt(NETPLAY_MAGIC)
                writeInt(platformMagic)
                writeInt(NETPLAY_COMPRESSION_ZLIB)
                writeInt(HIGH_PROTOCOL_VERSION)
                writeInt(LOW_PROTOCOL_VERSION)
                writeInt(IMPL_MAGIC)
            }

            // Read host's 24-byte pre-header
            val sHeader = IntArray(6) { input.readInt() }
            when (sHeader[0]) {
                FULL_MAGIC -> {
                    onStatusNotice("A sala está cheia! Jogando localmente.")
                    return null
                }
                BANNED_MAGIC -> {
                    onStatusNotice("Acesso recusado pela sala. Jogando localmente.")
                    return null
                }
                NETPLAY_MAGIC -> Unit
                else -> {
                    onStatusNotice("Resposta inesperada do servidor. Jogando localmente.")
                    return null
                }
            }
            hostPlatformMagic = sHeader[1]
            if (sHeader[3] != 0) {
                onStatusNotice("Esta sala exige senha. Escolha uma sala aberta.")
                return null
            }
            val hostProto = sHeader[4]
            if (hostProto < LOW_PROTOCOL_VERSION || hostProto > HIGH_PROTOCOL_VERSION) {
                onStatusNotice("Protocolo da sala incompatível (v$hostProto). Jogando localmente.")
                return null
            }
            negotiatedProtocol = hostProto
            peerUsesZlib = (sHeader[2] and NETPLAY_COMPRESSION_ZLIB) != 0

            // 2. Send client NICK, read host NICK
            handshakeStage = "nick"
            writePacket(output) {
                writeInt(CMD_NICK)
                writeInt(NICK_LEN)
                write(encodeCString(config.hostDisplayName, NICK_LEN))
            }
            val hNickCmd = input.readInt()
            val hNickSize = input.readInt()
            if (hNickCmd != CMD_NICK || hNickSize != NICK_LEN) {
                onStatusNotice("Falha no handshake de apelido. Jogando localmente.")
                return null
            }
            val hostNickBytes = ByteArray(NICK_LEN)
            input.readFully(hostNickBytes)
            val hostNick = decodeCString(hostNickBytes).ifBlank { "Host" }

            // 3. Read host INFO, echo compatible INFO back
            handshakeStage = "info"
            val hInfoCmd = input.readInt()
            val hInfoSize = input.readInt()
            if (hInfoCmd != CMD_INFO || hInfoSize < 4 + NICK_LEN + NICK_LEN) {
                onStatusNotice("Falha ao receber dados da sala. Jogando localmente.")
                return null
            }
            val hostInfoPayload = ByteArray(hInfoSize)
            input.readFully(hostInfoPayload)

            writePacket(output) {
                writeInt(CMD_INFO)
                writeInt(hostInfoPayload.size)
                write(hostInfoPayload)
            }

            // 4. Read host SYNC (parse frame, client_num, paused flag, and config_devices[16])
            handshakeStage = "sync"
            val syncCmd = input.readInt()
            val syncSize = input.readInt()
            if (syncCmd != CMD_SYNC || syncSize < 8) {
                onStatusNotice("Sala recusou sincronização (núcleo ou ROM diferente).")
                return null
            }
            val syncPayload = ByteArray(syncSize)
            input.readFully(syncPayload)
            val syncDis = DataInputStream(syncPayload.inputStream())
            val startFrame = syncDis.readInt()
            val rawClientNum = syncDis.readInt()
            isRemotePaused = (rawClientNum and SYNC_BIT_PAUSED) != 0
            val assignedClientNum = rawClientNum and 0x7FFFFFFF
            val configDevices = IntArray(MAX_INPUT_DEVICES)
            if (syncSize >= 8 + MAX_INPUT_DEVICES * 4) {
                for (i in 0 until MAX_INPUT_DEVICES) {
                    configDevices[i] = syncDis.readInt()
                }
            }
            hostFrame.set(startFrame)
            clientSendFrame.set(startFrame + 1)
            myClientNum = assignedClientNum

            // 5. Request to play on first available open player slot (0 = auto-assign)
            handshakeStage = "play"
            writePacket(output) {
                writeInt(CMD_PLAY)
                writeInt(4)
                writeInt(0)
            }

            socket.soTimeout = 0
            localPortIndex = 1
            clientInputWords = inputWordsForDevice(configDevices.getOrElse(1) { RETRO_DEVICE_JOYPAD })
            clientIsPlaying = false
            isPeerConnected = true
            Timber.i(
                "MultiplayerNetplay: Connected to %s (proto=%d, frame=%d, clientNum=%d)",
                hostNick,
                hostProto,
                startFrame,
                assignedClientNum,
            )
            onStatusNotice("Conectado à sala de $hostNick! Sincronizando partida...")

            // Strictly sequential CMD_INPUT pump at ~60Hz ONLY while playing and unpaused.
            // Never skips frame numbers; never sends CMD_NOINPUT (rejected by RetroArch servers).
            inputPumpJob =
                scope.launch {
                    while (!closed.get() && isPeerConnected && scope.isActive) {
                        delay(17L)
                        if (clientIsPlaying && !isRemotePaused) {
                            // Keep clientSendFrame paced close to hostFrame (+2 lead max)
                            val targetMax = hostFrame.get() + 2
                            if (clientSendFrame.get() <= targetMax) {
                                sendSequentialClientInput(output)
                            }
                        }
                    }
                }

            readCommandLoop(input, output, isHostSide = false, remoteNick = hostNick, configDevices = configDevices)
            return null
        } catch (e: EOFException) {
            Timber.w(e, "RetroArch client EOF at stage=%s (hostPlatform=0x%08X)", handshakeStage, hostPlatformMagic)
            val sameEndian = ((hostPlatformMagic xor platformMagic) and (1 shl 30)) == 0
            if (allowRetry && handshakeStage == "nick" && hostPlatformMagic != 0 && hostPlatformMagic != platformMagic && sameEndian) {
                return hostPlatformMagic
            }
            if (!closed.get()) {
                onStatusNotice(
                    if (isPeerConnected) {
                        "Conexão com a sala encerrada. Continuando localmente."
                    } else {
                        "Sala encerrou a conexão ($handshakeStage). Jogando localmente."
                    },
                )
            }
            return null
        } catch (e: Exception) {
            Timber.w(e, "RetroArch client connection failed at stage=%s", handshakeStage)
            if (!closed.get()) {
                val reason = e.message?.take(44).orEmpty()
                onStatusNotice(
                    if (isPeerConnected) {
                        "Conexão com a sala encerrada. Continuando localmente."
                    } else if (reason.isNotBlank()) {
                        "Falha ao conectar ($reason). Jogando localmente."
                    } else {
                        "Não foi possível conectar à sala ($handshakeStage). Jogando localmente."
                    },
                )
            }
            return null
        } finally {
            inputPumpJob?.cancel()
            clientIsPlaying = false
            isRemotePaused = false
            isPeerConnected = false
            outputStream = null
            releaseAllRemoteKeys()
            runCatching { socket.close() }
            if (activeSocket === socket) activeSocket = null
        }
    }

    // =========================================================================
    // SHARED RETROARCH NETPLAY COMMAND LOOP
    // =========================================================================

    private fun readCommandLoop(
        input: DataInputStream,
        output: DataOutputStream,
        isHostSide: Boolean,
        remoteNick: String,
        configDevices: IntArray,
    ) {
        while (!closed.get() && scope.isActive) {
            val cmd = input.readInt()
            val size = input.readInt()
            if (size < 0 || size > MAX_PACKET_BYTES) break
            val payload = if (size > 0) ByteArray(size).also { input.readFully(it) } else ByteArray(0)

            when (cmd) {
                CMD_NAK -> {
                    Timber.w("MultiplayerNetplay: Received CMD_NAK from peer")
                    break
                }
                CMD_DISCONNECT -> {
                    Timber.i("MultiplayerNetplay: Received CMD_DISCONNECT from peer")
                    break
                }
                CMD_PING_REQUEST -> {
                    writePacket(output) {
                        writeInt(CMD_PING_RESPONSE)
                        writeInt(payload.size)
                        if (payload.isNotEmpty()) write(payload)
                    }
                }
                CMD_PAUSE -> {
                    isRemotePaused = true
                }
                CMD_RESUME -> {
                    isRemotePaused = false
                }
                CMD_STALL -> {
                    if (payload.size >= 4) {
                        val stallFrames = readBeInt(payload, 0).coerceIn(1, 30)
                        runCatching { Thread.sleep(stallFrames * 16L) }
                    }
                }
                CMD_NOINPUT -> {
                    if (payload.size >= 4) {
                        val f = readBeInt(payload, 0)
                        if (f > hostFrame.get()) hostFrame.set(f)
                    }
                }
                CMD_INPUT -> {
                    if (payload.size >= 12) {
                        val frame = readBeInt(payload, 0)
                        val clientNum = readBeInt(payload, 4)
                        val mask = readBeInt(payload, 8)
                        if (frame > hostFrame.get()) hostFrame.set(frame)
                        val targetPort = if (isHostSide) 1 else if (clientNum == 0) 0 else 1
                        if (targetPort != localPortIndex) {
                            applyRemoteJoypadMask(targetPort, mask)
                        }
                    }
                }
                CMD_PLAY -> {
                    if (isHostSide) {
                        sendModeConfirmation(output, remoteNick, clientDevices = 1 shl 1)
                        sendSavestatePacket(output)
                    }
                }
                CMD_MODE -> {
                    if (!isHostSide && payload.size >= 12) {
                        val modeFrame = readBeInt(payload, 0)
                        val modeBits = readBeInt(payload, 4)
                        val devices = readBeInt(payload, 8)
                        val isYou = (modeBits and MODE_BIT_YOU) != 0
                        val isPlaying = (modeBits and MODE_BIT_PLAYING) != 0
                        if (isYou) {
                            if (isPlaying) {
                                val assignedPort = firstSetBit(devices).coerceIn(0, 3)
                                localPortIndex = assignedPort
                                clientInputWords = sumInputWordsForDevices(devices, configDevices)
                                clientSendFrame.set(modeFrame)
                                if (modeFrame > hostFrame.get()) hostFrame.set(modeFrame)
                                clientIsPlaying = true
                                sendSequentialClientInput(output)
                                Timber.i(
                                    "MultiplayerNetplay: Assigned Player %d (startFrame=%d, words=%d)",
                                    assignedPort + 1,
                                    modeFrame,
                                    clientInputWords,
                                )
                                onStatusNotice("Você entrou como Jogador ${assignedPort + 1}!")
                            } else {
                                clientIsPlaying = false
                                onStatusNotice("Conectado como espectador na sala de $remoteNick.")
                            }
                        }
                    }
                }
                CMD_MODE_REFUSED -> {
                    if (!isHostSide) {
                        clientIsPlaying = false
                        onStatusNotice("Sala sem vagas de jogador — assistindo como espectador.")
                    }
                }
                CMD_REQUEST_SAVESTATE -> {
                    if (isHostSide) {
                        sendSavestatePacket(output)
                    }
                }
                CMD_LOAD_SAVESTATE -> {
                    if (!isHostSide && payload.size > 8) {
                        val frame = readBeInt(payload, 0)
                        val expectedRawSize = readBeInt(payload, 4)
                        if (frame > hostFrame.get()) hostFrame.set(frame)
                        if (frame > clientSendFrame.get()) clientSendFrame.set(frame)
                        val stateData = payload.copyOfRange(8, payload.size)
                        val inflated =
                            if (peerUsesZlib && expectedRawSize > 0 && stateData.size != expectedRawSize) {
                                inflateZlib(stateData, expectedRawSize) ?: stateData
                            } else {
                                stateData
                            }
                        val coreState = unwrapNetplayStateContainer(inflated)
                        if (coreState.isNotEmpty()) {
                            Timber.i(
                                "MultiplayerNetplay: Applying remote savestate (%d bytes, frame=%d)",
                                coreState.size,
                                frame,
                            )
                            onApplyRemoteState(coreState)
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun sendModeConfirmation(
        output: DataOutputStream,
        nick: String,
        clientDevices: Int,
    ) {
        val modeBits = MODE_BIT_YOU or MODE_BIT_PLAYING or 1
        writePacket(output) {
            writeInt(CMD_MODE)
            writeInt(12 + MAX_INPUT_DEVICES + NICK_LEN)
            writeInt(hostFrame.get() + 1)
            writeInt(modeBits)
            writeInt(clientDevices)
            write(ByteArray(MAX_INPUT_DEVICES))
            write(encodeCString(nick, NICK_LEN))
        }
    }

    private fun sendSavestatePacket(output: DataOutputStream) {
        val raw = runCatching { onRequestSerializeState() }.getOrNull() ?: return
        if (raw.isEmpty() || raw.size > MAX_PACKET_BYTES) return
        val wrapped = if (negotiatedProtocol >= 7) wrapNetplayStateContainer(raw) else raw
        val payloadBytes = if (peerUsesZlib) deflateZlib(wrapped) ?: wrapped else wrapped
        writePacket(output) {
            writeInt(CMD_LOAD_SAVESTATE)
            writeInt(8 + payloadBytes.size)
            writeInt(hostFrame.get())
            writeInt(wrapped.size)
            write(payloadBytes)
        }
    }

    // =========================================================================
    // LOCAL INPUT -> RETROPAD BITMASK -> REMOTE PEER
    // =========================================================================

    private fun sendSequentialClientInput(output: DataOutputStream) {
        val frame = clientSendFrame.getAndIncrement()
        val cNum = myClientNum
        val mask = localJoypadMask.get()
        val words = clientInputWords.coerceIn(1, 16)
        writePacket(output) {
            writeInt(CMD_INPUT)
            writeInt(8 + words * 4)
            writeInt(frame)
            writeInt(cNum)
            writeInt(mask)
            for (i in 1 until words) {
                writeInt(0)
            }
        }
    }

    fun isGameplayKey(keyCode: Int): Boolean = androidKeyToRetroPadBit(keyCode) >= 0

    fun sendLocalKey(
        action: Int,
        keyCode: Int,
    ) {
        val bit = androidKeyToRetroPadBit(keyCode)
        if (bit < 0) return
        if (action == KeyEvent.ACTION_DOWN) {
            localJoypadMask.updateAndGet { it or (1 shl bit) }
        } else {
            localJoypadMask.updateAndGet { it and (1 shl bit).inv() }
        }
        // Note: Do NOT send an out-of-order frame here; the 60Hz sequential pump reads localJoypadMask.
    }

    private fun applyRemoteJoypadMask(
        port: Int,
        newMask: Int,
    ) {
        if (port !in remoteJoypadMasks.indices) return
        val prevMask = remoteJoypadMasks[port]
        if (prevMask == newMask) return
        remoteJoypadMasks[port] = newMask
        val changed = prevMask xor newMask
        for (bit in 0 until 16) {
            if ((changed and (1 shl bit)) != 0) {
                val keyCode = retroPadBitToAndroidKey(bit)
                if (keyCode != 0) {
                    val isDown = (newMask and (1 shl bit)) != 0
                    onRemoteKeyEvent(
                        if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                        keyCode,
                        port,
                    )
                }
            }
        }
    }

    private fun releaseAllRemoteKeys() {
        for (port in remoteJoypadMasks.indices) {
            if (remoteJoypadMasks[port] != 0) {
                applyRemoteJoypadMask(port, 0)
            }
        }
    }

    // =========================================================================
    // PROTOCOL v7/v8 SAVESTATE CONTAINER ("NETPLAY\x01" + "MEM ")
    // =========================================================================

    private fun unwrapNetplayStateContainer(data: ByteArray): ByteArray {
        if (data.size < 16) return data
        val isNetplayContainer =
            data[0] == 'N'.code.toByte() &&
                data[1] == 'E'.code.toByte() &&
                data[2] == 'T'.code.toByte() &&
                data[3] == 'P'.code.toByte() &&
                data[4] == 'L'.code.toByte() &&
                data[5] == 'A'.code.toByte() &&
                data[6] == 'Y'.code.toByte() &&
                data[7] == 1.toByte()
        if (!isNetplayContainer) return data

        var offset = 8
        while (offset + 8 <= data.size) {
            val tag0 = data[offset].toInt().toChar()
            val tag1 = data[offset + 1].toInt().toChar()
            val tag2 = data[offset + 2].toInt().toChar()
            val tag3 = data[offset + 3].toInt().toChar()
            val blockSize = readLeInt(data, offset + 4)
            offset += 8
            if (blockSize < 0 || offset + blockSize > data.size) break
            if (tag0 == 'M' && tag1 == 'E' && tag2 == 'M' && tag3 == ' ') {
                return data.copyOfRange(offset, offset + blockSize)
            }
            if (tag0 == 'E' && tag1 == 'N' && tag2 == 'D' && tag3 == ' ') {
                break
            }
            val alignedSize = (blockSize + 7) and 7.inv()
            offset += alignedSize
        }
        return data
    }

    private fun wrapNetplayStateContainer(coreState: ByteArray): ByteArray {
        val alignedLen = (coreState.size + 7) and 7.inv()
        val bos = ByteArrayOutputStream(8 + 8 + alignedLen + 8)
        bos.write(byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'T'.code.toByte(), 'P'.code.toByte(), 'L'.code.toByte(), 'A'.code.toByte(), 'Y'.code.toByte(), 1))
        bos.write(byteArrayOf('M'.code.toByte(), 'E'.code.toByte(), 'M'.code.toByte(), ' '.code.toByte()))
        writeLeInt(bos, coreState.size)
        bos.write(coreState)
        repeat(alignedLen - coreState.size) { bos.write(0) }
        bos.write(byteArrayOf('E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte(), ' '.code.toByte()))
        writeLeInt(bos, 0)
        return bos.toByteArray()
    }

    // =========================================================================
    // LAN UDP RESPONDER + HELPERS
    // =========================================================================

    private fun startLanDiscoveryResponder(boundPort: Int) {
        scope.launch {
            runCatching {
                val udp = DatagramSocket(MultiplayerSessionConfig.LAN_DISCOVERY_PORT)
                udp.broadcast = true
                lanBeaconSocket = udp
                val buf = ByteArray(512)
                while (!closed.get() && scope.isActive) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        udp.receive(packet)
                        val msg = String(packet.data, 0, packet.length).trim()
                        if (msg == "LEMUROID_HUB_DISCOVER") {
                            val payload =
                                JSONObject()
                                    .put("id", "lan_${config.gameTitle.hashCode()}_$boundPort")
                                    .put("host", config.hostDisplayName)
                                    .put("game", config.gameTitle)
                                    .put("version", config.romFileName.ifBlank { config.gameTitle })
                                    .put("core", normalizeRetroArchCoreName(config.coreName, config.consoleId))
                                    .put("consoleId", config.consoleId)
                                    .put("port", boundPort)
                                    .toString()
                                    .toByteArray()
                            val reply = DatagramPacket(payload, payload.size, packet.address, packet.port)
                            udp.send(reply)
                        }
                    } catch (_: Exception) {
                        if (closed.get()) break
                    }
                }
            }
        }
    }

    private inline fun writePacket(
        out: DataOutputStream,
        block: DataOutputStream.() -> Unit,
    ) {
        synchronized(writeLock) {
            runCatching {
                out.block()
                out.flush()
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        isPeerConnected = false
        clientIsPlaying = false
        outputStream = null
        releaseAllRemoteKeys()
        runCatching { activeSocket?.close() }
        runCatching { relayControlSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { lanBeaconSocket?.close() }
        scope.cancel()
    }

    private fun inferPlatformMagicFromFrontend(frontend: String): Int {
        val f = frontend.lowercase(Locale.ROOT)
        return when {
            "win32 x64" in f || "win64" in f -> PLATFORM_MAGIC_WIN64
            "x64" in f || "x86_64" in f || "arm64" in f || "armv8" in f || "aarch64" in f -> PLATFORM_MAGIC_LP64
            else -> PLATFORM_MAGIC_32
        }
    }

    private fun decodeMitmSession(raw: String): ByteArray? =
        runCatching {
            val normalized = raw.trim().replace('-', '+').replace('_', '/')
            val padded =
                when (normalized.length % 4) {
                    2 -> "$normalized=="
                    3 -> "$normalized="
                    else -> normalized
                }
            Base64.decode(padded, Base64.DEFAULT)
        }.getOrNull()

    private fun sumInputWordsForDevices(
        devicesMask: Int,
        configDevices: IntArray,
    ): Int {
        var total = 0
        for (d in 0 until MAX_INPUT_DEVICES) {
            if ((devicesMask and (1 shl d)) != 0) {
                total += inputWordsForDevice(configDevices.getOrElse(d) { RETRO_DEVICE_JOYPAD })
            }
        }
        return total.coerceAtLeast(1)
    }

    private fun inputWordsForDevice(deviceType: Int): Int {
        val base = deviceType and 0xFF
        val hasAnalogFlag = (deviceType and NETPLAY_DEVICE_ANALOG_JOYPAD_FLAG) != 0
        return when {
            base == RETRO_DEVICE_ANALOG || hasAnalogFlag -> 3
            base == RETRO_DEVICE_MOUSE || base == RETRO_DEVICE_LIGHTGUN -> 2
            base == RETRO_DEVICE_KEYBOARD -> 5
            else -> 1
        }
    }

    private fun resolveLocalIpv4(): String? =
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        }.getOrNull()

    private fun encodeCString(
        text: String,
        fixedLen: Int,
    ): ByteArray {
        val out = ByteArray(fixedLen)
        val raw = text.toByteArray(Charsets.UTF_8)
        System.arraycopy(raw, 0, out, 0, raw.size.coerceAtMost(fixedLen - 1))
        return out
    }

    private fun decodeCString(bytes: ByteArray): String {
        val zeroIdx = bytes.indexOf(0.toByte())
        val len = if (zeroIdx >= 0) zeroIdx else bytes.size
        return String(bytes, 0, len, Charsets.UTF_8).trim()
    }

    private fun readBeInt(
        b: ByteArray,
        offset: Int,
    ): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

    private fun readLeInt(
        b: ByteArray,
        offset: Int,
    ): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLeInt(
        bos: ByteArrayOutputStream,
        v: Int,
    ) {
        bos.write(v and 0xFF)
        bos.write((v ushr 8) and 0xFF)
        bos.write((v ushr 16) and 0xFF)
        bos.write((v ushr 24) and 0xFF)
    }

    private fun firstSetBit(mask: Int): Int {
        for (i in 0 until 16) {
            if ((mask and (1 shl i)) != 0) return i
        }
        return 1
    }

    private fun parseCrc32(hex: String): Int =
        runCatching { hex.trim().removePrefix("0x").toLong(16).toInt() }.getOrDefault(0)

    private fun deflateZlib(data: ByteArray): ByteArray? =
        runCatching {
            val deflater = Deflater(Deflater.BEST_SPEED)
            deflater.setInput(data)
            deflater.finish()
            val bos = ByteArrayOutputStream(data.size / 2 + 64)
            val buf = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n > 0) bos.write(buf, 0, n) else break
            }
            deflater.end()
            bos.toByteArray()
        }.getOrNull()

    private fun inflateZlib(
        compressed: ByteArray,
        expectedSize: Int,
    ): ByteArray? =
        runCatching {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val bos = ByteArrayOutputStream(expectedSize.coerceIn(1024, MAX_PACKET_BYTES))
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n > 0) bos.write(buf, 0, n) else break
            }
            inflater.end()
            bos.toByteArray()
        }.getOrNull()

    private fun normalizeRetroArchCoreName(
        coreName: String,
        consoleId: String,
    ): String {
        val mapped =
            when (consoleId.lowercase(Locale.ROOT)) {
                "snes" -> "Snes9x"
                "nes" -> "FCEUmm"
                "md", "genesis" -> "Genesis Plus GX"
                "gba" -> "mGBA"
                "gb", "gbc" -> "Gambatte"
                "psx" -> "PCSX-ReARMed"
                "n64" -> "Mupen64Plus-Next"
                "fbneo", "mame", "mame2003plus", "arcade" -> "FinalBurn Neo"
                else -> null
            }
        return mapped ?: coreName.ifBlank { "Libretro" }
    }

    private fun androidKeyToRetroPadBit(keyCode: Int): Int =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B -> 0 // RETRO_DEVICE_ID_JOYPAD_B
            KeyEvent.KEYCODE_BUTTON_Y -> 1 // Y
            KeyEvent.KEYCODE_BUTTON_SELECT -> 2 // SELECT
            KeyEvent.KEYCODE_BUTTON_START -> 3 // START
            KeyEvent.KEYCODE_DPAD_UP -> 4 // UP
            KeyEvent.KEYCODE_DPAD_DOWN -> 5 // DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> 6 // LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> 7 // RIGHT
            KeyEvent.KEYCODE_BUTTON_A -> 8 // A
            KeyEvent.KEYCODE_BUTTON_X -> 9 // X
            KeyEvent.KEYCODE_BUTTON_L1 -> 10 // L
            KeyEvent.KEYCODE_BUTTON_R1 -> 11 // R
            KeyEvent.KEYCODE_BUTTON_L2 -> 12 // L2
            KeyEvent.KEYCODE_BUTTON_R2 -> 13 // R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 14 // L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 15 // R3
            else -> -1
        }

    private fun retroPadBitToAndroidKey(bit: Int): Int =
        when (bit) {
            0 -> KeyEvent.KEYCODE_BUTTON_B
            1 -> KeyEvent.KEYCODE_BUTTON_Y
            2 -> KeyEvent.KEYCODE_BUTTON_SELECT
            3 -> KeyEvent.KEYCODE_BUTTON_START
            4 -> KeyEvent.KEYCODE_DPAD_UP
            5 -> KeyEvent.KEYCODE_DPAD_DOWN
            6 -> KeyEvent.KEYCODE_DPAD_LEFT
            7 -> KeyEvent.KEYCODE_DPAD_RIGHT
            8 -> KeyEvent.KEYCODE_BUTTON_A
            9 -> KeyEvent.KEYCODE_BUTTON_X
            10 -> KeyEvent.KEYCODE_BUTTON_L1
            11 -> KeyEvent.KEYCODE_BUTTON_R1
            12 -> KeyEvent.KEYCODE_BUTTON_L2
            13 -> KeyEvent.KEYCODE_BUTTON_R2
            14 -> KeyEvent.KEYCODE_BUTTON_THUMBL
            15 -> KeyEvent.KEYCODE_BUTTON_THUMBR
            else -> 0
        }

    companion object {
        private const val NETPLAY_MAGIC = 0x52414E50 // RANP
        private const val FULL_MAGIC = 0x46554C4C // FULL
        private const val BANNED_MAGIC = 0x44454E59 // DENY

        private const val MITM_SESSION_MAGIC = 0x52415453 // RATS
        private const val MITM_LINK_MAGIC = 0x5241544C // RATL
        private const val MITM_ADDR_MAGIC = 0x52415441 // RATA
        private const val MITM_PING_MAGIC = 0x52415450 // RATP

        private const val LOW_PROTOCOL_VERSION = 5
        private const val HIGH_PROTOCOL_VERSION = 8
        private const val NETPLAY_COMPRESSION_ZLIB = 1
        private const val PLATFORM_MAGIC_32 = (4 shl 15) or 4
        private const val PLATFORM_MAGIC_WIN64 = (8 shl 15) or 4
        private const val PLATFORM_MAGIC_LP64 = (8 shl 15) or 8
        private const val IMPL_MAGIC = 0x00001918

        private const val NICK_LEN = 32
        private const val MAX_INPUT_DEVICES = 16
        private const val RETRO_DEVICE_JOYPAD = 1
        private const val RETRO_DEVICE_MOUSE = 2
        private const val RETRO_DEVICE_KEYBOARD = 3
        private const val RETRO_DEVICE_LIGHTGUN = 4
        private const val RETRO_DEVICE_ANALOG = 5
        private const val NETPLAY_DEVICE_ANALOG_JOYPAD_FLAG = 1 shl 30

        private const val CMD_NAK = 0x0001
        private const val CMD_DISCONNECT = 0x0002
        private const val CMD_INPUT = 0x0003
        private const val CMD_NOINPUT = 0x0004
        private const val CMD_NICK = 0x0020
        private const val CMD_INFO = 0x0022
        private const val CMD_SYNC = 0x0023
        private const val CMD_PLAY = 0x0025
        private const val CMD_MODE = 0x0026
        private const val CMD_MODE_REFUSED = 0x0027
        private const val CMD_REQUEST_SAVESTATE = 0x0041
        private const val CMD_LOAD_SAVESTATE = 0x0042
        private const val CMD_PAUSE = 0x0043
        private const val CMD_RESUME = 0x0044
        private const val CMD_STALL = 0x0045
        private const val CMD_PING_REQUEST = 0x1100
        private const val CMD_PING_RESPONSE = 0x1101

        private const val SYNC_BIT_PAUSED = 1 shl 31
        private const val MODE_BIT_YOU = 1 shl 31
        private const val MODE_BIT_PLAYING = 1 shl 30

        private const val MAX_PACKET_BYTES = 16 * 1024 * 1024
    }
}
