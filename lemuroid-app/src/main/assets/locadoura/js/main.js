/**
 * Main Orchestrator for Locadoura.
 * Bootstraps state, themes, sound, catalog, sidebar, mobile/TV views,
 * gamepad + D-Pad focus engine, search modal, game detail modal, and toasts.
 */

import {
  getSettings,
  subscribeSettings,
  applyThemeAndMode,
  resolveEffectiveMode,
  isFavorite,
  toggleFavorite,
} from "./state.js";
import {
  loadConsoles,
  loadConsoleGames,
  loadConsoleArt,
  getGameKey,
  getDownloadUrl,
  getArtUrls,
  formatBytes,
} from "./catalog.js";
import {
  syncLocalLibrary,
  subscribeLibrarySync,
  matchLocalRom,
  getUnifiedLibrary,
  deleteLocalRom,
} from "./library-sync.js";
import {
  initDownloadQueueBridge,
  enqueueDownload,
  getDownloadQueue,
  getActiveDownloadCount,
  cancelDownloadItem,
  clearCompletedDownloads,
  subscribeDownloadQueue,
} from "./download-queue.js";
import {
  playMove,
  playConfirm,
  playBack,
  playSwitch,
  playError,
} from "./sound.js";
import {
  startInputLoop,
  onGamepadConnectionChange,
  getConnectedGamepads,
  triggerRumble,
} from "./gamepad.js";
import {
  move as focusMove,
  refreshFocus,
  focusFirst,
  getCurrentFocused,
  clearFocus,
} from "./focus-engine.js";
import {
  initSidebar,
  openSidebar,
  closeSidebar,
  isSidebarOpen,
  cycleSidebarTab,
} from "./sidebar.js";
import {
  initMobileView,
  setMobileData,
  renderMobileGames,
  invalidateMobileLibrary,
} from "./views-mobile.js";
import {
  initTvView,
  setTvData,
  renderTvPrompts,
  renderTvGameGrid,
  updateTvGamepadIndicator,
  cycleTvConsole,
} from "./views-tv.js";

let consoles = [];
let currentConsoleId = "snes";
let currentGames = [];
let currentEffectiveMode = "mobile";
let lastLayerDismissedAt = -Infinity;

let searchModalRegion = "all";
let searchModalResults = [];
let activeGameModal = null;

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export async function downloadGame(consoleId, game) {
  return enqueueDownload(consoleId, game, { onToast: showToast });
}

export function showToast(message, durationMs = 3000) {
  const container = document.getElementById("toast-container");
  if (!container || !message) return;

  const toast = document.createElement("div");
  toast.className = "toast";
  toast.textContent = message;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transition = "opacity 0.2s ease";
    setTimeout(() => toast.remove(), 220);
  }, durationMs);
}

function syncAppMode(settings = getSettings()) {
  applyThemeAndMode(settings);
  const nextMode = resolveEffectiveMode(settings.mode);
  const appEl = document.getElementById("app");
  if (appEl) {
    appEl.dataset.mode = nextMode;
  }

  const changed = nextMode !== currentEffectiveMode;
  currentEffectiveMode = nextMode;

  if (changed) {
    if (nextMode === "tv") {
      refreshFocus({ silent: true });
    } else {
      clearFocus();
    }
  }
}

let consoleLoadSequence = 0;

async function selectConsole(consoleId) {
  const loadSequence = ++consoleLoadSequence;
  currentConsoleId = consoleId;
  setMobileData({ consoles, consoleId, loading: true });
  setTvData({ consoles, consoleId, loading: true });

  try {
    const [bundle] = await Promise.all([
      loadConsoleGames(consoleId),
      loadConsoleArt(consoleId),
    ]);
    if (loadSequence !== consoleLoadSequence) return;
    currentGames = Array.isArray(bundle?.games) ? bundle.games : [];

    setMobileData({ consoles, consoleId, games: currentGames, loading: false });
    setTvData({ consoles, consoleId, games: currentGames, loading: false });

    if (currentEffectiveMode === "tv" && !isSidebarOpen() && !isAnyModalOpen()) {
      const gridEl = document.getElementById("tv-game-grid");
      focusFirst(gridEl, { silent: true });
    }
  } catch (err) {
    if (loadSequence !== consoleLoadSequence) return;
    playError();
    currentGames = [];
    setMobileData({ consoles, consoleId, games: [], loading: false });
    setTvData({ consoles, consoleId, games: [], loading: false });
    showToast(`Erro ao carregar ${consoleId}: ${err?.message || "falha de rede"}`);
  }
}

