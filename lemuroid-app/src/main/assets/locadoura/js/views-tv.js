/**
 * 10-Foot Console Dashboard view for Locadoura (#tv-view).
 * Features dynamic Hero Backdrop on D-Pad focus, horizontal Console Rail,
 * windowed Game Grid (only cards focusable), and live Controller Button Prompts.
 */

import { getSettings, updateSetting, isFavorite } from "./state.js";
import {
  getGameKey,
  getArtUrls,
  formatBytes,
} from "./catalog.js";
import { getButtonLabels } from "./gamepad.js";
import { playConfirm, playSwitch } from "./sound.js";
import { focusFirst, focusElement } from "./focus-engine.js";
import { matchLocalRom } from "./library-sync.js";

const TV_PAGE_SIZE = 60;

let consolesList = [];
let activeConsoleId = "snes";
let gamesList = [];
let currentPage = 1;
let isLoading = false;
let selectedHeroGame = null;
let heroArtTimer = null;

let callbacks = {
  onSelectConsole: null,
  onOpenSidebar: null,
  onOpenSearchModal: null,
  onOpenGameModal: null,
  onToast: null,
};

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function getEls() {
  return {
    activeConsolePill: document.getElementById("tv-active-console"),
    gamepadIndicator: document.getElementById("tv-gamepad-indicator"),
    searchBtn: document.getElementById("tv-search-btn"),
    menuBtn: document.getElementById("tv-menu-btn"),
    heroBackdrop: document.getElementById("tv-hero-backdrop"),
    heroBadges: document.getElementById("tv-hero-badges"),
    heroTitle: document.getElementById("tv-hero-title"),
    heroSubtitle: document.getElementById("tv-hero-subtitle"),
    heroPrimaryBtn: document.getElementById("tv-hero-primary-btn"),
    heroSecondaryBtn: document.getElementById("tv-hero-secondary-btn"),
    heroCover: document.getElementById("tv-hero-cover"),
    heroPrint: document.getElementById("tv-hero-print"),
    consoleRail: document.getElementById("tv-console-rail"),
    shelfTitle: document.getElementById("tv-shelf-title"),
    shelfCount: document.getElementById("tv-shelf-count"),
    gameGrid: document.getElementById("tv-game-grid"),
    promptsList: document.getElementById("tv-prompts-list"),
  };
}

function ensureMobileSwitchButton() {
  // Auto-detection handles TV/Mobile mode seamlessly; no manual switch button needed.
  const existing = document.getElementById("tv-mode-mobile-btn");
  if (existing) existing.remove();
}

export function updateTvGamepadIndicator(connectedPads = []) {
  const { gamepadIndicator } = getEls();
  if (!gamepadIndicator) return;
  const isConnected = connectedPads.length > 0;
  gamepadIndicator.dataset.connected = isConnected ? "true" : "false";
  const label = gamepadIndicator.querySelector(".gp-label");
  if (label) {
    label.textContent = isConnected
      ? `Controle: ${connectedPads[0].id.slice(0, 24)}`
      : "Controle: Teclado";
  }
}

export function renderTvPrompts() {
  const { promptsList } = getEls();
  const labels = getButtonLabels(getSettings().gamepadStyle);

  // Inject console button glyphs directly into TV action buttons so shortcuts are always visible on screen
  const backBtn = document.getElementById("tv-dashboard-back");
  if (backBtn) backBtn.innerHTML = `${labels.back.svg}<span>Central</span>`;
  const searchBtn = document.getElementById("tv-search-btn");
  if (searchBtn) searchBtn.innerHTML = `${labels.search.svg}<span>Buscar</span>`;
  const libBtn = document.getElementById("tv-library-btn");
  if (libBtn) libBtn.innerHTML = `${labels.library.svg}<span>Biblioteca</span>`;
  const dlBtn = document.getElementById("tv-downloads-btn");
  if (dlBtn) {
    const existingBadge = document.getElementById("tv-dl-badge");
    const badgeText = existingBadge ? existingBadge.textContent : "0";
    const badgeHidden = existingBadge ? existingBadge.classList.contains("hidden") : true;
    dlBtn.innerHTML = `${labels.downloads.svg}<span>Downloads</span><span id="tv-dl-badge" class="badge badge-accent${badgeHidden ? " hidden" : ""}">${escapeHtml(badgeText)}</span>`;
  }
  const menuBtn = document.getElementById("tv-menu-btn");
  if (menuBtn) menuBtn.innerHTML = `${labels.menu.svg}<span>Opções</span>`;
  const heroPrimaryBtn = document.getElementById("tv-hero-primary-btn");
  if (heroPrimaryBtn && !heroPrimaryBtn.querySelector(".gp-icon")) {
    heroPrimaryBtn.innerHTML = `${labels.confirm.svg}<span>${heroPrimaryBtn.textContent.trim() || "Baixar / Detalhes"}</span>`;
  }

  if (!promptsList) return;
  promptsList.innerHTML = `
    <div class="tv-prompt-item">
      ${labels.confirm.svg}
      <span>Selecionar</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.back.svg}
      <span>Voltar</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.search.svg}
      <span>Buscar</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.library.svg}
      <span>Biblioteca</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.downloads.svg}
      <span>Downloads</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.menu.svg}
      <span>Opções</span>
    </div>
    <div class="tv-prompt-item">
      ${labels.prevTab.svg}
      ${labels.nextTab.svg}
      <span>Console</span>
    </div>
  `;
}

