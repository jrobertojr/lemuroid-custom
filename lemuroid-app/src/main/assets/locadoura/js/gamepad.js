/**
 * Gamepad API polling loop + Keyboard bindings + Button Prompt SVG/glyph labels.
 * Normalized actions:
 *   "up" | "down" | "left" | "right" | "confirm" | "back" | "search" | "menu" | "prevTab" | "nextTab"
 */

import { getSettings } from "./state.js";

const INITIAL_REPEAT_MS = 220;
const REPEAT_INTERVAL_MS = 90;

const actionListeners = new Set();
const connectionListeners = new Set();

let rafId = null;
let keyboardBound = false;
let connectionBound = false;
let lastConnectedCount = 0;

// Tracks active held state per action: action -> { nextFireAt: number }
const heldActions = new Map();

const REPEATABLE_ACTIONS = new Set(["up", "down", "left", "right"]);

function emitAction(action, source = "gamepad") {
  for (const cb of actionListeners) {
    try {
      cb(action, source);
    } catch {
      // Isolate listener errors
    }
  }
}

function isEditableElement(el) {
  if (!el) return false;
  const tag = el.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return true;
  return Boolean(el.isContentEditable);
}

function handleKeyDown(e) {
  const key = e.key;
  const inInput = isEditableElement(e.target);

  if (key === "Escape") {
    e.preventDefault();
    emitAction("back", "keyboard");
    return;
  }

  if (inInput) {
    // Allow Enter inside search input to submit/confirm without hijacking typing
    if (key === "Enter" && e.target.dataset?.gamepadConfirm === "true") {
      emitAction("confirm", "keyboard");
    }
    return;
  }

  let action = null;
  switch (key) {
    case "ArrowUp":
      action = "up";
      break;
    case "ArrowDown":
      action = "down";
      break;
    case "ArrowLeft":
      action = "left";
      break;
    case "ArrowRight":
      action = "right";
      break;
    case "Enter":
      action = "confirm";
      break;
    case "Backspace":
      action = "back";
      break;
    case "Tab":
      action = e.shiftKey ? "prevTab" : "nextTab";
      break;
    case "q":
    case "Q":
      action = "prevTab";
      break;
    case "e":
    case "E":
      action = "nextTab";
      break;
    case "/":
    case "f":
    case "F":
      action = "search";
      break;
    case "m":
    case "M":
      action = "menu";
      break;
    default:
      break;
  }

  if (action) {
    e.preventDefault();
    if (e.repeat && !REPEATABLE_ACTIONS.has(action)) return;
    emitAction(action, "keyboard");
  }
}

function readStandardGamepadActions(gp, settings) {
  const active = new Set();
  if (!gp || !gp.connected) return active;

  const btns = gp.buttons || [];
  const pressed = (idx) => Boolean(btns[idx] && (btns[idx].pressed || btns[idx].value > 0.5));

  if (pressed(12)) active.add("up");
  if (pressed(13)) active.add("down");
  if (pressed(14)) active.add("left");
  if (pressed(15)) active.add("right");

  if (pressed(0)) active.add("confirm");
  if (pressed(1)) active.add("back");
  if (pressed(2) || pressed(9)) active.add("menu");
  if (pressed(3)) active.add("search");
  if (pressed(4)) active.add("prevTab");
  if (pressed(5)) active.add("nextTab");
  if (pressed(6)) active.add("library");
  if (pressed(7)) active.add("downloads");

  if (settings.analogAsDpad && Array.isArray(gp.axes) && gp.axes.length >= 2) {
    const dz = typeof settings.deadzone === "number" ? settings.deadzone : 0.25;
    const ax = gp.axes[0] || 0;
    const ay = gp.axes[1] || 0;
    if (ax <= -dz) active.add("left");
    if (ax >= dz) active.add("right");
    if (ay <= -dz) active.add("up");
    if (ay >= dz) active.add("down");
  }

  return active;
}