/* ==========================================================================
   MODALS: SEARCH & GAME DETAILS
   ========================================================================== */

function isSearchModalOpen() {
  const el = document.getElementById("search-modal");
  return Boolean(el && !el.classList.contains("hidden"));
}

function isGameModalOpen() {
  const el = document.getElementById("game-modal");
  return Boolean(el && !el.classList.contains("hidden"));
}

function isUtilityModalOpen() {
  const el = document.getElementById("utility-modal");
  return Boolean(el && !el.classList.contains("hidden"));
}

function isConfirmModalOpen() {
  const el = document.getElementById("confirm-modal");
  return Boolean(el && !el.classList.contains("hidden"));
}

function isAnyModalOpen() {
  return isSearchModalOpen() || isGameModalOpen() || isUtilityModalOpen() || isConfirmModalOpen();
}

function renderSearchModalResults() {
  const input = document.getElementById("search-modal-input");
  const resultsEl = document.getElementById("search-modal-results");
  if (!resultsEl) return;

  const q = (input?.value || "").trim().toLowerCase();
  const matches = [];
  for (let i = 0; i < currentGames.length; i++) {
    const g = currentGames[i];
    if (searchModalRegion !== "all" && g.region !== searchModalRegion) continue;
    if (q) {
      const hay = `${g.title || ""} ${g.rom || ""} ${g.file || ""} ${g.maker || ""}`.toLowerCase();
      if (!hay.includes(q)) continue;
    }
    matches.push(g);
    if (matches.length >= 80) break;
  }

  searchModalResults = matches;

  if (matches.length === 0) {
    resultsEl.innerHTML = `<p class="sb-desc">Nenhum título encontrado para "${escapeHtml(q)}".</p>`;
    return;
  }

  resultsEl.innerHTML = matches
    .map((g, idx) => {
      const key = getGameKey(currentConsoleId, g);
      const sub = [g.region, g.year, g.maker, g.size ? formatBytes(g.size) : ""]
        .filter(Boolean)
        .join(" • ");
      return `
        <button
          type="button"
          class="modal-result-row"
          data-search-idx="${idx}"
          data-focusable="true"
          data-focus-group="modal-search"
        >
          <div style="min-width:0;">
            <strong>${escapeHtml(g.title || key)}</strong>
            <div class="sb-desc">${escapeHtml(sub)}</div>
          </div>
          <span class="badge badge-accent">Abrir</span>
        </button>
      `;
    })
    .join("");
}

function openSearchModal(initialQuery = "") {
  const modal = document.getElementById("search-modal");
  const input = document.getElementById("search-modal-input");
  const titleEl = document.getElementById("search-modal-title");
  const filtersEl = document.getElementById("search-modal-filters");
  if (!modal) return;

  modal.setAttribute("data-focus-scope", "modal");
  modal.classList.remove("hidden");
  searchModalRegion = "all";

  if (titleEl) {
    const meta = consoles.find((c) => c.id === currentConsoleId);
    titleEl.textContent = initialQuery
      ? `Versões — ${meta ? meta.shortName || meta.name : currentConsoleId}`
      : `Buscar em ${meta ? meta.shortName || meta.name : currentConsoleId}`;
  }

  if (filtersEl) {
    const regions = new Set();
    for (const g of currentGames) {
      if (g.region) regions.add(g.region);
    }
    const regionList = ["all", ...Array.from(regions).sort().slice(0, 8)];
    filtersEl.innerHTML = regionList
      .map(
        (r) => `
        <button
          type="button"
          class="sb-pill-btn ${r === searchModalRegion ? "is-active" : ""}"
          data-search-region="${escapeHtml(r)}"
          data-focusable="true"
          data-focus-group="modal-search"
        >${r === "all" ? "Todas" : escapeHtml(r)}</button>
      `
      )
      .join("");
  }

  if (input) {
    input.value = typeof initialQuery === "string" ? initialQuery : "";
    input.focus();
  }
  renderSearchModalResults();
}

