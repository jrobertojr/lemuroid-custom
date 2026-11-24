/**
 * Local ROM Library Sync & Identification Engine for Locadoura.
 *
 * Scans configured Android SAF folders on startup (and on demand) and matches
 * local files against catalog games using 3 strict tiers:
 *   1. Exact filename (e.g. "sf2.zip")
 *   2. Exact stem ignoring extension (e.g. "Super Mario World (U) [!].smc")
 *   3. Strict normalized base title for PT-BR translations / hacks / mods
 *      (e.g. "Chrono Trigger (PT-BR) [T+Por].sfc" -> "Chrono Trigger (USA).zip")
 */

import { getSettings } from "./state.js";

const BRACKET_TAG_RE = /\s*[\(\[][^\)\]]*[\)\]]/g;
const LEADING_NUM_RE = /^\d{4}\s*-\s*/;
const PUNCT_RE = /[:\-_'\.\!\?,/]+/g;
const MULTI_SPACE_RE = /\s+/g;
const MOD_MARKER_RE = /\b(pt[\s_-]?br|t[\+\-_ ]?(?:por|pt|br)|trad(?:uzid[oa]|u[cç][aã]o)?|hack|mod|dublado|v\d+(?:\.\d+)?)\b/i;
const TRAILING_MOD_TOKENS_RE = /(?:\s+(?:pt[\s_-]?br|ptbr|traduzido|traducao|tradução|dublado|hack|mod|br))+$/i;
const DISC_SUFFIX_RE = /\s+(?:CD|Disc|Disk)\s*\d+.*$/i;
const VERSION_SUFFIX_RE = /\s+v\d+(?:\.\d+)*$/i;
const TOKEN_NORMALIZATIONS = {
  ii: "2",
  iii: "3",
  iv: "4",
  vi: "6",
  vii: "7",
  viii: "8",
  ix: "9",
  bros: "brothers",
  vs: "versus",
};
const STOP_WORDS = new Set(["the", "and", "for", "of", "a", "an", "pt", "br", "unl", "hack", "remastered", "ultimate", "traduzido", "traducao", "dublado", "portugues", "espanhol", "ingles", "edition", "rev", "plus"]);
const PENALTY_TAGS = [
  "(Beta", "(Proto", "(Demo", "(Sample", "(Debug",
  "(Aftermarket", "(Unl)", "(Pirate", "(Alt",
  "(Virtual Console", "(Switch Online", "[tr ", "[T-", "[h",
];

function getTokenList(rawName) {
  let s = stripExtension(rawName);
  s = s.replace(LEADING_NUM_RE, "");
  while (true) {
    const next = s.replace(BRACKET_TAG_RE, "").trim();
    if (next === s) break;
    s = next;
  }
  s = s.replace(DISC_SUFFIX_RE, "").replace(VERSION_SUFFIX_RE, "").trim();
  const base = normalizeBaseTitle(s);
  const out = [];
  for (const tok of base.split(" ")) {
    if (!tok) continue;
    const norm = TOKEN_NORMALIZATIONS[tok] || tok;
    if (STOP_WORDS.has(norm)) continue;
    if (norm.length >= 2 || /^\d+$/.test(norm)) {
      out.push(norm);
    }
  }
  return out;
}

function tokenizeStrict(rawName) {
  return getTokenList(rawName).join(" ");
}

function scoreCatalogCandidate(queryTokens, queryNums, candidateSet, candidateNums, candidateTitle) {
  if (queryTokens.length === 0 || candidateSet.size === 0) return -9999;

  // Strict numeric guard: numbers/sequels must match exactly (e.g. "2" != "3")
  if (queryNums.length !== candidateNums.length) return -9999;
  for (let i = 0; i < queryNums.length; i++) {
    if (queryNums[i] !== candidateNums[i]) return -9999;
  }

  let matchedCount = 0;
  for (const t of queryTokens) {
    if (candidateSet.has(t)) matchedCount++;
  }
  if (matchedCount === 0) return -9999;
  if (matchedCount === 1 && (queryTokens.length > 1 || candidateSet.size > 1)) return -9999;

  const querySet = new Set(queryTokens);
  let extraTokens = 0;
  for (const ct of candidateSet) {
    if (!querySet.has(ct)) extraTokens++;
  }

  let score = Math.round((matchedCount * 100) / querySet.size) - extraTokens * 12;
  if (score < 55) return -9999;

  if (candidateTitle.includes("(USA")) score += 30;
  else if (candidateTitle.includes("(World")) score += 28;
  else if (candidateTitle.includes("(Europe")) score += 25;
  else if (candidateTitle.includes("(Brazil")) score += 20;
  else if (candidateTitle.includes("(Japan")) score += 5;

  for (const tag of PENALTY_TAGS) {
    if (candidateTitle.includes(tag)) score -= 40;
  }
  return score;
}

let lastSyncAt = 0;
let lastTotalFiles = 0;
let isSyncing = false;
let consoleIndexes = new Map(); // consoleId -> FolderIndex
let defaultIndex = null;
const syncListeners = new Set();

function stripExtension(filename) {
  const str = String(filename || "").trim();
  const dot = str.lastIndexOf(".");
  return dot > 0 ? str.slice(0, dot) : str;
}

export function normalizeBaseTitle(rawName) {
  let s = stripExtension(rawName);
  s = LEADING_NUM_RE.sub ? s : s.replace(LEADING_NUM_RE, "");

  // Remove all (...) and [...] tags iteratively
  while (true) {
    const next = s.replace(BRACKET_TAG_RE, "").trim();
    if (next === s) break;
    s = next;
  }

  s = s.replace(TRAILING_MOD_TOKENS_RE, "").trim();

  // Move trailing ", The" / ", A" / ", An" to front
  const lower = s.toLowerCase();
  for (const article of [", the", ", a", ", an"]) {
    if (lower.endsWith(article)) {
      const word = article.slice(2);
      s = `${word} ${s.slice(0, -article.length)}`;
      break;
    }
  }

  s = s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/&/g, " and ")
    .replace(PUNCT_RE, " ")
    .replace(MULTI_SPACE_RE, " ")
    .trim();

  return s;
}

