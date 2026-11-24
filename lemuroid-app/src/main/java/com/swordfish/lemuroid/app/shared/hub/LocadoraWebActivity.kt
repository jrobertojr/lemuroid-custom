package com.swordfish.lemuroid.app.shared.hub

import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Embedded Locadora (19 - RomDownloader) Activity inside the single Lemuroid APK.
 *
 * - Serves bundled `assets/locadoura/` via `https://locadoura.local/` (zero CORS issues, zero Capacitor dep).
 * - Bridges `window.Capacitor.Plugins.LocadouraNative` to Lemuroid's shared global SAF folder.
 * - Injects the active [com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme] palette so
 *   Locadora visually follows the theme selected on the Initial Hub.
 * - Releases the page on exit before entering emulation on memory-constrained TVs.
 */
class LocadoraWebActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val ioExecutor = Executors.newFixedThreadPool(2)
    private val cancelFlags = ConcurrentHashMap<String, AtomicBoolean>()
    private var pendingPickCallbackId: String? = null
    private var backDownInActivity = false

    private val pickFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cbId = pendingPickCallbackId ?: return@registerForActivityResult
            pendingPickCallbackId = null
            if (result.resultCode != Activity.RESULT_OK || result.data?.data == null) {
                resolveJsPromise(cbId, JSONObject().put("cancelled", true))
                return@registerForActivityResult
            }
            val treeUri = result.data!!.data!!
            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val payload =
                JSONObject()
                    .put("cancelled", false)
                    .put("uri", treeUri.toString())
                    .put("displayPath", readableUriPath(treeUri))
            resolveJsPromise(cbId, payload)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveFullScreen()

        val activeTheme = HubConfigManager.getActiveTheme(this)
        val visuals = HubConfigManager.visualsFor(activeTheme)
        val globalFolderUri = HubConfigManager.getGlobalRomFolderUri(this).orEmpty()

        val wv =
            WebView(this).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(android.graphics.Color.parseColor(visuals.webBgHex))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = false
                    allowContentAccess = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                addJavascriptInterface(NativeBridge(), "LemuroidLocadoraNative")
                webChromeClient = WebChromeClient()
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return true
                            // Only packaged content may execute beside the privileged native bridge.
                            return uri.scheme != "https" || uri.host != LOCAL_HOST
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val uri = request?.url ?: return null
                            if (uri.host != LOCAL_HOST) return null
                            val relPath = uri.path?.trimStart('/')?.ifEmpty { "index.html" } ?: "index.html"
                            return runCatching {
                                val stream = assets.open("locadoura/$relPath")
                                val mime = guessMimeType(relPath)
                                val headers = mapOf("Access-Control-Allow-Origin" to "*")
                                WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream)
                            }.getOrElse {
                                WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    404,
                                    "Not Found",
                                    emptyMap(),
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            }
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            if (url?.let { Uri.parse(it).host } == LOCAL_HOST) {
                                injectBridgeAndTheme(view, visuals, globalFolderUri)
                            }
                        }
                    }
            }

        webView = wv
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        setContentView(wv)
        wv.requestFocus()
        val initialMode = resolveEffectiveUiMode(resources.configuration)
        wv.loadUrl("https://$LOCAL_HOST/index.html?mode=$initialMode")
    }

    private fun isAndroidTvDevice(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvUi = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTvUi || hasLeanback
    }

    private fun resolveEffectiveUiMode(config: Configuration): String {
        if (isAndroidTvDevice()) return "tv"
        return if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "tv" else "mobile"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nextMode = resolveEffectiveUiMode(newConfig)
        webView?.evaluateJavascript(
            "window.__lemuroidNativeMode = '$nextMode'; window.dispatchEvent(new Event('resize'));",
            null,
        )
    }

    private fun injectBridgeAndTheme(
        view: WebView?,
        visuals: HubThemeVisuals,
        globalFolderUri: String,
    ) {
        val escapedUri = JSONObject.quote(globalFolderUri)
        val escapedRootPath = JSONObject.quote(HubConfigManager.getReadableGlobalRomFolder(this))
        val discoveredFoldersJson =
            JSONObject(HubConfigManager.getDiscoveredConsoleFolders(this)).toString()
        val folderPlanJson =
            JSONObject().apply {
                HubConfigManager.getConsoleFolderPlan(this@LocadoraWebActivity).forEach { (k, v) ->
                    put(k, JSONObject(v))
                }
            }.toString()
        timber.log.Timber.i(
            "LocadoraFolderPlan: games=%d plan=%s",
            HubConfigManager.getIndexedGamesSnapshot().size,
            folderPlanJson,
        )
        val effectiveMode = resolveEffectiveUiMode(resources.configuration)
        val js =
            """
            (function() {
              try {
                window.__lemuroidNativeMode = '$effectiveMode';
                window.__lemuroidHubThemeKey = ${JSONObject.quote(visuals.webThemeKey)};
                document.documentElement.setAttribute('data-mode', '$effectiveMode');
                document.documentElement.setAttribute('data-theme', ${JSONObject.quote(visuals.webThemeKey)});
                var s = document.getElementById('lemuroid-hub-theme-override');
                if (!s) {
                  s = document.createElement('style');
                  s.id = 'lemuroid-hub-theme-override';
                  document.head.appendChild(s);
                }
                s.textContent =
                  ':root, html, html[data-theme] {' +
                  ' --bg-app: ${visuals.webBgHex} !important;' +
                  ' --bg-surface: ${visuals.webSurfaceHex} !important;' +
                  ' --bg-card: ${visuals.webCardHex} !important;' +
                  ' --bg-elevated: ${visuals.webCardHex} !important;' +
                  ' --bg-overlay: rgba(6, 9, 14, 0.84) !important;' +
                  ' --text-primary: #F8FAFC !important;' +
                  ' --text-secondary: #CBD5E1 !important;' +
                  ' --text-muted: #94A3B8 !important;' +
                  ' --accent: ${visuals.webAccentHex} !important;' +
                  ' --accent-contrast: #0A0D12 !important;' +
                  ' --accent-2: ${visuals.webAccent2Hex} !important;' +
                  ' --border-color: rgba(255, 255, 255, 0.14) !important;' +
                  ' --focus-ring: ${visuals.webAccentHex} !important;' +
                  ' --shadow-card: 0 6px 18px rgba(0, 0, 0, 0.42) !important;' +
                  ' --shadow-focus: 0 0 0 2.5px ${visuals.webAccentHex}, 0 8px 24px rgba(0, 0, 0, 0.55) !important;' +
                  '}' +
                  'html[data-theme] .tv-hero-scrim { background: linear-gradient(90deg, ${visuals.webBgHex} 12%, rgba(8,10,16,0.82) 56%, rgba(8,10,16,0.35) 100%) !important; }' +
                  'html[data-theme] .tv-game-card, html[data-theme] .mobile-game-card { border-top: 1px solid rgba(255,255,255,0.12) !important; border-bottom: 1px solid rgba(255,255,255,0.08) !important; }' +
                  '#mobile-mode-toggle, #tv-mode-mobile-btn { display: none !important; }' +
                  'html[data-mode="tv"] .tv-status-bar { padding: 7px 16px !important; min-height: 44px !important; gap: 8px !important; }' +
                  'html[data-mode="tv"] .tv-status-left, html[data-mode="tv"] .tv-status-right { gap: 7px !important; }' +
                  'html[data-mode="tv"] .tv-brand .brand-logo-img { width: 26px !important; height: 26px !important; }' +
                  'html[data-mode="tv"] .tv-brand .brand-name { font-size: 13px !important; }' +
                  'html[data-mode="tv"] .tv-active-console-pill { font-size: 11px !important; padding: 3px 9px !important; }' +
                  'html[data-mode="tv"] #tv-gamepad-indicator { display: none !important; }' +
                  'html[data-mode="tv"] .tv-top-btn { padding: 5px 9px !important; font-size: 11.5px !important; gap: 5px !important; border-radius: 999px !important; }' +
                  'html[data-mode="tv"] .tv-hero { min-height: 128px !important; padding: 10px 18px !important; }' +
                  'html[data-mode="tv"] .dashboard-eyebrow { display: none !important; }' +
                  'html[data-mode="tv"] .tv-hero-inner { gap: 14px !important; align-items: center !important; }' +
                  'html[data-mode="tv"] .tv-hero-title { font-size: clamp(16px, 2.1vw, 21px) !important; margin: 2px 0 !important; line-height: 1.15 !important; }' +
                  'html[data-mode="tv"] .tv-hero-subtitle { font-size: 11px !important; margin: 0 !important; }' +
                  'html[data-mode="tv"] .tv-hero-actions { margin-top: 7px !important; gap: 8px !important; }' +
                  'html[data-mode="tv"] .tv-primary-btn { padding: 6px 13px !important; font-size: 11.5px !important; gap: 6px !important; display: inline-flex !important; align-items: center !important; }' +
                  'html[data-mode="tv"] .tv-hero-cover { width: 102px !important; height: 82px !important; }' +
                  'html[data-mode="tv"] .tv-hero-print { width: 92px !important; height: 126px !important; }' +
                  'html[data-mode="tv"] .tv-console-section { padding: 6px 18px 2px !important; }' +
                  'html[data-mode="tv"] .tv-console-rail { gap: 7px !important; padding: 3px 0 !important; }' +
                  'html[data-mode="tv"] .tv-console-card { padding: 6px 12px !important; font-size: 11.5px !important; border-radius: 10px !important; }' +
                  'html[data-mode="tv"] .tv-shelf-section { padding: 6px 18px 14px !important; }' +
                  'html[data-mode="tv"] .tv-shelf-header { margin-bottom: 8px !important; }' +
                  'html[data-mode="tv"] .tv-shelf-title { font-size: 13.5px !important; }' +
                  'html[data-mode="tv"] .tv-game-grid { grid-template-columns: repeat(auto-fill, minmax(124px, 1fr)) !important; gap: 10px !important; }' +
                  'html[data-mode="tv"] .tv-game-card { border-radius: 10px !important; }' +
                  'html[data-mode="tv"] .tv-game-thumb-wrap { aspect-ratio: 4 / 3 !important; padding: 6px !important; }' +
                  'html[data-mode="tv"] .tv-game-body { padding: 6px 8px 8px !important; gap: 4px !important; }' +
                  'html[data-mode="tv"] .tv-game-title { font-size: 11px !important; line-height: 1.2 !important; min-height: 2.4em !important; }' +
                  'html[data-mode="tv"] .tv-game-meta { gap: 4px !important; }' +
                  'html[data-mode="tv"] .tv-game-meta .badge { font-size: 9px !important; padding: 1px 5px !important; }' +
                  'html[data-mode="tv"] .tv-prompts-bar { padding: 5px 14px !important; min-height: 34px !important; }' +
                  'html[data-mode="tv"] .tv-prompts-list { gap: 12px !important; font-size: 11px !important; }' +
                  'html[data-mode="tv"] .tv-rail-hint { display: inline-flex !important; align-items: center !important; flex-shrink: 0 !important; opacity: 0.9 !important; }' +
                  'html[data-mode="tv"] *, html[data-mode="tv"] *::before, html[data-mode="tv"] *::after { backdrop-filter: none !important; -webkit-backdrop-filter: none !important; }' +
                  'html[data-mode="tv"] .tv-hero-backdrop { filter: none !important; opacity: 0.28 !important; transition: none !important; }' +
                  'html[data-mode="tv"] .tv-game-card { transition: none !important; box-shadow: none !important; content-visibility: auto !important; contain-intrinsic-size: 128px 148px !important; contain: layout style paint !important; }' +
                  'html[data-mode="tv"] .tv-game-card:focus, html[data-mode="tv"] .tv-game-card.is-focused { box-shadow: 0 0 0 2.5px ${visuals.webAccentHex} !important; transform: none !important; }' +
                  'html[data-mode="tv"] .tv-console-card, html[data-mode="tv"] .tv-top-btn { transition: none !important; box-shadow: none !important; }' +
                  'html[data-mode="tv"] .sidebar { width: min(500px, 64vw) !important; max-width: 500px !important; display: flex !important; flex-direction: column !important; }' +
                  'html[data-mode="tv"] .sidebar-header { padding: 12px 18px !important; }' +
                  'html[data-mode="tv"] .sidebar-tabs { display: flex !important; align-items: center !important; gap: 6px !important; padding: 8px 14px !important; }' +
                  'html[data-mode="tv"] .sidebar-tab-btn { flex: 1 !important; padding: 7px 10px !important; font-size: 12px !important; font-weight: 800 !important; border-radius: 999px !important; }' +
                  'html[data-mode="tv"] .sidebar-body { flex: 1 !important; overflow-y: auto !important; padding: 12px 16px !important; }' +
                  'html[data-mode="tv"] .sb-console-row { width: 100% !important; text-align: left !important; display: flex !important; flex-direction: column !important; gap: 4px !important; padding: 10px 13px !important; border-radius: 10px !important; border: 1.5px solid rgba(255,255,255,0.12) !important; background: rgba(255,255,255,0.04) !important; color: var(--text-primary) !important; cursor: pointer !important; }' +
                  'html[data-mode="tv"] .sb-console-row:focus, html[data-mode="tv"] .sb-console-row.is-focused, html[data-mode="tv"] .sb-btn:focus, html[data-mode="tv"] .sb-btn-secondary:focus, html[data-mode="tv"] .sb-theme-card:focus { outline: none !important; border-color: ${visuals.webAccentHex} !important; box-shadow: 0 0 0 2px ${visuals.webAccentHex} !important; background: rgba(255,255,255,0.10) !important; }' +
                  'html[data-mode="tv"] .sidebar-footer-hints { display: flex !important; align-items: center !important; justify-content: center !important; gap: 12px !important; padding: 8px 14px !important; border-top: 1px solid rgba(255,255,255,0.10) !important; font-size: 11px !important; color: var(--text-secondary) !important; background: rgba(0,0,0,0.28) !important; }';
              } catch (e) {}

              if (!window.__lemuroidBridgeReady) {
                window.__lemuroidBridgeReady = true;
                window.__lemuroidCbs = {};
                window.__lemuroidListeners = {};
                window.__lemuroidCbSeq = 1;
                window.__lemuroidResolve = function(id, jsonStr) {
                  var cb = window.__lemuroidCbs[id];
                  if (cb) { delete window.__lemuroidCbs[id]; cb.resolve(JSON.parse(jsonStr)); }
                };
                window.__lemuroidReject = function(id, msg) {
                  var cb = window.__lemuroidCbs[id];
                  if (cb) { delete window.__lemuroidCbs[id]; cb.reject(new Error(msg)); }
                };
                window.__lemuroidEmit = function(eventName, jsonStr) {
                  var list = window.__lemuroidListeners[eventName] || [];
                  var payload = JSON.parse(jsonStr);
                  list.forEach(function(fn) { try { fn(payload); } catch(e) {} });
                };
                function callNative(method, args) {
                  return new Promise(function(resolve, reject) {
                    var id = 'cb_' + (window.__lemuroidCbSeq++);
                    window.__lemuroidCbs[id] = { resolve: resolve, reject: reject };
                    window.LemuroidLocadoraNative.invoke(method, id, JSON.stringify(args || {}));
                  });
                }
                window.__lemuroidExit = function() { return callNative('closeLocadora'); };
                window.Capacitor = window.Capacitor || {};
                window.Capacitor.isNativePlatform = function() { return true; };
                window.Capacitor.Plugins = window.Capacitor.Plugins || {};
                window.Capacitor.Plugins.LocadouraNative = {
                  pickFolder: function(opts) { return callNative('pickFolder', opts); },
                  downloadFile: function(opts) { return callNative('downloadFile', opts); },
                  cancelDownload: function(opts) { return callNative('cancelDownload', opts); },
                  scanFolders: function(opts) { return callNative('scanFolders', opts); },
                  deleteLocalFile: function(opts) { return callNative('deleteLocalFile', opts); },
                  addListener: function(eventName, fn) {
                    window.__lemuroidListeners[eventName] = window.__lemuroidListeners[eventName] || [];
                    window.__lemuroidListeners[eventName].push(fn);
                    return Promise.resolve({ remove: function() {
                      var list = window.__lemuroidListeners[eventName] || [];
                      var index = list.indexOf(fn);
                      if (index >= 0) list.splice(index, 1);
                    } });
                  }
                };
                window.__lemuroidGlobalRomUri = $escapedUri;
                window.__lemuroidGlobalRomPath = $escapedRootPath;
                window.__lemuroidDiscoveredFolders = $discoveredFoldersJson;
                window.__lemuroidFolderPlan = $folderPlanJson;
                window.Capacitor.Plugins.LocadouraNative.setConsoleFolderOverride = function(consoleId, uri) {
                  return callNative('setConsoleFolderOverride', { consoleId: consoleId, uri: uri || '' });
                };
                try {
                  var rawCfg = localStorage.getItem('locadoura_settings_v1');
                  var cfg = rawCfg ? JSON.parse(rawCfg) : {};
                  cfg.folderUris = cfg.folderUris || {};
                  if ($escapedUri) cfg.folderUris.games = $escapedUri;
                  cfg.consoleFolders = Object.assign({}, cfg.consoleFolders || {}, window.__lemuroidDiscoveredFolders || {});
                  localStorage.setItem('locadoura_settings_v1', JSON.stringify(cfg));
                } catch (e) {}
                if (typeof window.__lemuroidSyncLibrary === 'function') {
                  window.__lemuroidSyncLibrary();
                } else {
                  setTimeout(function() {
                    if (typeof window.__lemuroidSyncLibrary === 'function') {
                      window.__lemuroidSyncLibrary();
                    }
                  }, 450);
                }
              }
            })();
            """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private inner class NativeBridge {
        @JavascriptInterface
        fun invoke(
            method: String,
            cbId: String,
            argsJson: String,
        ) {
            val args = runCatching { JSONObject(argsJson) }.getOrElse { JSONObject() }
            when (method) {
                "closeLocadora" -> runOnUiThread { finish() }

                "pickFolder" -> {
                    runOnUiThread {
                        pendingPickCallbackId = cbId
                        val intent =
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                                )
                            }
                        pickFolderLauncher.launch(intent)
                    }
                }

                "setConsoleFolderOverride" -> {
                    val cid = args.optString("consoleId", "").trim()
                    val uri = args.optString("uri", "").trim()
                    if (cid.equals("__ALL__", ignoreCase = true)) {
                        HubConfigManager.getConsoleFolderPlan(this@LocadoraWebActivity).keys.forEach { k ->
                            HubConfigManager.clearConsoleFolderUri(this@LocadoraWebActivity, k)
                        }
                    } else if (cid.isNotEmpty()) {
                        if (uri.isEmpty()) {
                            HubConfigManager.clearConsoleFolderUri(this@LocadoraWebActivity, cid)
                        } else {
                            HubConfigManager.setConsoleFolderUri(this@LocadoraWebActivity, cid, uri)
                        }
                    }
                    val updatedPlan = JSONObject()
                    HubConfigManager.getConsoleFolderPlan(this@LocadoraWebActivity).forEach { (k, v) ->
                        updatedPlan.put(k, JSONObject(v))
                    }
                    resolveJsPromise(cbId, JSONObject().put("folderPlan", updatedPlan))
                }

                "cancelDownload" -> {
                    val id = args.optString("id", "")
                    cancelFlags[id]?.set(true)
                    resolveJsPromise(cbId, JSONObject().put("cancelled", true))
                }

                "downloadFile" -> {
                    val id = args.optString("id", System.currentTimeMillis().toString())
                    val urlStr = args.optString("url", "")
                    val rawFilename = args.optString("filename", "")
                    val filename = rawFilename.ifBlank { URLUtil.guessFileName(urlStr, null, null) }
                    val treeUriStr =
                        args.optString("treeUri", "").ifBlank {
                            HubConfigManager.getGlobalRomFolderUri(this@LocadoraWebActivity).orEmpty()
                        }
                    val consoleId = args.optString("consoleId", "arcade")

                    if (urlStr.isBlank()) {
                        rejectJsPromise(cbId, "URL inválida.")
                        return
                    }
                    if (treeUriStr.isBlank()) {
                        rejectJsPromise(cbId, "Configure a pasta global de jogos na Tela Inicial antes de baixar.")
                        return
                    }

                    val flag = AtomicBoolean(false)
                    cancelFlags[id] = flag
                    emitDownloadProgress(id, filename, "queued", 0L, 0L, null)
                    ioExecutor.execute {
                        downloadIntoSaf(cbId, id, urlStr, filename, treeUriStr, consoleId, flag)
                    }
                }

                "scanFolders" -> {
                    if (args.optBoolean("triggerRescan", false)) {
                        runCatching { LibraryIndexScheduler.scheduleLibrarySync(applicationContext) }
                    }
                    ioExecutor.execute {
                        val result = JSONObject()
                        val defaultFiles = JSONArray()
                        val seenByConsole = HashMap<String, MutableSet<String>>()
                        var totalScanned = 0
                        val folderPlan = JSONObject()
                        HubConfigManager.getConsoleFolderPlan(this@LocadoraWebActivity).forEach { (k, v) ->
                            folderPlan.put(k, JSONObject(v))
                        }
                        result.put("folderPlan", folderPlan)

                        fun addEntry(
                            consoleId: String,
                            name: String,
                            size: Long,
                            uri: String,
                        ) {
                            if (name.isBlank()) return
                            val seen = seenByConsole.getOrPut(consoleId) { HashSet() }
                            val key = name.lowercase()
                            if (!seen.add(key)) return
                            val item =
                                JSONObject()
                                    .put("name", name)
                                    .put("size", size)
                                    .put("uri", uri)
                                    .put("docId", uri)
                            val arr = result.optJSONArray(consoleId) ?: JSONArray().also { result.put(consoleId, it) }
                            arr.put(item)
                            defaultFiles.put(item)
                            totalScanned += 1
                        }

                        // 1. Feed Locadora's Biblioteca directly from VideoGame's unified indexed library
                        HubConfigManager.getIndexedGamesSnapshot().forEach { game ->
                            val locConsoleId = mapLemuroidSystemToLocadora(game.systemId)
                            addEntry(locConsoleId, game.fileName, 0L, game.fileUri)
                        }

                        // 2. Also include any direct files in SAF folders
                        val foldersObj = args.optJSONObject("folders") ?: JSONObject()
                        val globalUri = HubConfigManager.getGlobalRomFolderUri(this@LocadoraWebActivity)
                        val keys = foldersObj.keys()
                        while (keys.hasNext()) {
                            val consoleId = keys.next()
                            val uriStr = foldersObj.optString(consoleId, "").ifBlank { globalUri.orEmpty() }
                            if (uriStr.isNotBlank()) {
                                val arr = listFilesInTree(Uri.parse(uriStr))
                                for (i in 0 until arr.length()) {
                                    val obj = arr.optJSONObject(i) ?: continue
                                    addEntry(
                                        consoleId,
                                        obj.optString("name", ""),
                                        obj.optLong("size", 0L),
                                        obj.optString("uri", ""),
                                    )
                                }
                            }
                        }
                        resolveJsPromise(
                            cbId,
                            JSONObject()
                                .put("filesByConsole", result)
                                .put("defaultFiles", defaultFiles)
                                .put("totalScanned", totalScanned),
                        )
                    }
                }

                "deleteLocalFile" -> {
                    val docUriStr = args.optString("docUri", args.optString("uri", ""))
                    ioExecutor.execute {
                        val deleted =
                            runCatching {
                                if (docUriStr.isNotBlank()) {
                                    DocumentsContract.deleteDocument(contentResolver, Uri.parse(docUriStr))
                                } else {
                                    false
                                }
                            }.getOrDefault(false)
                        if (deleted) {
                            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                        }
                        resolveJsPromise(cbId, JSONObject().put("deleted", deleted))
                    }
                }

                else -> rejectJsPromise(cbId, "Método nativo desconhecido: $method")
            }
        }
    }

    private fun downloadIntoSaf(
        cbId: String,
        id: String,
        urlStr: String,
        filename: String,
        treeUriStr: String,
        consoleId: String,
        cancelFlag: AtomicBoolean,
    ) {
        var conn: HttpURLConnection? = null
        var target: DocumentFile? = null
        try {
            val globalUri = HubConfigManager.getGlobalRomFolderUri(this)
            val targetDir =
                if (treeUriStr == globalUri) {
                    HubConfigManager.resolveWritableConsoleDir(this, consoleId)
                } else {
                    DocumentFile.fromTreeUri(this, Uri.parse(treeUriStr))
                }
            if (targetDir == null || !targetDir.canWrite()) {
                emitDownloadProgress(id, filename, "failed", 0L, 0L, "Sem permissão de escrita na pasta global.")
                rejectJsPromise(cbId, "Sem permissão de escrita na pasta global.")
                return
            }

            targetDir.findFile(filename)?.delete()
            target = targetDir.createFile("application/octet-stream", filename)
            if (target == null) {
                emitDownloadProgress(id, filename, "failed", 0L, 0L, "Falha ao criar arquivo na pasta.")
                rejectJsPromise(cbId, "Falha ao criar arquivo na pasta.")
                return
            }

            conn =
                (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "LemuroidHub/1.0")
                }
            val code = conn.responseCode
            if (code !in 200..299) {
                target.delete()
                emitDownloadProgress(id, filename, "failed", 0L, 0L, "HTTP $code")
                rejectJsPromise(cbId, "Erro HTTP $code ao baixar ROM.")
                return
            }

            val totalBytes = conn.contentLengthLong.coerceAtLeast(0L)
            var downloaded = 0L
            var lastEmitMs = 0L

            conn.inputStream.use { input ->
                contentResolver.openOutputStream(target.uri)?.use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelFlag.get()) {
                            target.delete()
                            emitDownloadProgress(id, filename, "cancelled", downloaded, totalBytes, null)
                            resolveJsPromise(cbId, JSONObject().put("cancelled", true).put("id", id))
                            return
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 200L) {
                            lastEmitMs = now
                            emitDownloadProgress(id, filename, "downloading", downloaded, totalBytes, null)
                        }
                    }
                    output.flush()
                }
            }

            emitDownloadProgress(id, filename, "completed", downloaded, totalBytes, null)
            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
            resolveJsPromise(
                cbId,
                JSONObject()
                    .put("ok", true)
                    .put("id", id)
                    .put("filename", filename)
                    .put("bytesDownloaded", downloaded),
            )
        } catch (e: Exception) {
            runCatching { target?.delete() }
            val msg = e.message ?: "Erro de download"
            emitDownloadProgress(id, filename, "failed", 0L, 0L, msg)
            rejectJsPromise(cbId, msg)
        } finally {
            cancelFlags.remove(id)
            runCatching { conn?.disconnect() }
        }
    }

    private fun listFilesInTree(treeUri: Uri): JSONArray {
        val arr = JSONArray()
        val root = DocumentFile.fromTreeUri(this, treeUri) ?: return arr
        root.listFiles().forEach { doc ->
            if (doc.isFile) {
                arr.put(
                    JSONObject()
                        .put("name", doc.name.orEmpty())
                        .put("size", doc.length())
                        .put("uri", doc.uri.toString()),
                )
            }
        }
        return arr
    }

    private fun emitDownloadProgress(
        id: String,
        filename: String,
        status: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        error: String?,
    ) {
        val ev =
            JSONObject()
                .put("id", id)
                .put("filename", filename)
                .put("status", status)
                .put("bytesDownloaded", bytesDownloaded)
                .put("totalBytes", totalBytes)
        if (!error.isNullOrBlank()) ev.put("error", error)
        runOnUiThread {
            webView?.evaluateJavascript(
                "window.__lemuroidEmit && window.__lemuroidEmit('downloadProgress', ${JSONObject.quote(
                    ev.toString(),
                )});",
                null,
            )
        }
    }

    private fun resolveJsPromise(
        cbId: String,
        payload: JSONObject,
    ) {
        runOnUiThread {
            webView?.evaluateJavascript(
                "window.__lemuroidResolve && window.__lemuroidResolve(${JSONObject.quote(
                    cbId,
                )}, ${JSONObject.quote(payload.toString())});",
                null,
            )
        }
    }

    private fun rejectJsPromise(
        cbId: String,
        message: String,
    ) {
        runOnUiThread {
            webView?.evaluateJavascript(
                "window.__lemuroidReject && window.__lemuroidReject(${JSONObject.quote(
                    cbId,
                )}, ${JSONObject.quote(message)});",
                null,
            )
        }
    }

    private fun mapLemuroidSystemToLocadora(systemId: String): String =
        when (systemId.lowercase()) {
            "fbneo", "mame2003plus", "arcade" -> "mame"
            "md", "genesis" -> "megadrive"
            "sms" -> "mastersystem"
            else -> systemId.lowercase()
        }

    private fun readableUriPath(uri: Uri): String {
        val decoded = Uri.decode(uri.lastPathSegment ?: uri.toString())
        val idx = decoded.lastIndexOf(':')
        return if (idx >= 0 && idx + 1 < decoded.length) decoded.substring(idx + 1) else decoded
    }

    private fun guessMimeType(path: String): String =
        when {
            path.endsWith(".html", true) -> "text/html"
            path.endsWith(".js", true) -> "application/javascript"
            path.endsWith(".css", true) -> "text/css"
            path.endsWith(".json", true) -> "application/json"
            path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
            path.endsWith(".png", true) -> "image/png"
            path.endsWith(".webp", true) -> "image/webp"
            path.endsWith(".svg", true) -> "image/svg+xml"
            else -> "application/octet-stream"
        }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                backDownInActivity = true
            } else if (event.action == KeyEvent.ACTION_UP && backDownInActivity) {
                backDownInActivity = false
                webView?.evaluateJavascript(
                    "Boolean(window.__lemuroidHandleBack && window.__lemuroidHandleBack())",
                ) { handled ->
                    if (handled != "true" && !isFinishing) finish()
                } ?: finish()
            }
            return true
        }
        val dpadAction =
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> "up"
                KeyEvent.KEYCODE_DPAD_DOWN -> "down"
                KeyEvent.KEYCODE_DPAD_LEFT -> "left"
                KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
                else -> null
            }
        if (dpadAction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val allowFromInput = dpadAction == "up" || dpadAction == "down"
                val guard =
                    if (allowFromInput) {
                        ""
                    } else {
                        "var a=document.activeElement;if(a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'))return;"
                    }
                webView?.evaluateJavascript(
                    "(function(){${guard}if(window.__lemuroidDispatchAction)window.__lemuroidDispatchAction('$dpadAction');})();",
                    null,
                )
            }
            return true
        }
        val mappedAction =
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_X -> "menu"
                KeyEvent.KEYCODE_BUTTON_Y -> "search"
                KeyEvent.KEYCODE_BUTTON_L1 -> "prevTab"
                KeyEvent.KEYCODE_BUTTON_R1 -> "nextTab"
                KeyEvent.KEYCODE_BUTTON_L2 -> "library"
                KeyEvent.KEYCODE_BUTTON_R2 -> "downloads"
                KeyEvent.KEYCODE_BUTTON_START -> "menu"
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> "confirm"
                else -> null
            }
        if (mappedAction != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                webView?.evaluateJavascript(
                    "(function(){var a=document.activeElement;if(a&&(a.tagName==='INPUT'||a.tagName==='TEXTAREA'))return;if(window.__lemuroidDispatchAction)window.__lemuroidDispatchAction('$mappedAction');})();",
                    null,
                )
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveFullScreen()
    }

    private fun applyImmersiveFullScreen() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        webView?.evaluateJavascript("window.dispatchEvent(new Event('focus'));", null)
    }

    override fun onPause() {
        webView?.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null)
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        cancelFlags.values.forEach { it.set(true) }
        cancelFlags.clear()
        ioExecutor.shutdownNow()
        webView?.apply {
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            removeJavascriptInterface("LemuroidLocadoraNative")
            clearHistory()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val LOCAL_HOST = "locadoura.local"
    }
}
