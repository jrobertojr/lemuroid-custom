/**
 * Real-time Download Queue Manager for Locadoura.
 * Tracks active, queued, completed, failed, and cancelled ROM downloads
 * and bridges progress events from LocadouraNativePlugin.
 */

import { getSettings } from "./state.js";
import { getDownloadUrl } from "./catalog.js";
import { registerLocalFile } from "./library-sync.js";

const queue = []; // newest first
const listeners = new Set();
let nativeListenerBound = false;

function notify() {
  const snapshot = getDownloadQueue();
  for (const cb of listeners) {
    try {
      cb(snapshot);
    } catch {
      // ignore
    }
  }
}

export function subscribeDownloadQueue(cb) {
  if (typeof cb !== "function") return () => {};
  listeners.add(cb);
  return () => listeners.delete(cb);
}

export function getDownloadQueue() {
  return queue.map((item) => ({ ...item }));
}

export function getActiveDownloadCount() {
  return queue.filter((i) => i.status === "queued" || i.status === "downloading").length;
}

export function initDownloadQueueBridge() {
  if (nativeListenerBound) return;
  const native = window.Capacitor?.Plugins?.LocadouraNative;
  if (!native || typeof native.addListener !== "function") return;

  nativeListenerBound = true;
  native.addListener("downloadProgress", (ev) => {
    if (!ev || !ev.id) return;
    const item = queue.find((q) => q.id === ev.id);
    if (!item) return;

    item.status = ev.status || item.status;
    if (typeof ev.bytesDownloaded === "number") item.bytesDownloaded = ev.bytesDownloaded;
    if (typeof ev.totalBytes === "number" && ev.totalBytes > 0) item.totalBytes = ev.totalBytes;
    if (ev.error) item.error = ev.error;
    item.updatedAt = Date.now();

    if (item.status === "completed") {
      registerLocalFile(item.consoleId, item.filename, item.bytesDownloaded || item.totalBytes || 0);
    }
    notify();
  });
}

export async function enqueueDownload(consoleId, game, { onToast } = {}) {
  if (!game) return;
  initDownloadQueueBridge();

  const url = getDownloadUrl(consoleId, game);
  if (!url) {
    onToast?.("Link de download indisponível.");
    return;
  }

  const filename = game.file
    ? String(game.file).split("/").pop()
    : game.rom
    ? `${game.rom}.zip`
    : "rom.zip";

  const id = `dl_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const entry = {
    id,
    consoleId,
    title: game.title || filename,
    filename,
    url,
    status: "queued",
    bytesDownloaded: 0,
    totalBytes: Number(game.size || 0),
    error: "",
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };

  queue.unshift(entry);
  if (queue.length > 100) queue.pop();
  notify();

  const native = window.Capacitor?.Plugins?.LocadouraNative;
  if (native && typeof native.downloadFile === "function") {
    onToast?.(`Baixando ${filename}...`);
    try {
      const settings = getSettings();
      const treeUri =
        settings.consoleFolderUris?.[consoleId] ||
        settings.folderUris?.games ||
        "";
      const res = await native.downloadFile({
        id,
        url,
        filename,
        treeUri,
      });
      if (res?.cancelled) {
        entry.status = "cancelled";
        notify();
        onToast?.(`Download cancelado: ${filename}`);
        return;
      }
      entry.status = "completed";
      if (typeof res?.bytesDownloaded === "number") {
        entry.bytesDownloaded = res.bytesDownloaded;
        entry.totalBytes = Math.max(entry.totalBytes, res.bytesDownloaded);
      } else {
        entry.bytesDownloaded = entry.totalBytes;
      }
      registerLocalFile(consoleId, filename, entry.bytesDownloaded || entry.totalBytes || 0);
      notify();
      onToast?.(`Concluído: ${filename}`);
    } catch (err) {
      entry.status = "failed";
      entry.error = err?.message || "falha nativa";
      notify();
      onToast?.(`Erro ao baixar ${filename}: ${entry.error}`);
    }
    return;
  }

  // Web fallback
  window.open(url, "_blank", "noopener,noreferrer");
  entry.status = "completed";
  entry.bytesDownloaded = entry.totalBytes;
  notify();
  onToast?.(`Iniciando download: ${entry.title}`);
}

export async function cancelDownloadItem(id) {
  const item = queue.find((q) => q.id === id);
  if (!item) return;
  const native = window.Capacitor?.Plugins?.LocadouraNative;
  if (native && typeof native.cancelDownload === "function") {
    try {
      await native.cancelDownload({ id });
    } catch {
      // ignore
    }
  }
  if (item.status === "queued" || item.status === "downloading") {
    item.status = "cancelled";
    notify();
  }
}

export function clearCompletedDownloads() {
  for (let i = queue.length - 1; i >= 0; i--) {
    const st = queue[i].status;
    if (st === "completed" || st === "failed" || st === "cancelled") {
      queue.splice(i, 1);
    }
  }
  notify();
}