function buildFolderIndex(files) {
  const exactNames = new Map(); // lower filename -> fileObj
  const stems = new Map(); // lower stem -> fileObj
  const baseKeys = new Map(); // strict base title -> fileObj
  const tokenKeys = new Map(); // Lemuroid normalized token key -> fileObj

  for (const f of files || []) {
    if (!f || !f.name) continue;
    const lowerName = String(f.name).toLowerCase();
    const stem = stripExtension(lowerName);
    const isMod = MOD_MARKER_RE.test(f.name) || MOD_MARKER_RE.test(f.innerName || "");
    const entry = {
      name: f.name,
      innerName: f.innerName || "",
      size: Number(f.size || 0),
      isMod,
      docId: f.docId || "",
      treeUri: f.treeUri || "",
    };

    if (!exactNames.has(lowerName)) exactNames.set(lowerName, entry);
    if (!stems.has(stem)) stems.set(stem, entry);

    const bk = normalizeBaseTitle(f.name);
    if (bk && !baseKeys.has(bk)) baseKeys.set(bk, entry);

    const tk = tokenizeStrict(f.name);
    if (tk && !tokenKeys.has(tk)) tokenKeys.set(tk, entry);

    // Also index innerName from .zip files (e.g. "jogo1.zip" containing "Super Mario World (USA).sfc")
    if (f.innerName) {
      const innerLower = String(f.innerName).toLowerCase();
      const innerStem = stripExtension(innerLower);
      if (!stems.has(innerStem)) stems.set(innerStem, entry);
      const ibk = normalizeBaseTitle(f.innerName);
      if (ibk && !baseKeys.has(ibk)) baseKeys.set(ibk, entry);
      const itk = tokenizeStrict(f.innerName);
      if (itk && !tokenKeys.has(itk)) tokenKeys.set(itk, entry);
    }
  }

  return { exactNames, stems, baseKeys, tokenKeys, count: (files || []).length };
}