function closeSearchModal() {
  const modal = document.getElementById("search-modal");
  if (!modal || modal.classList.contains("hidden")) return;
  modal.classList.add("hidden");
  modal.removeAttribute("data-focus-scope");
  if (currentEffectiveMode === "tv") refreshFocus({ silent: true });
}

function findMameClones(game) {
  if (currentConsoleId !== "mame" || !game) return [];
  const parentRom = game.cloneof || game.rom;
  if (!parentRom) return [];
  return currentGames.filter(
    (g) => g.rom !== game.rom && (g.cloneof === parentRom || g.rom === parentRom)
  );
}

export function openGameModal(consoleId, game) {
  const modal = document.getElementById("game-modal");
  const titleEl = document.getElementById("game-modal-title");
  const bodyEl = document.getElementById("game-modal-body");
  if (!modal || !bodyEl || !game) return;

  activeGameModal = { consoleId, game };
  modal.setAttribute("data-focus-scope", "modal");
  modal.classList.remove("hidden");

  const key = getGameKey(consoleId, game);
  const fav = isFavorite(consoleId, key);
  const art = getArtUrls(consoleId, game);
  const dlUrl = getDownloadUrl(consoleId, game);
  const clones = consoleId === "mame" ? findMameClones(game) : [];

  if (titleEl) {
    titleEl.textContent = game.title || key;
  }

  const metaItems = [
    game.rom ? `ROM: ${game.rom}` : null,
    game.file ? `Arquivo: ${game.file}` : null,
    game.region ? `Região: ${game.region}` : null,
    game.year ? `Ano: ${game.year}` : null,
    game.maker ? `Fabricante: ${game.maker}` : null,
    game.players ? `Jogadores: ${game.players}` : null,
    game.status ? `Status: ${game.status}` : null,
    game.size ? `Tamanho: ${formatBytes(game.size)}` : null,
  ].filter(Boolean);

  const localMatch = matchLocalRom(consoleId, game);
  const localBanner = localMatch
    ? `<p class="sb-desc" style="color:var(--accent);font-weight:700;margin-bottom:8px;">✓ ${
        localMatch.kind === "mod" || localMatch.kind === "variant"
          ? "Já baixado (Mod / Tradução)"
          : "Já baixado no aparelho"
      }: <code>${escapeHtml(localMatch.localName)}</code></p>`
    : "";

  bodyEl.innerHTML = `
    <div class="game-modal-hero">
      ${
        art.boxUrl || art.snapUrl
          ? `<div class="game-modal-art-pair">
              ${art.boxUrl ? `<img style="object-fit:contain;" src="${escapeHtml(art.boxUrl)}" alt="Capa" onerror="this.style.display='none'" />` : ""}
              ${art.snapUrl ? `<img style="object-fit:contain;" src="${escapeHtml(art.snapUrl)}" alt="Print" onerror="this.style.display='none'" />` : ""}
            </div>`
          : ""
      }
      <div style="flex:1;min-width:220px;">
        ${localBanner}
        <div class="mobile-game-meta" style="margin-bottom:10px;">
          ${metaItems.map((m) => `<span class="badge">${escapeHtml(m)}</span>`).join("")}
        </div>
        <p class="sb-desc" style="word-break:break-all;">Destino: <code>${escapeHtml(getSettings().consoleFolders?.[consoleId] || `${getSettings().folders.games}/${consoleId}`)}</code></p>
      </div>
    </div>

    <div class="game-modal-actions">
      <button
        type="button"
        class="tv-primary-btn"
        data-modal-action="download"
        data-focusable="true"
        data-focus-group="modal-game"
      >⬇ Baixar Agora</button>
      <button
        type="button"
        class="tv-secondary-btn"
        data-modal-action="copy"
        data-focusable="true"
        data-focus-group="modal-game"
      >🔗 Copiar Link</button>
      <button
        type="button"
        class="tv-secondary-btn"
        data-modal-action="fav"
        data-focusable="true"
        data-focus-group="modal-game"
      >${fav ? "★ Remover Favorito" : "☆ Favoritar"}</button>
      ${
        localMatch
          ? `<button
              type="button"
              class="tv-secondary-btn"
              data-modal-action="delete"
              data-focusable="true"
              data-focus-group="modal-game"
              style="color:#f87171;border-color:rgba(248,113,113,0.4);"
            >🗑️ Excluir do Aparelho</button>`
          : ""
      }
    </div>

    ${
      clones.length > 0
        ? `
      <div>
        <h3 class="sb-group-title" style="margin-bottom:8px;">Versões & Clones (${clones.length})</h3>
        <div class="game-modal-clones-list">
          ${clones
            .slice(0, 25)
            .map(
              (c) => `
            <button
              type="button"
              class="clone-row"
              data-clone-rom="${escapeHtml(c.rom)}"
              data-focusable="true"
              data-focus-group="modal-game"
            >
              <span><strong>${escapeHtml(c.title || c.rom)}</strong> <span class="sb-desc">(${escapeHtml(c.rom)})</span></span>
              <span class="badge">${formatBytes(c.size || 0)}</span>
            </button>
          `
            )
            .join("")}
        </div>
      </div>
    `
        : ""
    }
  `;

  if (currentEffectiveMode === "tv") {
    focusFirst(modal, { silent: true });
  }
}

