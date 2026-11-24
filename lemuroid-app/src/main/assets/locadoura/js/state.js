/**
 * Global state, theme/sound/gamepad registries, and persistent settings for Locadoura.
 */

export const STORAGE_KEY = "locadoura_settings_v1";

export const THEMES = [
  {
    id: "clean",
    name: "Básico Limpo",
    description: "Fundo claro/neutro sem efeitos pesados, máxima performance.",
    badge: "Leve",
  },
  {
    id: "retro80s",
    name: "Anos 80/90 (Locadora VHS)",
    description: "Estética locadora retrô, neon magenta/ciano e scanlines CRT sutis.",
    badge: "Clássico",
  },
  {
    id: "nextgen",
    name: "Console Next-Gen",
    description: "Estilo PS5/Switch com vidro fosco, backdrop imersivo e cantos amplos.",
    badge: "Moderno",
  },
  {
    id: "cyber",
    name: "Cyber Arcade",
    description: "Fliperama noturno de alto contraste em verde e âmbar elétrico.",
    badge: "Arcade",
  },
  {
    id: "handheld",
    name: "Portátil Clássico",
    description: "Inspirado em consoles portáteis clássicos em oliva e cinza retrô.",
    badge: "Portátil",
  },
];

export const SOUND_PROFILES = [
  {
    id: "retro80s",
    name: "Anos 80/90 (Chiptune)",
    description: "Ondas quadradas e triangulares estilo 8/16-bit.",
  },
  {
    id: "modern",
    name: "Console Moderno (Suave)",
    description: "Tons senoidais limpos e discretos.",
  },
  {
    id: "arcade",
    name: "Arcade Clássico",
    description: "Efeitos nítidos de fliperama e fichas.",
  },
];

export const GAMEPAD_STYLES = [
  { id: "xbox", name: "Xbox (A B X Y)" },
  { id: "playstation", name: "PlayStation (✕ ○ □ △)" },
  { id: "nintendo", name: "Nintendo (B A Y X)" },
];

export const SOON_TOOLS = [
  {
    id: "ps1-chd",
    name: "PS1 → CHD",
    description: "Converter .bin/.cue/.iso para .chd com economia de espaço.",
    status: "Em breve",
  },
  {
    id: "psp-cso",
    name: "PSP → CSO",
    description: "Comprimir imagens .iso de PSP para .cso.",
    status: "Em breve",
  },
  {
    id: "dc-ps2-chd",
    name: "Dreamcast / PS2 → CHD",
    description: "Conversão em lote para emuladores modernos.",
    status: "Em breve",
  },
  {
    id: "hash-crc",
    name: "Verificador Hash / CRC",
    description: "Validar integridade CRC32/MD5/SHA1 das ROMs baixadas.",
    status: "Em breve",
  },
];

export const DEFAULT_SETTINGS = {
  mode: "auto", // "auto" | "mobile" | "tv"
  theme: "retro80s",
  soundEnabled: true,
  soundVolume: 0.65,
  soundProfile: "retro80s",
  gamepadStyle: "xbox",
  analogAsDpad: true,
  deadzone: 0.25,
  rumbleEnabled: true,
  folders: {
    cache: "Locadoura/Cache",
    games: "Locadoura/ROMs",
    temp: "Locadoura/Temp",
    saves: "Locadoura/Saves",
  },
  folderUris: {
    cache: "",
    games: "",
    temp: "",
    saves: "",
  },
  consoleFolders: {},
  consoleFolderUris: {},
  favorites: {},
  showInlineArt: true,
};

const listeners = new Set();
let currentSettings = loadSettings();

function cloneDefaults() {
  return {
    ...DEFAULT_SETTINGS,
    folders: { ...DEFAULT_SETTINGS.folders },
    folderUris: { ...DEFAULT_SETTINGS.folderUris },
    consoleFolders: {},
    consoleFolderUris: {},
    favorites: {},
  };
}

function normalizeFavorites(raw) {
  if (Array.isArray(raw)) {
    const map = {};
    for (const item of raw) {
      if (typeof item === "string" && item) map[item] = true;
    }
    return map;
  }
  if (raw && typeof raw === "object") {
    const map = {};
    for (const [k, v] of Object.entries(raw)) {
      if (v) map[k] = true;
    }
    return map;
  }
  return {};
}