function notifySyncListeners() {
  const snapshot = getSyncStatus();
  for (const cb of syncListeners) {
    try {
      cb(snapshot);
    } catch {
      // ignore listener errors
    }
  }
}

export function subscribeLibrarySync(cb) {
  if (typeof cb !== "function") return () => {};
  syncListeners.add(cb);
  return () => syncListeners.delete(cb);
}

export function getSyncStatus() {
  const hasNativeBridge = Boolean(
    typeof window !== "undefined" &&
      (window.__lemuroidGlobalRomUri || window.Capacitor?.Plugins?.LocadouraNative)
  );
  return {
    isSyncing,
    lastSyncAt,
    lastTotalFiles,
    hasFolders: Boolean(
      hasNativeBridge ||
        getSettings().folderUris?.games ||
        Object.keys(getSettings().consoleFolderUris || {}).length > 0
    ),
  };
}

/**
 * Registers a newly downloaded file immediately in memory so UI updates without rescan.
 */
export function registerLocalFile(consoleId, filename, size = 0) {
  if (!consoleId || !filename) return;
  const idx = consoleIndexes.get(consoleId) || buildFolderIndex([]);
  const lowerName = String(filename).toLowerCase();
  const stem = stripExtension(lowerName);
  const entry = { name: filename, size: Number(size || 0), isMod: MOD_MARKER_RE.test(filename) };
  idx.exactNames.set(lowerName, entry);
  idx.stems.set(stem, entry);
  const bk = normalizeBaseTitle(filename);
  if (bk) idx.baseKeys.set(bk, entry);
  idx.count = idx.exactNames.size;
  consoleIndexes.set(consoleId, idx);
  lastTotalFiles += 1;
  lastSyncAt = Date.now();
  notifySyncListeners();
}

/**
 * Scans configured Android SAF folders via LocadouraNative.scanFolders().
 */
export async function syncLocalLibrary(options = {}) {
  const native = window.Capacitor?.Plugins?.LocadouraNative;
  const settings = getSettings();
  const consoleUris = settings.consoleFolderUris || {};
  const consolePaths = settings.consoleFolders || {};
  const defaultUri = settings.folderUris?.games || "";
  const defaultPath = settings.folders?.games || "";

  if (!native || typeof native.scanFolders !== "function") {
    return getSyncStatus();
  }

  const nativeGlobalUri =
    typeof window !== "undefined" ? String(window.__lemuroidGlobalRomUri || "") : "";
  const effectiveDefaultUri = defaultUri || nativeGlobalUri;

  const hasAny =
    Boolean(effectiveDefaultUri) ||
    Boolean(native) ||
    defaultPath.startsWith("/") ||
    Object.keys(consoleUris).length > 0 ||
    Object.keys(consolePaths).length > 0;

  if (!hasAny) {
    consoleIndexes = new Map();
    defaultIndex = null;
    lastTotalFiles = 0;
    lastSyncAt = Date.now();
    notifySyncListeners();
    return getSyncStatus();
  }

  isSyncing = true;
  notifySyncListeners();

  try {
    const res = await native.scanFolders({
      folders: consoleUris,
      folderPaths: consolePaths,
      defaultUri: effectiveDefaultUri,
      defaultPath,
      triggerRescan: Boolean(options && options.triggerRescan),
    });

    if (res?.folderPlan && typeof window !== "undefined") {
      window.__lemuroidFolderPlan = res.folderPlan;
    }

    const nextMap = new Map();
    const byConsole = res?.filesByConsole || {};
    for (const [cid, list] of Object.entries(byConsole)) {
      if (Array.isArray(list)) {
        nextMap.set(cid, buildFolderIndex(list));
      }
    }

    consoleIndexes = nextMap;
    defaultIndex = Array.isArray(res?.defaultFiles)
      ? buildFolderIndex(res.defaultFiles)
      : null;
    const computedCount = Array.from(nextMap.values()).reduce((sum, idx) => sum + (idx?.count || 0), 0);
    lastTotalFiles = Number(res?.totalScanned || computedCount || 0);
    lastSyncAt = Date.now();
  } catch {
    // Keep previous index on error
  } finally {
    isSyncing = false;
    notifySyncListeners();
  }

  return getSyncStatus();
}

