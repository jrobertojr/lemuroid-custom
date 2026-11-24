/**
 * Deterministic spatial D-Pad focus engine for Locadoura TV mode.
 * Only elements with [data-focusable="true"] that are visible and enabled can receive focus.
 * Automatically traps focus within open [data-focus-scope="modal"] or [data-focus-scope="sidebar"].
 */

let currentFocused = null;
const focusHistory = [];
let onFocusChangeCb = null;

function isElementVisible(el, rects = null) {
  if (!el || !el.isConnected) return false;
  if (el.disabled || el.getAttribute("aria-disabled") === "true") return false;
  if (el.closest('[aria-hidden="true"]')) return false;
  if (el.closest(".hidden")) return false;

  const style = typeof getComputedStyle === "function" ? getComputedStyle(el) : null;
  if (style && (style.display === "none" || style.visibility === "hidden")) {
    return false;
  }

  const rect = el.getBoundingClientRect();
  if (rects) rects.set(el, rect);
  return rect.width > 0 && rect.height > 0;
}

/**
 * Returns the highest-priority active focus scope element (modal > sidebar > document).
 */
export function getActiveScope() {
  if (typeof document === "undefined") return null;

  const modals = document.querySelectorAll('[data-focus-scope="modal"]');
  for (let i = modals.length - 1; i >= 0; i--) {
    if (isElementVisible(modals[i])) return modals[i];
  }

  const sidebars = document.querySelectorAll('[data-focus-scope="sidebar"]');
  for (let i = sidebars.length - 1; i >= 0; i--) {
    if (isElementVisible(sidebars[i])) return sidebars[i];
  }

  return document;
}

/**
 * Returns all valid, visible [data-focusable="true"] elements within the given scope.
 */
export function getFocusableElements(scopeRoot = getActiveScope(), rects = null) {
  if (!scopeRoot || typeof scopeRoot.querySelectorAll !== "function") return [];
  const nodes = scopeRoot.querySelectorAll('[data-focusable="true"]');
  const result = [];
  for (let i = 0; i < nodes.length; i++) {
    if (isElementVisible(nodes[i], rects)) {
      result.push(nodes[i]);
    }
  }
  return result;
}

export function getCurrentFocused() {
  if (currentFocused && isElementVisible(currentFocused)) {
    return currentFocused;
  }
  return null;
}

export function setOnFocusChange(callback) {
  onFocusChangeCb = typeof callback === "function" ? callback : null;
}

export function clearFocus() {
  focusHistory.length = 0;
  if (currentFocused && currentFocused.classList) {
    currentFocused.classList.remove("is-focused");
  }
  const prev = currentFocused;
  currentFocused = null;
  if (prev && onFocusChangeCb) {
    onFocusChangeCb(null, prev);
  }
}

/**
 * Focuses a specific [data-focusable="true"] element, adds `.is-focused`, and scrolls into view.
 */
export function focusElement(el, options = {}) {
  const { scroll = true, silent = false } = options;
  if (!el || el.getAttribute?.("data-focusable") !== "true" || !isElementVisible(el)) {
    return null;
  }

  const prev = currentFocused;
  if (prev && prev !== el && prev.classList) {
    prev.classList.remove("is-focused");
  }

  if (prev && prev !== el) {
    focusHistory.push(prev);
    if (focusHistory.length > 32) focusHistory.shift();
  }
  currentFocused = el;
  el.classList.add("is-focused");

  if (typeof el.focus === "function") {
    try {
      el.focus({ preventScroll: true });
    } catch {
      el.focus();
    }
  }

  if (scroll && typeof el.scrollIntoView === "function") {
    try {
      el.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "auto" });
    } catch {
      el.scrollIntoView();
    }
  }

  if (!silent && onFocusChangeCb && prev !== el) {
    onFocusChangeCb(el, prev);
  }
  return el;
}

/**
 * Focuses the first valid focusable element inside the given scope (or active scope).
 */
export function focusFirst(scopeOrSelector = null, options = {}) {
  let root = null;
  if (typeof scopeOrSelector === "string" && typeof document !== "undefined") {
    root = document.querySelector(scopeOrSelector);
  } else if (scopeOrSelector && typeof scopeOrSelector.querySelectorAll === "function") {
    root = scopeOrSelector;
  }
  if (!root) {
    root = getActiveScope();
  }

  const candidates = getFocusableElements(root);
  if (candidates.length === 0) return null;
  return focusElement(candidates[0], options);
}

/**
 * Ensures the currently focused element is still valid and inside the active scope.
 * If not, focuses the first element in the active scope.
 */
export function refreshFocus(options = {}) {
  const scope = getActiveScope();
  const cur = getCurrentFocused();
  if (cur && (scope === document || scope.contains(cur))) {
    cur.classList.add("is-focused");
    return cur;
  }
  // Closing a dialog returns to its source card instead of the first header action.
  while (focusHistory.length) {
    const previous = focusHistory.pop();
    if (isElementVisible(previous) && (scope === document || scope.contains(previous))) {
      return focusElement(previous, options);
    }
  }
  return focusFirst(scope, options);
}

function axisOverlap(aStart, aEnd, bStart, bEnd) {
  return Math.min(aEnd, bEnd) - Math.max(aStart, bStart) > 2;
}

/**
 * Moves focus deterministically in one of: "up" | "down" | "left" | "right".
 * Returns the newly focused element, or null if no candidate exists in that direction.
 */
export function move(direction) {
  const scope = getActiveScope();
  const rects = new Map();
  const candidates = getFocusableElements(scope, rects);
  if (candidates.length === 0) return null;

  const cur = getCurrentFocused();
  if (!cur || (scope !== document && !scope.contains(cur))) {
    return focusElement(candidates[0]);
  }

  const curRect = rects.get(cur) || cur.getBoundingClientRect();
  const curCx = curRect.left + curRect.width / 2;
  const curCy = curRect.top + curRect.height / 2;

  let bestEl = null;
  let bestScore = Infinity;

  for (let i = 0; i < candidates.length; i++) {
    const cand = candidates[i];
    if (cand === cur) continue;

    const r = rects.get(cand);
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;

    let isValidDir = false;
    let primaryDist = 0;
    let perpDist = 0;
    let hasOverlap = false;

    switch (direction) {
      case "left":
        isValidDir = cx < curCx - 2;
        primaryDist = curCx - cx;
        perpDist = Math.abs(cy - curCy);
        hasOverlap = axisOverlap(curRect.top, curRect.bottom, r.top, r.bottom);
        break;
      case "right":
        isValidDir = cx > curCx + 2;
        primaryDist = cx - curCx;
        perpDist = Math.abs(cy - curCy);
        hasOverlap = axisOverlap(curRect.top, curRect.bottom, r.top, r.bottom);
        break;
      case "up":
        isValidDir = cy < curCy - 2;
        primaryDist = curCy - cy;
        perpDist = Math.abs(cx - curCx);
        hasOverlap = axisOverlap(curRect.left, curRect.right, r.left, r.right);
        break;
      case "down":
        isValidDir = cy > curCy + 2;
        primaryDist = cy - curCy;
        perpDist = Math.abs(cx - curCx);
        hasOverlap = axisOverlap(curRect.left, curRect.right, r.left, r.right);
        break;
      default:
        return null;
    }

    if (!isValidDir) continue;

    // Strongly favor elements aligned along the orthogonal axis
    const score = primaryDist + perpDist * (hasOverlap ? 0.35 : 2.6);
    if (score < bestScore) {
      bestScore = score;
      bestEl = cand;
    }
  }

  if (bestEl) {
    return focusElement(bestEl);
  }
  return null;
}