function closeGameModal() {
  const modal = document.getElementById("game-modal");
  if (!modal || modal.classList.contains("hidden")) return;
  modal.classList.add("hidden");
  modal.removeAttribute("data-focus-scope");
  activeGameModal = null;
  if (currentEffectiveMode === "tv") refreshFocus({ silent: true });
}

let activeUtilityTab = null; // "library" | "downloads" | null
let tvLibraryCache = [];
let pendingDeleteAction = null;

function closeConfirmModal() {
  const modal = document.getElementById("confirm-modal");
  if (!modal) return;
  modal.classList.add("hidden");
  pendingDeleteAction = null;
  if (currentEffectiveMode === "tv") refreshFocus({ silent: true });
}

export function confirmDeleteGame(consoleId, game, match, onDeleted) {
  const modal = document.getElementById("confirm-modal");
  const msgEl = document.getElementById("confirm-modal-message");
  if (!modal || !match) return;

  const title = game?.title || match.localName;
  if (msgEl) {
    msgEl.innerHTML = `Tem certeza que deseja excluir <strong>${escapeHtml(title)}</strong> (<code>${escapeHtml(match.localName)}</code>) do aparelho?<br><br>Esta ação remove o arquivo da pasta e não pode ser desfeita.`;
  }

  pendingDeleteAction = async () => {
    try {
      const ok = await deleteLocalRom(consoleId, match);
      if (ok) {
        playConfirm();
        showToast(`Excluído: ${match.localName}`);
        if (typeof onDeleted === "function") onDeleted();
      } else {
        playError();
        showToast("Não foi possível excluir o arquivo.");
      }
    } catch (err) {
      playError();
      showToast(`Erro ao excluir: ${err?.message || "falha nativa"}`);
    }
  };

  modal.classList.remove("hidden");
  focusFirst(modal, { silent: true });
}

function closeUtilityModal() {
  const modal = document.getElementById("utility-modal");
  if (!modal) return;
  modal.classList.add("hidden");
  activeUtilityTab = null;
  if (currentEffectiveMode === "tv") refreshFocus({ silent: true });
}