if (typeof window !== "undefined") {
  window.__lemuroidSyncLibrary = syncLocalLibrary;
}

function lookupInIndex(idx, expectedFilename, expectedStem, baseTitleKey, tokenKey) {
  if (!idx) return null;

  const toResult = (entry, kind) => ({
    localName: entry.name,
    localSize: entry.size,
    kind,
    docId: entry.docId || "",
    treeUri: entry.treeUri || "",
  });

  if (expectedFilename) {
    const exact = idx.exactNames.get(expectedFilename);
    if (exact) return toResult(exact, exact.isMod ? "mod" : "exact");
  }

  if (expectedStem) {
    const byStem = idx.stems.get(expectedStem);
    if (byStem) return toResult(byStem, byStem.isMod ? "mod" : "extracted");
  }

  if (baseTitleKey) {
    const byBase = idx.baseKeys.get(baseTitleKey);
    if (byBase) return toResult(byBase, byBase.isMod ? "mod" : "variant");
  }

  if (tokenKey && idx.tokenKeys) {
    const byTok = idx.tokenKeys.get(tokenKey);
    if (byTok) return toResult(byTok, byTok.isMod ? "mod" : "variant");
  }

  return null;
}

/**
 * Checks whether a catalog game is already downloaded locally.
 * Returns null or { localName, localSize, kind: "exact" | "extracted" | "variant" | "mod" }.
 */
export function matchLocalRom(consoleId, game) {
  if (!game) return null;
  const cIdx = consoleIndexes.get(consoleId);
  if (!cIdx && !defaultIndex) return null;

  const rawFile = game.file
    ? String(game.file).split("/").pop()
    : game.rom
    ? `${game.rom}.zip`
    : "";
  const expectedFilename = rawFile.toLowerCase();
  const expectedStem = stripExtension(expectedFilename);

  // For MAME, do NOT match by generic base title across unrelated games unless stem matches;
  // for consoles, match by strict normalized base title & Lemuroid token key.
  const baseTitleKey = consoleId === "mame" ? "" : normalizeBaseTitle(game.title || expectedStem);
  const tokenKey = consoleId === "mame" ? "" : tokenizeStrict(game.title || expectedStem);

  return (
    lookupInIndex(cIdx, expectedFilename, expectedStem, baseTitleKey, tokenKey) ||
    lookupInIndex(defaultIndex, expectedFilename, expectedStem, baseTitleKey, tokenKey)
  );
}

const EXT_TO_CONSOLE = {
  sfc: "snes",
  smc: "snes",
  nes: "nes",
  gba: "gba",
  gbc: "gbc",
  gb: "gb",
  n64: "n64",
  z64: "n64",
  v64: "n64",
  md: "megadrive",
  gen: "megadrive",
  sms: "mastersystem",
  a26: "atari2600",
  nds: "nds",
};

function guessConsoleByExt(filename) {
  const dot = String(filename || "").lastIndexOf(".");
  if (dot < 0) return null;
  const ext = filename.slice(dot + 1).toLowerCase();
  return EXT_TO_CONSOLE[ext] || null;
}

/**
 * Builds a unified library across all consoles that have local files.
 * Returns { items: [{ consoleId, consoleName, game, match }], totalCount }.
 */