export function updateTvHero(game) {
  const {
    heroBackdrop,
    heroBadges,
    heroTitle,
    heroSubtitle,
    heroCover,
    heroPrint,
  } = getEls();

  for (const id of ["tv-hero-primary-btn", "tv-hero-secondary-btn"]) {
    const button = document.getElementById(id);
    if (button) button.disabled = !game;
  }
  const sameGame = selectedHeroGame === game;
  selectedHeroGame = game || null;

  if (!game) {
    if (heroBackdrop) heroBackdrop.style.backgroundImage = "";
    if (heroBadges) heroBadges.innerHTML = "";
    if (heroTitle) heroTitle.textContent = "Selecione um Jogo";
    if (heroSubtitle) heroSubtitle.textContent = "Use o D-Pad ou setas para navegar pela estante.";
    if (heroCover) heroCover.classList.add("hidden");
    if (heroPrint) heroPrint.classList.add("hidden");
    return;
  }

  const key = getGameKey(activeConsoleId, game);
  const fav = isFavorite(activeConsoleId, key);
  const art = getArtUrls(activeConsoleId, game);
  const consoleMeta = consolesList.find((c) => c.id === activeConsoleId);

  const bgUrl = art.snapUrl || art.boxUrl || "";
  if (heroArtTimer) {
    clearTimeout(heroArtTimer);
    heroArtTimer = null;
  }
  if (!sameGame) {
    // Debounce remote artwork updates so holding D-Pad across the TV grid stays silky smooth.
    heroArtTimer = setTimeout(() => {
      heroArtTimer = null;
      if (selectedHeroGame !== game) return;
      if (heroBackdrop) {
        heroBackdrop.style.backgroundImage = bgUrl ? `url("${bgUrl}")` : "";
      }
      if (heroCover) {
        if (art.boxUrl) {
          if (heroCover.getAttribute("src") !== art.boxUrl) heroCover.src = art.boxUrl;
          heroCover.classList.remove("hidden");
          heroCover.onerror = () => heroCover.classList.add("hidden");
        } else {
          heroCover.classList.add("hidden");
        }
      }
      if (heroPrint) {
        if (art.snapUrl) {
          if (heroPrint.getAttribute("src") !== art.snapUrl) heroPrint.src = art.snapUrl;
          heroPrint.classList.remove("hidden");
          heroPrint.onerror = () => heroPrint.classList.add("hidden");
        } else {
          heroPrint.classList.add("hidden");
        }
      }
    }, 110);
  }

  if (heroBadges) {
    const badges = [];
    const localMatch = matchLocalRom(activeConsoleId, game);
    if (localMatch) {
      const isMod = localMatch.kind === "mod" || localMatch.kind === "variant";
      badges.push(`<span class="badge ${isMod ? "badge-warn" : "badge-accent"}">${isMod ? "✓ Mod / PT-BR" : "✓ Baixado"}</span>`);
    }
    if (consoleMeta) badges.push(`<span class="badge badge-accent">${escapeHtml(consoleMeta.shortName || consoleMeta.name)}</span>`);
    if (game.region) badges.push(`<span class="badge">${escapeHtml(game.region)}</span>`);
    if (game.year) badges.push(`<span class="badge">${escapeHtml(game.year)}</span>`);
    if (game.size) badges.push(`<span class="badge">${formatBytes(game.size)}</span>`);
    if (fav) badges.push(`<span class="badge badge-accent">★ Favorito</span>`);
    heroBadges.innerHTML = badges.join("");
  }

  if (heroTitle) {
    heroTitle.textContent = game.title || key;
  }

  if (heroSubtitle) {
    const parts = [];
    if (game.maker) parts.push(game.maker);
    if (game.rom) parts.push(`ROM: ${game.rom}`);
    else if (game.file) parts.push(game.file);
    if (game.cloneof) parts.push(`Clone de ${game.cloneof}`);
    heroSubtitle.textContent = parts.join(" • ") || "Pronto para baixar ou ver detalhes.";
  }
}