function sanitizeSettings(raw) {
  const base = cloneDefaults();
  if (!raw || typeof raw !== "object") return base;

  const validModes = new Set(["auto", "mobile", "tv"]);
  const validThemes = new Set(THEMES.map((t) => t.id));
  const validSounds = new Set(SOUND_PROFILES.map((s) => s.id));
  const validPads = new Set(GAMEPAD_STYLES.map((g) => g.id));

  if (validModes.has(raw.mode)) base.mode = raw.mode;
  if (validThemes.has(raw.theme)) base.theme = raw.theme;
  if (typeof raw.soundEnabled === "boolean") base.soundEnabled = raw.soundEnabled;

  if (typeof raw.soundVolume === "number" && Number.isFinite(raw.soundVolume)) {
    base.soundVolume = Math.min(1, Math.max(0, raw.soundVolume));
  }
  if (validSounds.has(raw.soundProfile)) base.soundProfile = raw.soundProfile;
  if (validPads.has(raw.gamepadStyle)) base.gamepadStyle = raw.gamepadStyle;
  if (typeof raw.analogAsDpad === "boolean") base.analogAsDpad = raw.analogAsDpad;

  if (typeof raw.deadzone === "number" && Number.isFinite(raw.deadzone)) {
    base.deadzone = Math.min(0.6, Math.max(0.15, raw.deadzone));
  }
  if (typeof raw.rumbleEnabled === "boolean") base.rumbleEnabled = raw.rumbleEnabled;
  if (typeof raw.showInlineArt === "boolean") base.showInlineArt = raw.showInlineArt;

  if (raw.folders && typeof raw.folders === "object") {
    for (const key of Object.keys(base.folders)) {
      if (typeof raw.folders[key] === "string" && raw.folders[key].trim()) {
        base.folders[key] = raw.folders[key].trim();
      }
    }
  }

  if (raw.folderUris && typeof raw.folderUris === "object") {
    for (const key of Object.keys(base.folderUris)) {
      if (typeof raw.folderUris[key] === "string") {
        base.folderUris[key] = raw.folderUris[key].trim();
      }
    }
  }

  if (raw.consoleFolders && typeof raw.consoleFolders === "object") {
    for (const [cid, val] of Object.entries(raw.consoleFolders)) {
      if (typeof val === "string" && val.trim()) {
        base.consoleFolders[cid] = val.trim();
      }
    }
  }

  if (raw.consoleFolderUris && typeof raw.consoleFolderUris === "object") {
    for (const [cid, val] of Object.entries(raw.consoleFolderUris)) {
      if (typeof val === "string" && val.trim()) {
        base.consoleFolderUris[cid] = val.trim();
      }
    }
  }

  base.favorites = normalizeFavorites(raw.favorites);
  return base;
}

/**
 * Loads settings from localStorage (or returns defaults if unavailable/corrupt).
 */
export function loadSettings() {
  try {
    if (typeof localStorage === "undefined") return cloneDefaults();
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return cloneDefaults();
    return sanitizeSettings(JSON.parse(raw));
  } catch {
    return cloneDefaults();
  }
}

/**
 * Returns the current in-memory settings snapshot.
 */
export function getSettings() {
  return currentSettings;
}

/**
 * Merges and persists settings to localStorage, notifying subscribers.
 */
export function saveSettings(next) {
  const merged = sanitizeSettings({
    ...currentSettings,
    ...next,
    folders: {
      ...currentSettings.folders,
      ...(next && next.folders ? next.folders : {}),
    },
    folderUris: {
      ...currentSettings.folderUris,
      ...(next && next.folderUris ? next.folderUris : {}),
    },
    consoleFolders:
      next && next.consoleFolders !== undefined
        ? next.consoleFolders
        : { ...currentSettings.consoleFolders },
    consoleFolderUris:
      next && next.consoleFolderUris !== undefined
        ? next.consoleFolderUris
        : { ...currentSettings.consoleFolderUris },
    favorites:
      next && next.favorites !== undefined ? next.favorites : currentSettings.favorites,
  });

  currentSettings = merged;
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
    }
  } catch {
    // Ignore storage quota / privacy mode errors
  }

  applyThemeAndMode(merged);
  for (const cb of listeners) {
    try {
      cb(merged);
    } catch {
      // Prevent one broken listener from blocking others
    }
  }
  return merged;
}