function pollGamepads(now) {
  rafId = null;
  if (!keyboardBound || (typeof document !== "undefined" && document.hidden)) {
    heldActions.clear();
    return;
  }
  const pads =
    typeof navigator !== "undefined" && typeof navigator.getGamepads === "function"
      ? navigator.getGamepads()
      : [];

  const settings = getSettings();
  const combinedActive = new Set();
  let connectedCount = 0;

  for (let i = 0; i < pads.length; i++) {
    const gp = pads[i];
    if (gp && gp.connected) {
      connectedCount++;
      const actions = readStandardGamepadActions(gp, settings);
      for (const a of actions) combinedActive.add(a);
    }
  }

  if (connectedCount !== lastConnectedCount) {
    lastConnectedCount = connectedCount;
    const list = getConnectedGamepads();
    for (const cb of connectionListeners) {
      try {
        cb(list);
      } catch {
        // Ignore
      }
    }
  }

  // Release actions no longer held
  for (const action of heldActions.keys()) {
    if (!combinedActive.has(action)) {
      heldActions.delete(action);
    }
  }

  // Trigger newly pressed or repeating directional actions
  for (const action of combinedActive) {
    const state = heldActions.get(action);
    if (!state) {
      heldActions.set(action, { nextFireAt: now + INITIAL_REPEAT_MS });
      emitAction(action, "gamepad");
    } else if (REPEATABLE_ACTIONS.has(action) && now >= state.nextFireAt) {
      state.nextFireAt = now + REPEAT_INTERVAL_MS;
      emitAction(action, "gamepad");
    }
  }

  // Connection events wake polling; do not spend 60 callbacks/s without a pad.
  if (connectedCount > 0) rafId = requestAnimationFrame(pollGamepads);
}

function resumePolling() {
  if (keyboardBound && rafId === null && typeof requestAnimationFrame === "function" &&
      !(typeof document !== "undefined" && document.hidden) && getConnectedGamepads().length > 0) {
    rafId = requestAnimationFrame(pollGamepads);
  }
}

function pausePolling() {
  if (rafId !== null) cancelAnimationFrame(rafId);
  rafId = null;
  heldActions.clear();
}

function handleVisibilityChange() {
  if (document.hidden) pausePolling();
  else resumePolling();
}

function handleConnectionEvent() {
  resumePolling();
  const list = getConnectedGamepads();
  lastConnectedCount = list.length;
  for (const cb of connectionListeners) {
    try {
      cb(list);
    } catch {
      // Ignore
    }
  }
}

/**
 * Starts the Gamepad RAF polling loop and keyboard listeners.
 */
export function startInputLoop(onAction) {
  if (typeof onAction === "function") {
    actionListeners.add(onAction);
  }

  if (typeof window !== "undefined") {
    if (!keyboardBound) {
      keyboardBound = true;
      window.addEventListener("keydown", handleKeyDown);
      window.addEventListener("blur", pausePolling);
      window.addEventListener("focus", resumePolling);
      if (typeof document !== "undefined") document.addEventListener("visibilitychange", handleVisibilityChange);
    }
    if (!connectionBound) {
      connectionBound = true;
      window.addEventListener("gamepadconnected", handleConnectionEvent);
      window.addEventListener("gamepaddisconnected", handleConnectionEvent);
    }
    resumePolling();
  }

  return () => {
    if (typeof onAction === "function") {
      actionListeners.delete(onAction);
    }
  };
}

/**
 * Stops the Gamepad RAF polling loop and removes global listeners.
 */
export function stopInputLoop() {
  if (rafId !== null && typeof cancelAnimationFrame === "function") {
    cancelAnimationFrame(rafId);
    rafId = null;
  }
  if (typeof window !== "undefined") {
    if (keyboardBound) {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("blur", pausePolling);
      window.removeEventListener("focus", resumePolling);
      if (typeof document !== "undefined") document.removeEventListener("visibilitychange", handleVisibilityChange);
      keyboardBound = false;
    }
    if (connectionBound) {
      window.removeEventListener("gamepadconnected", handleConnectionEvent);
      window.removeEventListener("gamepaddisconnected", handleConnectionEvent);
      connectionBound = false;
    }
  }
  heldActions.clear();
}