function renderUtilityDownloads() {
  const titleEl = document.getElementById("utility-modal-title");
  const bodyEl = document.getElementById("utility-modal-body");
  if (!bodyEl) return;
  if (titleEl) titleEl.textContent = "Downloads Ativos e Recentes";

  const queue = getDownloadQueue();
  const statusMap = {
    queued: "Na fila",
    downloading: "Baixando",
    completed: "Concluído",
    failed: "Erro",
    cancelled: "Cancelado",
  };

  if (queue.length === 0) {
    bodyEl.innerHTML = `<p class="sb-desc">Nenhum download na fila. Selecione um jogo e clique em "Baixar Agora".</p>`;
    return;
  }

  bodyEl.innerHTML = `
    <div style="display:flex;justify-content:flex-end;margin-bottom:10px;">
      <button type="button" class="sb-btn-secondary" data-util-action="clear-dl" data-focusable="true" data-focus-group="modal-utility">Limpar Concluídos</button>
    </div>
    <div class="game-modal-clones-list">
      ${queue
        .map((item) => {
          const pct = item.totalBytes > 0
            ? Math.min(100, Math.round((item.bytesDownloaded / item.totalBytes) * 100))
            : (item.status === "completed" ? 100 : 0);
          const isActive = item.status === "queued" || item.status === "downloading";
          return `
            <div class="clone-row" style="flex-direction:column;align-items:stretch;gap:6px;">
              <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
                <strong>${escapeHtml(item.title)}</strong>
                <span class="badge ${item.status === "completed" ? "badge-accent" : ""}">${escapeHtml(statusMap[item.status] || item.status)}</span>
              </div>
              <div class="dl-progress-track"><div class="dl-progress-fill" style="width:${pct}%;"></div></div>
              <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
                <span class="sb-desc">${escapeHtml(item.error || `${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes || item.bytesDownloaded)} (${pct}%)`)}</span>
                ${
                  isActive
                    ? `<button type="button" class="sb-btn-secondary" data-util-action="cancel-dl" data-dl-id="${escapeHtml(item.id)}" data-focusable="true" data-focus-group="modal-utility">✕ Cancelar</button>`
                    : ""
                }
              </div>
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

async function openUtilityModal(tab) {
  const modal = document.getElementById("utility-modal");
  const titleEl = document.getElementById("utility-modal-title");
  const bodyEl = document.getElementById("utility-modal-body");
  if (!modal || !bodyEl) return;

  activeUtilityTab = tab;
  modal.classList.remove("hidden");

  if (tab === "downloads") {
    renderUtilityDownloads();
    focusFirst(modal, { silent: true });
    return;
  }

  if (titleEl) titleEl.textContent = "Minha Biblioteca (Todos os Consoles)";
  bodyEl.innerHTML = `<p class="sb-desc">Sincronizando biblioteca local...</p>`;
  const res = await getUnifiedLibrary(consoles, loadConsoleGames, loadConsoleArt);
  tvLibraryCache = res.items || [];

  if (tvLibraryCache.length === 0) {
    bodyEl.innerHTML = `
      <p class="sb-desc">Nenhum jogo encontrado nas pastas configuradas. Configure em Opções → Pastas.</p>
      <button type="button" class="sb-btn-secondary" data-util-action="sync-lib" data-focusable="true" data-focus-group="modal-utility" style="margin-top:10px;">🔄 Sincronizar Agora</button>
    `;
    focusFirst(modal, { silent: true });
    return;
  }

  bodyEl.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;gap:8px;">
      <span class="sb-desc">${tvLibraryCache.length} jogo(s) detectado(s)</span>
      <button type="button" class="sb-btn-secondary" data-util-action="sync-lib" data-focusable="true" data-focus-group="modal-utility">🔄 Sincronizar</button>
    </div>
    <div class="game-modal-clones-list">
      ${tvLibraryCache
        .slice(0, 80)
        .map((entry, idx) => {
          const isMod = entry.match.kind === "mod" || entry.match.kind === "variant";
          return `
            <button type="button" class="clone-row" data-util-lib-idx="${idx}" data-focusable="true" data-focus-group="modal-utility" style="text-align:left;">
              <div>
                <strong>${escapeHtml(entry.game.title || entry.match.localName)}</strong>
                <div class="sb-desc">${escapeHtml(entry.consoleName)} • ${escapeHtml(entry.match.localName)}</div>
              </div>
              <span class="badge ${isMod ? "badge-warn" : "badge-accent"}">${isMod ? "✓ Mod/BR" : "✓ Baixado"}</span>
            </button>
          `;
        })
        .join("")}
    </div>
  `;
  focusFirst(modal, { silent: true });
}

function bindModalEvents() {
  const searchModal = document.getElementById("search-modal");
  const searchClose = document.getElementById("search-modal-close");
  const searchInput = document.getElementById("search-modal-input");
  const searchFilters = document.getElementById("search-modal-filters");
  const searchResults = document.getElementById("search-modal-results");

  if (searchClose) {
    searchClose.addEventListener("click", () => {
      playBack();
      closeSearchModal();
    });
  }
  if (searchModal) {
    searchModal.addEventListener("click", (e) => {
      if (e.target === searchModal) {
        playBack();
        closeSearchModal();
      }
    });
  }
  if (searchInput) {
    searchInput.addEventListener("input", renderSearchModalResults);
  }
  if (searchFilters) {
    searchFilters.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-search-region]");
      if (!btn) return;
      playSwitch();
      searchModalRegion = btn.dataset.searchRegion;
      for (const pill of searchFilters.querySelectorAll("[data-search-region]")) {
        pill.classList.toggle("is-active", pill === btn);
      }
      renderSearchModalResults();
    });
  }
  if (searchResults) {
    searchResults.addEventListener("click", (e) => {
      const row = e.target.closest("[data-search-idx]");
      if (!row) return;
      const game = searchModalResults[Number(row.dataset.searchIdx)];
      if (!game) return;
      playConfirm();
      closeSearchModal();
      openGameModal(currentConsoleId, game);
    });
  }

  const gameModal = document.getElementById("game-modal");
  const gameClose = document.getElementById("game-modal-close");
  const gameBody = document.getElementById("game-modal-body");

  if (gameClose) {
    gameClose.addEventListener("click", () => {
      playBack();
      closeGameModal();
    });
  }
  if (gameModal) {
    gameModal.addEventListener("click", (e) => {
      if (e.target === gameModal) {
        playBack();
        closeGameModal();
      }
    });
  }
  if (gameBody) {
    gameBody.addEventListener("click", async (e) => {
      if (!activeGameModal) return;
      const { consoleId, game } = activeGameModal;

      const cloneBtn = e.target.closest("[data-clone-rom]");
      if (cloneBtn) {
        const cloneGame = currentGames.find((g) => g.rom === cloneBtn.dataset.cloneRom);
        if (cloneGame) {
          playConfirm();
          openGameModal(consoleId, cloneGame);
        }
        return;
      }

      const actionBtn = e.target.closest("[data-modal-action]");
      if (!actionBtn) return;
      const act = actionBtn.dataset.modalAction;

      if (act === "download") {
        playConfirm();
        await downloadGame(consoleId, game);
      } else if (act === "copy") {
        playConfirm();
        const url = getDownloadUrl(consoleId, game);
        try {
          await navigator.clipboard.writeText(url);
          showToast("Link copiado para a área de transferência!");
        } catch {
          showToast("Não foi possível copiar o link.");
        }
      } else if (act === "fav") {
        playConfirm();
        const key = getGameKey(consoleId, game);
        const nowFav = toggleFavorite(consoleId, key);
        showToast(nowFav ? "Adicionado aos favoritos!" : "Removido dos favoritos.");
        openGameModal(consoleId, game);
        renderMobileGames();
      } else if (act === "delete") {
        const localMatch = matchLocalRom(consoleId, game);
        if (localMatch) {
          playSwitch();
          confirmDeleteGame(consoleId, game, localMatch, () => {
            openGameModal(consoleId, game);
            renderMobileGames();
          });
        }
      }
    });
  }

  const confirmModal = document.getElementById("confirm-modal");
  const confirmClose = document.getElementById("confirm-modal-close");
  const confirmCancel = document.getElementById("confirm-modal-cancel");
  const confirmOk = document.getElementById("confirm-modal-ok");
  if (confirmClose) confirmClose.addEventListener("click", () => { playBack(); closeConfirmModal(); });
  if (confirmCancel) confirmCancel.addEventListener("click", () => { playBack(); closeConfirmModal(); });
  if (confirmModal) {
    confirmModal.addEventListener("click", (e) => {
      if (e.target === confirmModal) { playBack(); closeConfirmModal(); }
    });
  }
  if (confirmOk) {
    confirmOk.addEventListener("click", async () => {
      const fn = pendingDeleteAction;
      closeConfirmModal();
      if (typeof fn === "function") await fn();
    });
  }

  const utilModal = document.getElementById("utility-modal");
  const utilClose = document.getElementById("utility-modal-close");
  const utilBody = document.getElementById("utility-modal-body");
  const tvLibBtn = document.getElementById("tv-library-btn");
  const tvDlBtn = document.getElementById("tv-downloads-btn");

  if (tvLibBtn) {
    tvLibBtn.addEventListener("click", () => {
      playConfirm();
      openUtilityModal("library");
    });
  }
  if (tvDlBtn) {
    tvDlBtn.addEventListener("click", () => {
      playConfirm();
      openUtilityModal("downloads");
    });
  }
  if (utilClose) {
    utilClose.addEventListener("click", () => {
      playBack();
      closeUtilityModal();
    });
  }
  if (utilModal) {
    utilModal.addEventListener("click", (e) => {
      if (e.target === utilModal) {
        playBack();
        closeUtilityModal();
      }
    });
  }
  if (utilBody) {
    utilBody.addEventListener("click", async (e) => {
      const libBtn = e.target.closest("[data-util-lib-idx]");
      if (libBtn) {
        const entry = tvLibraryCache[Number(libBtn.dataset.utilLibIdx)];
        if (entry) {
          playConfirm();
          closeUtilityModal();
          openGameModal(entry.consoleId, entry.game);
        }
        return;
      }
      const actBtn = e.target.closest("[data-util-action]");
      if (!actBtn) return;
      const act = actBtn.dataset.utilAction;
      if (act === "cancel-dl") {
        playSwitch();
        await cancelDownloadItem(actBtn.dataset.dlId);
        renderUtilityDownloads();
      } else if (act === "clear-dl") {
        playSwitch();
        clearCompletedDownloads();
        renderUtilityDownloads();
      } else if (act === "sync-lib") {
        playConfirm();
        showToast("Iniciando rescan da biblioteca do VideoGame...");
        await syncLocalLibrary({ triggerRescan: true });
        openUtilityModal("library");
      }
    });
  }
}

/* ==========================================================================
   GAMEPAD & D-PAD INPUT ROUTING
   ========================================================================== */

function handleInputAction(action) {
  const modalOpen = isAnyModalOpen();
  const sidebarOpen = isSidebarOpen();

  // In Mobile mode, only handle back/menu shortcuts when modals or sidebar are open
  if (currentEffectiveMode === "mobile" && !modalOpen && !sidebarOpen) {
    return;
  }

  switch (action) {
    case "up":
    case "down":
    case "left":
    case "right": {
      const moved = focusMove(action);
      if (moved) playMove();
      break;
    }
    case "confirm": {
      const cur = getCurrentFocused();
      if (cur) {
        triggerRumble(45, 0.2, 0.3);
        cur.click();
      }
      break;
    }
    case "back": {
      if (modalOpen || sidebarOpen) lastLayerDismissedAt = Date.now();
      if (isConfirmModalOpen()) {
        playBack();
        closeConfirmModal();
      } else if (isGameModalOpen()) {
        playBack();
        closeGameModal();
      } else if (isUtilityModalOpen()) {
        playBack();
        closeUtilityModal();
      } else if (isSearchModalOpen()) {
        playBack();
        closeSearchModal();
      } else if (sidebarOpen) {
        playBack();
        closeSidebar();
      } else if (Date.now() - lastLayerDismissedAt > 300 && typeof window.__lemuroidExit === "function") {
        playBack();
        window.__lemuroidExit();
      }
      break;
    }
    case "search": {
      if (!sidebarOpen && !isGameModalOpen()) {
        playSwitch();
        if (isSearchModalOpen()) closeSearchModal();
        else openSearchModal();
      }
      break;
    }
    case "menu": {
      if (!modalOpen) {
        playSwitch();
        if (sidebarOpen) closeSidebar();
        else openSidebar();
      }
      break;
    }
    case "prevTab": {
      if (sidebarOpen) cycleSidebarTab(-1);
      else if (!modalOpen && currentEffectiveMode === "tv") cycleTvConsole(-1);
      break;
    }
    case "nextTab": {
      if (sidebarOpen) cycleSidebarTab(1);
      else if (!modalOpen && currentEffectiveMode === "tv") cycleTvConsole(1);
      break;
    }
    case "library": {
      if (!sidebarOpen && !isGameModalOpen()) {
        playSwitch();
        if (isUtilityModalOpen() && activeUtilityTab === "library") closeUtilityModal();
        else openUtilityModal("library");
      }
      break;
    }
    case "downloads": {
      if (!sidebarOpen && !isGameModalOpen()) {
        playSwitch();
        if (isUtilityModalOpen() && activeUtilityTab === "downloads") closeUtilityModal();
        else openUtilityModal("downloads");
      }
      break;
    }
    default:
      break;
  }
}

/* ==========================================================================
   BOOTSTRAP
   ========================================================================== */

async function bootstrap() {
  window.__lemuroidDispatchAction = (action) => handleInputAction(action);
  // Android Back must dismiss the active layer before leaving the dashboard.
  window.__lemuroidHandleBack = () => {
    if (!isAnyModalOpen() && !isSidebarOpen()) {
      // Android and Gamepad API may report the same physical B press.
      return Date.now() - lastLayerDismissedAt < 250;
    }
    handleInputAction("back");
    return true;
  };
  syncAppMode(getSettings());

  window.addEventListener("resize", () => {
    if (getSettings().mode === "auto") {
      syncAppMode(getSettings());
    }
  });

  subscribeSettings((nextSettings) => {
    syncAppMode(nextSettings);
    renderTvPrompts();
    if (currentEffectiveMode === "mobile") {
      renderMobileGames();
    } else if (!isSidebarOpen() && !isAnyModalOpen()) {
      renderTvGameGrid();
    }
  });

  initSidebar({
    onToast: showToast,
    onClose: () => {
      if (currentEffectiveMode === "tv") refreshFocus({ silent: true });
    },
  });

  initMobileView({
    onSelectConsole: selectConsole,
    onOpenSidebar: () => openSidebar(),
    onOpenGameModal: openGameModal,
    onDownloadGame: downloadGame,
    onDeleteGame: confirmDeleteGame,
    onFindVersions: async (cid, query) => {
      if (cid && cid !== "local" && cid !== currentConsoleId) {
        await selectConsole(cid);
      }
      openSearchModal(query || "");
    },
    onToast: showToast,
  });

  initTvView({
    onSelectConsole: selectConsole,
    onOpenSidebar: () => openSidebar(),
    onOpenSearchModal: openSearchModal,
    onOpenGameModal: openGameModal,
    onToast: showToast,
  });

  bindModalEvents();

  updateTvGamepadIndicator(getConnectedGamepads());
  onGamepadConnectionChange((pads) => {
    updateTvGamepadIndicator(pads);
    if (pads.length > 0) {
      showToast(`Controle conectado: ${pads[0].id.slice(0, 28)}`);
    }
  });
  startInputLoop(handleInputAction);

  try {
    consoles = await loadConsoles();
    const defaultConsole =
      consoles.find((c) => c.id === "snes")?.id || consoles[0]?.id || "snes";
    await selectConsole(defaultConsole);
  } catch (err) {
    playError();
    showToast(`Erro ao carregar lista de consoles: ${err?.message || "verifique data/consoles.json"}`);
  }

  subscribeLibrarySync(() => {
    invalidateMobileLibrary();
    if (currentEffectiveMode === "mobile") {
      renderMobileGames();
    } else if (!isSidebarOpen() && !isAnyModalOpen()) {
      renderTvGameGrid();
    }
  });
  initDownloadQueueBridge();
  subscribeDownloadQueue(() => {
    const active = getActiveDownloadCount();
    const tvBadge = document.getElementById("tv-dl-badge");
    if (tvBadge) {
      tvBadge.textContent = String(active);
      tvBadge.classList.toggle("hidden", active === 0);
    }
    if (isUtilityModalOpen() && activeUtilityTab === "downloads") {
      renderUtilityDownloads();
    }
  });
  syncLocalLibrary().catch(() => {});

  const splashEl = document.getElementById("app-splash");
  if (splashEl) {
    setTimeout(() => {
      splashEl.classList.add("is-hidden");
    }, 650);
  }
}

if (typeof document !== "undefined") {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bootstrap);
  } else {
    bootstrap();
  }
}