/**
 * Updates a single top-level setting key.
 */
export function updateSetting(key, value) {
  return saveSettings({ [key]: value });
}

/**
 * Updates a single folder path inside settings.folders.
 */
export function updateFolderSetting(folderKey, pathValue, uriValue) {
  const next = {
    folders: {
      ...currentSettings.folders,
      [folderKey]: pathValue,
    },
  };
  if (uriValue !== undefined) {
    next.folderUris = {
      ...currentSettings.folderUris,
      [folderKey]: uriValue,
    };
  }
  return saveSettings(next);
}

/**
 * Updates or clears a per-console ROM folder path and optional SAF tree URI.
 */
export function updateConsoleFolderSetting(consoleId, pathValue, uriValue) {
  const nextFolders = { ...currentSettings.consoleFolders };
  const nextUris = { ...currentSettings.consoleFolderUris };

  if (pathValue && pathValue.trim()) {
    nextFolders[consoleId] = pathValue.trim();
  } else {
    delete nextFolders[consoleId];
  }

  if (uriValue !== undefined) {
    if (uriValue && uriValue.trim()) {
      nextUris[consoleId] = uriValue.trim();
    } else {
      delete nextUris[consoleId];
    }
  }

  return saveSettings({
    consoleFolders: nextFolders,
    consoleFolderUris: nextUris,
  });
}

/**
 * Resets all settings to defaults (preserving nothing).
 */
export function resetSettings() {
  currentSettings = cloneDefaults();
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(currentSettings));
    }
  } catch {
    // Ignore
  }
  applyThemeAndMode(currentSettings);
  for (const cb of listeners) {
    try {
      cb(currentSettings);
    } catch {
      // Ignore
    }
  }
  return currentSettings;
}

/**
 * Subscribes to settings changes. Returns an unsubscribe function.
 */
export function subscribeSettings(listener) {
  if (typeof listener !== "function") return () => {};
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Resolves the effective UI mode ("mobile" or "tv") based on user preference or viewport/pointer.
 */
export function resolveEffectiveMode(modeOverride = currentSettings.mode) {
  if (typeof window !== "undefined") {
    if (window.__lemuroidNativeMode === "tv" || window.__lemuroidNativeMode === "mobile") {
      return window.__lemuroidNativeMode;
    }
    try {
      const params = new URLSearchParams(window.location.search);
      const urlMode = params.get("mode");
      if (urlMode === "tv" || urlMode === "mobile") {
        return urlMode;
      }
    } catch {
      // ignore
    }
  }
  if (modeOverride === "mobile" || modeOverride === "tv") {
    return modeOverride;
  }
  if (typeof window === "undefined") {
    return "mobile";
  }
  const isLandscape = window.innerWidth > window.innerHeight;
  return isLandscape ? "tv" : "mobile";
}

/**
 * Builds a stable key for favorites across consoles.
 */
export function getFavoriteKey(consoleId, gameKey) {
  return `${consoleId}:${gameKey}`;
}

export function isFavorite(consoleId, gameKey) {
  return Boolean(currentSettings.favorites[getFavoriteKey(consoleId, gameKey)]);
}

export function toggleFavorite(consoleId, gameKey) {
  const key = getFavoriteKey(consoleId, gameKey);
  const nextFavs = { ...currentSettings.favorites };
  if (nextFavs[key]) {
    delete nextFavs[key];
  } else {
    nextFavs[key] = true;
  }
  saveSettings({ favorites: nextFavs });
  return Boolean(nextFavs[key]);
}

/**
 * Applies `data-theme` and `data-mode` attributes to `document.documentElement`.
 */
export function applyThemeAndMode(settings = currentSettings) {
  if (typeof document === "undefined" || !document.documentElement) return;
  const root = document.documentElement;
  const nativeTheme =
    typeof window !== "undefined" && window.__lemuroidHubThemeKey
      ? window.__lemuroidHubThemeKey
      : settings.theme;
  root.dataset.theme = nativeTheme;
  root.dataset.mode = resolveEffectiveMode(settings.mode);
}