export function onGamepadAction(listener) {
  if (typeof listener !== "function") return () => {};
  actionListeners.add(listener);
  return () => actionListeners.delete(listener);
}

export function onGamepadConnectionChange(listener) {
  if (typeof listener !== "function") return () => {};
  connectionListeners.add(listener);
  return () => connectionListeners.delete(listener);
}

/**
 * Returns a snapshot of currently connected gamepads.
 */
export function getConnectedGamepads() {
  if (typeof navigator === "undefined" || typeof navigator.getGamepads !== "function") {
    return [];
  }
  const pads = navigator.getGamepads();
  const result = [];
  for (let i = 0; i < pads.length; i++) {
    const gp = pads[i];
    if (gp && gp.connected) {
      result.push({
        index: gp.index,
        id: gp.id,
        mapping: gp.mapping || "standard",
        pressedButtons: (gp.buttons || [])
          .map((b, idx) => (b && (b.pressed || b.value > 0.5) ? idx : -1))
          .filter((idx) => idx !== -1),
        axes: Array.from(gp.axes || []).map((v) => Number(v.toFixed(2))),
      });
    }
  }
  return result;
}

/**
 * Triggers dual-rumble haptic feedback on connected gamepads if rumbleEnabled is true.
 */
export function triggerRumble(durationMs = 70, weakMagnitude = 0.25, strongMagnitude = 0.35) {
  const settings = getSettings();
  if (!settings.rumbleEnabled) return;
  if (typeof navigator === "undefined" || typeof navigator.getGamepads !== "function") {
    return;
  }
  const pads = navigator.getGamepads();
  for (let i = 0; i < pads.length; i++) {
    const gp = pads[i];
    if (gp && gp.connected && gp.vibrationActuator && typeof gp.vibrationActuator.playEffect === "function") {
      gp.vibrationActuator
        .playEffect("dual-rumble", {
          startDelay: 0,
          duration: durationMs,
          weakMagnitude,
          strongMagnitude,
        })
        .catch(() => {});
    }
  }
}

function pillSvg(text, bg, border, fg = "#ffffff") {
  return `<svg class="gp-icon" style="display:inline-block;vertical-align:middle;flex-shrink:0;" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><circle cx="12" cy="12" r="10.2" fill="${bg}" stroke="${border}" stroke-width="1.5"/><text x="12" y="12.5" text-anchor="middle" dominant-baseline="central" font-size="11.5" font-weight="900" font-family="system-ui,sans-serif" fill="${fg}">${text}</text></svg>`;
}

function shoulderSvg(text) {
  return `<svg class="gp-icon gp-icon-wide" style="display:inline-block;vertical-align:middle;flex-shrink:0;" viewBox="0 0 34 22" width="29" height="19" aria-hidden="true"><rect x="1" y="1.5" width="32" height="19" rx="7" fill="#2B313F" stroke="rgba(208,216,232,0.78)" stroke-width="1.3"/><text x="17" y="11.5" text-anchor="middle" dominant-baseline="central" font-size="10" font-weight="800" font-family="system-ui,sans-serif" fill="#ffffff">${text}</text></svg>`;
}

/**
 * Returns standardized physical controller button glyphs matching VideoGame's native HUD.
 */
export function getButtonLabels() {
  return {
    style: "standard",
    confirm: { glyph: "A", label: "Confirmar", svg: pillSvg("A", "#28A745", "#9BF5B0") },
    back: { glyph: "B", label: "Voltar", svg: pillSvg("B", "#E03131", "#FFB4B4") },
    menu: { glyph: "X", label: "Menu", svg: pillSvg("X", "#2979FF", "#A6D4FF") },
    search: { glyph: "Y", label: "Buscar", svg: pillSvg("Y", "#E6B400", "#FFE99A", "#1A1400") },
    prevTab: { glyph: "L1", label: "Anterior", svg: shoulderSvg("L1") },
    nextTab: { glyph: "R1", label: "Próximo", svg: shoulderSvg("R1") },
    library: { glyph: "L2", label: "Biblioteca", svg: shoulderSvg("L2") },
    downloads: { glyph: "R2", label: "Downloads", svg: shoulderSvg("R2") },
  };
}