export async function getUnifiedLibrary(consolesList, loadGamesFn, loadArtFn) {
  const items = [];
  const seenKeys = new Set();
  const matchedLocalNames = new Set();

  const defaultByConsole = new Map();
  const defaultUnclassified = [];
  if (defaultIndex) {
    for (const entry of defaultIndex.exactNames.values()) {
      const guessed = guessConsoleByExt(entry.name);
      if (guessed) {
        if (!defaultByConsole.has(guessed)) defaultByConsole.set(guessed, []);
        defaultByConsole.get(guessed).push(entry);
      } else {
        defaultUnclassified.push(entry);
      }
    }
  }

  for (const c of consolesList || []) {
    const cIdx = consoleIndexes.get(c.id);
    const defList = defaultByConsole.get(c.id) || [];
    const hasFiles = (cIdx && cIdx.count > 0) || defList.length > 0;
    if (!hasFiles) continue;

    const combinedIdx = cIdx || buildFolderIndex(defList);

    let loadedGames = [];
    try {
      const [bundle] = await Promise.all([
        loadGamesFn(c.id),
        typeof loadArtFn === "function" ? loadArtFn(c.id).catch(() => null) : Promise.resolve(null),
      ]);
      loadedGames = Array.isArray(bundle) ? bundle : bundle?.games || [];
      for (const game of loadedGames) {
        const rawFile = game.file
          ? String(game.file).split("/").pop()
          : game.rom
          ? `${game.rom}.zip`
          : "";
        const expectedFilename = rawFile.toLowerCase();
        const expectedStem = stripExtension(expectedFilename);
        const baseTitleKey = c.id === "mame" ? "" : normalizeBaseTitle(game.title || expectedStem);
        const tokenKey = c.id === "mame" ? "" : tokenizeStrict(game.title || expectedStem);
        const match = lookupInIndex(combinedIdx, expectedFilename, expectedStem, baseTitleKey, tokenKey);
        if (!match) continue;

        const uniq = `${c.id}:${match.localName.toLowerCase()}`;
        if (seenKeys.has(uniq)) continue;
        seenKeys.add(uniq);
        matchedLocalNames.add(match.localName.toLowerCase());
        items.push({
          consoleId: c.id,
          consoleName: c.shortName || c.name || c.id,
          game,
          match,
        });
      }
    } catch {
      // ignore console load error
    }

    // Tier 5 (Lemuroid-style token scoring) for remaining unmatched local files (including descriptive MAME zips)
    const remainingEntries = [];
    for (const entry of combinedIdx.exactNames.values()) {
      const low = entry.name.toLowerCase();
      if (!seenKeys.has(`${c.id}:${low}`)) {
        remainingEntries.push(entry);
      }
    }

    if (remainingEntries.length > 0 && loadedGames.length > 0) {
      const candidatesSource =
        c.id === "mame"
          ? loadedGames.filter((g) => !g.type || g.type === "game")
          : loadedGames;
      const catalogPrepared = candidatesSource.map((game) => {
        const t = game.title || "";
        const toks = getTokenList(t);
        return {
          game,
          title: t,
          set: new Set(toks),
          nums: toks.filter((x) => /^\d+$/.test(x)).sort(),
          isParent: c.id === "mame" && !game.cloneof,
        };
      });

      for (const entry of remainingEntries) {
        const qToks = getTokenList(entry.innerName || entry.name);
        const qNums = qToks.filter((x) => /^\d+$/.test(x)).sort();
        let bestGame = null;
        let bestScore = 65;

        for (const cand of catalogPrepared) {
          let sc = scoreCatalogCandidate(qToks, qNums, cand.set, cand.nums, cand.title);
          if (sc > 0 && cand.isParent) sc += 10;
          if (sc > bestScore) {
            bestScore = sc;
            bestGame = cand.game;
          }
        }

        if (bestGame) {
          const low = entry.name.toLowerCase();
          seenKeys.add(`${c.id}:${low}`);
          matchedLocalNames.add(low);
          items.push({
            consoleId: c.id,
            consoleName: c.shortName || c.name || c.id,
            game: bestGame,
            match: {
              localName: entry.name,
              localSize: entry.size,
              kind: entry.isMod ? "mod" : "variant",
              docId: entry.docId || "",
              treeUri: entry.treeUri || "",
            },
          });
        }
      }
    }

    for (const entry of combinedIdx.exactNames.values()) {
      const low = entry.name.toLowerCase();
      const uniq = `${c.id}:${low}`;
      if (seenKeys.has(uniq)) continue;
      seenKeys.add(uniq);
      matchedLocalNames.add(low);
      items.push({
        consoleId: c.id,
        consoleName: c.shortName || c.name || c.id,
        isUnmatched: true,
        game: { title: stripExtension(entry.name), file: entry.name, size: entry.size },
        match: {
          localName: entry.name,
          localSize: entry.size,
          kind: entry.isMod ? "mod" : "local",
          docId: entry.docId || "",
          treeUri: entry.treeUri || "",
        },
      });
    }
  }

  // If there are unclassified .zip files in default root, try matching them against MAME shortnames
  const unclassifiedZips = defaultUnclassified.filter((e) => e.name.toLowerCase().endsWith(".zip"));
  if (unclassifiedZips.length > 0 && !consoleIndexes.has("mame")) {
    try {
      const mameIdx = buildFolderIndex(unclassifiedZips);
      const [mameBundle] = await Promise.all([
        loadGamesFn("mame"),
        typeof loadArtFn === "function" ? loadArtFn("mame").catch(() => null) : Promise.resolve(null),
      ]);
      const mameGames = Array.isArray(mameBundle) ? mameBundle : mameBundle?.games || [];
      for (const game of mameGames) {
        const expectedFilename = game.rom ? `${game.rom}.zip`.toLowerCase() : "";
        if (!expectedFilename) continue;
        const match = lookupInIndex(mameIdx, expectedFilename, game.rom.toLowerCase(), "", "");
        if (!match) continue;
        const uniq = `mame:${match.localName.toLowerCase()}`;
        if (seenKeys.has(uniq)) continue;
        seenKeys.add(uniq);
        matchedLocalNames.add(match.localName.toLowerCase());
        items.push({
          consoleId: "mame",
          consoleName: "MAME 0.260",
          game,
          match,
        });
      }
    } catch {
      // ignore
    }
  }

  for (const entry of defaultUnclassified) {
    const low = entry.name.toLowerCase();
    if (matchedLocalNames.has(low)) continue;
    matchedLocalNames.add(low);
    items.push({
      consoleId: "local",
      consoleName: "Pasta ROMs",
      isUnmatched: true,
      game: { title: stripExtension(entry.name), file: entry.name, size: entry.size },
      match: {
        localName: entry.name,
        localSize: entry.size,
        kind: entry.isMod ? "mod" : "local",
        docId: entry.docId || "",
        treeUri: entry.treeUri || "",
      },
    });
  }

  return { items, totalCount: items.length };
}

