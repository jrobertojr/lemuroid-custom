/**
 * Multi-console catalog & Libretro artwork loader for Locadoura.
 * Reads JSONs from `data/` (linked/copied from `web/data/`).
 */

const LIBRETRO_ROOT = "https://raw.githubusercontent.com/libretro-thumbnails/";

let consolesCache = null;
let consolesPromise = null;

const consoleDataCache = new Map();
const consoleDataPromises = new Map();

const artCache = new Map();
const artPromises = new Map();

async function fetchJson(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} ao carregar ${url}`);
  }
  return res.json();
}

function hydrateGames(fields, rows, consoleId) {
  if (!Array.isArray(rows) || !Array.isArray(fields)) return [];
  const count = rows.length;
  const result = new Array(count);
  const fLen = fields.length;

  for (let i = 0; i < count; i++) {
    const row = rows[i];
    if (Array.isArray(row)) {
      const obj = {};
      for (let j = 0; j < fLen; j++) {
        obj[fields[j]] = row[j];
      }
      obj._key = consoleId === "mame" ? obj.rom : obj.file || obj.rom;
      result[i] = obj;
    } else if (row && typeof row === "object") {
      result[i] = {
        ...row,
        _key: row._key || (consoleId === "mame" ? row.rom : row.file || row.rom),
      };
    }
  }
  return result;
}

/**
 * Loads `data/consoles.json` and returns the array of console metadata.
 */
export async function loadConsoles() {
  if (consolesCache) return consolesCache;
  if (consolesPromise) return consolesPromise;

  consolesPromise = (async () => {
    try {
      const data = await fetchJson("data/consoles.json");
      consolesCache = Array.isArray(data?.consoles) ? data.consoles : [];
      return consolesCache;
    } finally {
      consolesPromise = null;
    }
  })();

  return consolesPromise;
}

/**
 * Returns cached console metadata by id (or null if not yet loaded).
 */
export function getConsoleMeta(consoleId) {
  if (!consolesCache) return null;
  return consolesCache.find((c) => c.id === consoleId) || null;
}

/**
 * Loads and caches the game list for a given consoleId (`mame` -> `data/games.json`,
 * others -> `data/consoles/<id>.json` or `meta.dataFile`).
 */
export async function loadConsoleGames(consoleId) {
  if (consoleDataCache.has(consoleId)) {
    return consoleDataCache.get(consoleId);
  }
  if (consoleDataPromises.has(consoleId)) {
    return consoleDataPromises.get(consoleId);
  }

  const promise = (async () => {
    try {
      const consoles = await loadConsoles();
      const meta = consoles.find((c) => c.id === consoleId) || null;
      const dataFile =
        (meta && meta.dataFile) ||
        (consoleId === "mame" ? "data/games.json" : `data/consoles/${consoleId}.json`);

      const raw = await fetchJson(dataFile);
      const fields = Array.isArray(raw.fields) ? raw.fields : [];
      const games = hydrateGames(fields, raw.games, consoleId);
      const bundle = {
        id: consoleId,
        meta,
        baseUrl: String(raw.baseUrl || ""),
        libretroRepo: raw.libretroRepo || (meta && meta.libretroRepo) || "",
        fields,
        games,
      };
      consoleDataCache.set(consoleId, bundle);
      return bundle;
    } finally {
      consoleDataPromises.delete(consoleId);
    }
  })();

  consoleDataPromises.set(consoleId, promise);
  return promise;
}

/**
 * Loads and caches the verified Libretro art index `data/art/<id>.json`.
 */
export async function loadConsoleArt(consoleId) {
  if (artCache.has(consoleId)) {
    return artCache.get(consoleId);
  }
  if (artPromises.has(consoleId)) {
    return artPromises.get(consoleId);
  }

  const promise = (async () => {
    try {
      const raw = await fetchJson(`data/art/${consoleId}.json`);
      const index = {
        id: consoleId,
        repo: raw.repo || "",
        entries: raw.entries || {},
      };
      artCache.set(consoleId, index);
      return index;
    } catch {
      const fallback = { id: consoleId, repo: "", entries: {} };
      artCache.set(consoleId, fallback);
      return fallback;
    } finally {
      artPromises.delete(consoleId);
    }
  })();

  artPromises.set(consoleId, promise);
  return promise;
}

/**
 * Returns the unique game key for a game object within a console.
 */
export function getGameKey(consoleId, game) {
  if (!game) return "";
  if (game._key) return game._key;
  return consoleId === "mame" ? String(game.rom || "") : String(game.file || game.rom || "");
}

/**
 * Builds the direct download URL for a game in a given console.
 */
export function getDownloadUrl(consoleId, game) {
  if (!game) return "";
  const bundle = consoleDataCache.get(consoleId);
  const baseUrl = bundle ? bundle.baseUrl : "";
  if (consoleId === "mame") {
    return baseUrl + encodeURIComponent(`${game.rom}.zip`);
  }
  const pathParts = String(game.file || game.filename || "").split("/");
  return baseUrl + pathParts.map(encodeURIComponent).join("/");
}

/**
 * Builds verified Libretro Boxart & Snap/Title URLs for a game using cached art index.
 */
export function getArtUrls(consoleId, game) {
  const index = artCache.get(consoleId);
  if (!index || !index.entries || !index.repo || !game) {
    return { indexLoaded: Boolean(index), hasEntry: false, boxUrl: null, snapUrl: null };
  }

  const key = consoleId === "mame" ? game.rom : game.file;
  const val = Object.prototype.hasOwnProperty.call(index.entries, key)
    ? index.entries[key]
    : undefined;

  if (!val) {
    return { indexLoaded: true, hasEntry: false, boxUrl: null, snapUrl: null };
  }

  const base = `${LIBRETRO_ROOT}${encodeURIComponent(index.repo)}/master/`;
  let boxName = "";
  let snapName = "";
  let titleName = "";

  if (typeof val === "string") {
    boxName = val;
    snapName = val;
  } else if (Array.isArray(val)) {
    boxName = val[0] ? String(val[0]) : "";
    snapName = val[1] ? String(val[1]) : "";
    titleName = val[2] ? String(val[2]) : "";
  }

  const boxUrl = boxName ? `${base}Named_Boxarts/${encodeURIComponent(`${boxName}.png`)}` : null;
  const snapUrl = snapName
    ? `${base}Named_Snaps/${encodeURIComponent(`${snapName}.png`)}`
    : titleName
    ? `${base}Named_Titles/${encodeURIComponent(`${titleName}.png`)}`
    : null;

  return { indexLoaded: true, hasEntry: Boolean(boxUrl || snapUrl), boxUrl, snapUrl };
}

/**
 * Formats byte count into human-readable string.
 */
export function formatBytes(bytes) {
  const num = Number(bytes);
  if (!Number.isFinite(num) || num <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let idx = 0;
  let val = num;
  while (val >= 1024 && idx < units.length - 1) {
    val /= 1024;
    idx++;
  }
  return `${val >= 100 || idx === 0 ? val.toFixed(0) : val.toFixed(1)} ${units[idx]}`;
}