export function updateTvHeroForElement(el) {
  if (!el || !el.dataset || el.dataset.tvGameIdx === undefined) return;
  const idx = Number(el.dataset.tvGameIdx);
  const game = gamesList[idx];
  if (game) {
    updateTvHero(game);
  }
}

export function renderTvConsoleRail() {
  const { consoleRail, activeConsolePill, shelfTitle } = getEls();
  const activeMeta = consolesList.find((c) => c.id === activeConsoleId);

  if (activeConsolePill) {
    activeConsolePill.textContent = activeMeta ? activeMeta.name : activeConsoleId;
  }
  if (shelfTitle) {
    shelfTitle.textContent = activeMeta ? `Estante — ${activeMeta.shortName || activeMeta.name}` : "Catálogo";
  }

  if (!consoleRail) return;
  const labels = getButtonLabels(getSettings().gamepadStyle);
  consoleRail.innerHTML =
    `<span class="tv-rail-hint" aria-hidden="true">${labels.prevTab.svg}</span>` +
    consolesList
      .map((c) => {
        const active = c.id === activeConsoleId;
        return `
          <button
            type="button"
            tabindex="-1"
            class="tv-console-card ${active ? "is-active" : ""}"
            data-tv-console-id="${escapeHtml(c.id)}"
          >
            <span>${escapeHtml(c.shortName || c.name)}</span>
            <span class="badge">${Number(c.count || 0).toLocaleString("pt-BR")}</span>
          </button>
        `;
      })
      .join("") +
    `<span class="tv-rail-hint" aria-hidden="true">${labels.nextTab.svg}</span>`;
}

export function renderTvGameGrid() {
  const { shelfCount, gameGrid } = getEls();
  if (!gameGrid) return;

  if (isLoading) {
    if (shelfCount) shelfCount.textContent = "Carregando...";
    gameGrid.innerHTML = `<div class="tv-game-card" style="padding:20px;"><strong>Carregando títulos...</strong></div>`;
    return;
  }

  const focusedIndex = gameGrid.contains(document.activeElement)
    ? document.activeElement.dataset.tvGameIdx : undefined;
  const total = gamesList.length;
  const totalPages = Math.max(1, Math.ceil(total / TV_PAGE_SIZE));
  if (currentPage > totalPages) currentPage = totalPages;

  if (shelfCount) {
    shelfCount.textContent = `${total.toLocaleString("pt-BR")} títulos` + (totalPages > 1 ? ` • Página ${currentPage}/${totalPages}` : "");
  }

  if (total === 0) {
    gameGrid.innerHTML = `<div class="tv-game-card" style="padding:20px;"><strong>Nenhum título encontrado</strong></div>`;
    updateTvHero(null);
    return;
  }

  const startIdx = (currentPage - 1) * TV_PAGE_SIZE;
  const pageItems = gamesList.slice(startIdx, startIdx + TV_PAGE_SIZE);

  // CRITICAL: Only the .tv-game-card button itself has data-focusable="true".
  // Inner badges, titles, and images MUST NOT have data-focusable="true".
  const cardsHtml = pageItems
    .map((game, i) => {
      const globalIdx = startIdx + i;
      const key = getGameKey(activeConsoleId, game);
      const art = getArtUrls(activeConsoleId, game);
      const thumbUrl = art.boxUrl || art.snapUrl;
      const tag = game.region || game.year || formatBytes(game.size || 0);
      const localMatch = matchLocalRom(activeConsoleId, game);
      const localPill = localMatch
        ? `<span class="badge ${localMatch.kind === "mod" || localMatch.kind === "variant" ? "badge-warn" : "badge-accent"}">✓</span>`
        : "";

      return `
        <button
          type="button"
          class="tv-game-card"
          data-tv-game-idx="${globalIdx}"
          data-focusable="true"
          data-focus-group="tv-games"
        >
          <div class="tv-game-thumb-wrap">
            ${
              thumbUrl
                ? `<img class="tv-game-thumb" style="object-fit:contain;" src="${escapeHtml(thumbUrl)}" alt="" loading="lazy" decoding="async" onerror="this.style.display='none'" />`
                : `<div class="tv-game-thumb-placeholder" aria-hidden="true">🎮</div>`
            }
          </div>
          <div class="tv-game-body">
            <h3 class="tv-game-title">${escapeHtml(game.title || key)}</h3>
            <div class="tv-game-meta">
              ${localPill}
              <span class="badge">${escapeHtml(tag)}</span>
              ${game.size ? `<span class="badge">${formatBytes(game.size)}</span>` : ""}
            </div>
          </div>
        </button>
      `;
    })
    .join("");

  const paginationHtml =
    totalPages > 1
      ? `
      <div style="grid-column:1/-1;display:flex;align-items:center;justify-content:center;gap:14px;padding:12px 0;">
        ${
          currentPage > 1
            ? `<button type="button" class="tv-top-btn" data-tv-page="prev" data-focusable="true" data-focus-group="tv-games">◀ Página Anterior</button>`
            : ""
        }
        <span class="badge">Página ${currentPage} de ${totalPages}</span>
        ${
          currentPage < totalPages
            ? `<button type="button" class="tv-top-btn" data-tv-page="next" data-focusable="true" data-focus-group="tv-games">Próxima Página ▶</button>`
            : ""
        }
      </div>
    `
      : "";

  gameGrid.innerHTML = cardsHtml + paginationHtml;
  const retained = focusedIndex === undefined ? null
    : gameGrid.querySelector(`[data-tv-game-idx="${focusedIndex}"]`);
  if (retained) focusElement(retained, { scroll: false, silent: true });
  updateTvHero(retained ? gamesList[Number(focusedIndex)]
    : pageItems.includes(selectedHeroGame) ? selectedHeroGame : pageItems[0]);
}