/**
 * Deletes a downloaded ROM file from the device and removes it from the in-memory index.
 */
export async function deleteLocalRom(consoleId, match) {
  if (!match || !match.localName) return false;
  const native = window.Capacitor?.Plugins?.LocadouraNative;
  if (!native || typeof native.deleteLocalFile !== "function") {
    return false;
  }

  const settings = getSettings();
  const treeUri =
    match.treeUri ||
    settings.consoleFolderUris?.[consoleId] ||
    settings.folderUris?.games ||
    "";

  const res = await native.deleteLocalFile({
    docId: match.docId || "",
    treeUri,
    filename: match.localName,
  });

  if (!res?.deleted) return false;

  const lowerName = match.localName.toLowerCase();
  const stem = stripExtension(lowerName);
  const removeFromIdx = (idx) => {
    if (!idx) return;
    idx.exactNames.delete(lowerName);
    idx.stems.delete(stem);
    const bk = normalizeBaseTitle(match.localName);
    if (bk) idx.baseKeys.delete(bk);
    idx.count = idx.exactNames.size;
  };
  removeFromIdx(consoleIndexes.get(consoleId));
  removeFromIdx(defaultIndex);
  lastTotalFiles = Math.max(0, lastTotalFiles - 1);
  notifySyncListeners();
  return true;
}