export function setTvData({ consoles, consoleId, games, loading }) {
  if (Array.isArray(consoles)) consolesList = consoles;
  if (typeof consoleId === "string") activeConsoleId = consoleId;
  if (Array.isArray(games)) {
    gamesList = games;
    currentPage = 1;
  }
  if (typeof loading === "boolean") isLoading = loading;
  if (isLoading) updateTvHero(null);

  renderTvConsoleRail();
  renderTvGameGrid();
}

export function cycleTvConsole(delta) {
  if (consolesList.length === 0) return;
  const idx = consolesList.findIndex((c) => c.id === activeConsoleId);
  const nextIdx = (Math.max(0, idx) + delta + consolesList.length) % consolesList.length;
  const nextConsole = consolesList[nextIdx];
  if (nextConsole && nextConsole.id !== activeConsoleId) {
    playSwitch();
    callbacks.onSelectConsole?.(nextConsole.id);
  }
}

function bindEvents() {
  document.getElementById("tv-dashboard-back")?.addEventListener("click", () => {
    if (typeof window.__lemuroidExit === "function") window.__lemuroidExit();
    else window.history.back();
  });
  const {
    searchBtn,
    menuBtn,
    heroPrimaryBtn,
    heroSecondaryBtn,
    consoleRail,
    gameGrid,
  } = getEls();

  if (searchBtn) {
    searchBtn.addEventListener("click", () => {
      playSwitch();
      callbacks.onOpenSearchModal?.();
    });
  }

  if (menuBtn) {
    menuBtn.addEventListener("click", () => {
      playSwitch();
      callbacks.onOpenSidebar?.();
    });
  }

  if (heroPrimaryBtn) {
    heroPrimaryBtn.addEventListener("click", () => {
      if (!selectedHeroGame) return;
      playConfirm();
      callbacks.onOpenGameModal?.(activeConsoleId, selectedHeroGame);
    });
  }

  if (heroSecondaryBtn) {
    heroSecondaryBtn.addEventListener("click", () => {
      if (!selectedHeroGame) return;
      playConfirm();
      callbacks.onOpenGameModal?.(activeConsoleId, selectedHeroGame);
    });
  }

  if (consoleRail) {
    consoleRail.addEventListener("click", (e) => {
      const card = e.target.closest("[data-tv-console-id]");
      if (!card) return;
      const nextId = card.dataset.tvConsoleId;
      if (nextId && nextId !== activeConsoleId) {
        playSwitch();
        callbacks.onSelectConsole?.(nextId);
      }
    });
  }

  if (gameGrid) {
    gameGrid.addEventListener("click", (e) => {
      const pageBtn = e.target.closest("[data-tv-page]");
      if (pageBtn) {
        playSwitch();
        currentPage += pageBtn.dataset.tvPage === "next" ? 1 : -1;
        renderTvGameGrid();
        focusFirst(gameGrid, { silent: true });
        return;
      }

      const card = e.target.closest("[data-tv-game-idx]");
      if (!card) return;
      const idx = Number(card.dataset.tvGameIdx);
      const game = gamesList[idx];
      if (!game) return;
      playConfirm();
      updateTvHero(game);
      callbacks.onOpenGameModal?.(activeConsoleId, game);
    });

    gameGrid.addEventListener("focusin", (e) => {
      const card = e.target.closest("[data-tv-game-idx]");
      if (card) updateTvHeroForElement(card);
    });
  }
}

export function initTvView(opts = {}) {
  callbacks = { ...callbacks, ...opts };
  ensureMobileSwitchButton();
  renderTvPrompts();
  bindEvents();
}
