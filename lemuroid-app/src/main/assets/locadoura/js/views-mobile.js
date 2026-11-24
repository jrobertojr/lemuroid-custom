/**
 * Touch-First Mobile Dashboard view for Locadoura (#mobile-view).
 * Renders console pills, live search, favorites/region filters, inline Cover+Print toggle,
 * and paginated game cards (50 per page).
 */

import {
  getSettings,
  updateSetting,
  updateConsoleFolderSetting,
  isFavorite,
  toggleFavorite,
} from "./state.js";
import {
  getGameKey,
  getDownloadUrl,
  getArtUrls,
  formatBytes,
  loadConsoleGames,
  loadConsoleArt,
} from "./catalog.js";
import { playConfirm, playSwitch } from "./sound.js";
import { matchLocalRom, getUnifiedLibrary, syncLocalLibrary, getSyncStatus, normalizeBaseTitle } from "./library-sync.js";
import {
  getDownloadQueue,
  getActiveDownloadCount,
  cancelDownloadItem,
  clearCompletedDownloads,
  subscribeDownloadQueue,
} from "./download-queue.js";

const PAGE_SIZE = 50;

let consolesList = [];
let activeConsoleId = "snes";
let allGames = [];
let filteredGames = [];
let currentPage = 1;
let searchQuery = "";
let onlyFavorites = false;
let onlyDownloaded = false;
let regionFilter = "all";
let mobileSubView = "games"; // "games" | "consoles" | "library" | "downloads"
let unifiedLibraryCache = [];
let unifiedLibraryDirty = true;
let unifiedLibraryLoading = false;
let libPage = 1;
const LIB_PAGE_SIZE = 60;
let libraryLocalFilter = null; // null = follow activeConsoleId, or "local" / "all"
let isLoading = false;

export function invalidateMobileLibrary() {
  unifiedLibraryDirty = true;
  if (mobileSubView === "library" && !unifiedLibraryLoading && consolesList.length > 0) {
    unifiedLibraryLoading = true;
    renderMobileGames();
    getUnifiedLibrary(consolesList, loadConsoleGames, loadConsoleArt).then((res) => {
      unifiedLibraryCache = res.items || [];
      unifiedLibraryDirty = false;
      unifiedLibraryLoading = false;
      renderMobileConsolePills();
      if (mobileSubView === "library") renderMobileGames();
    });
  }
}

let callbacks = {
  onSelectConsole: null,
  onOpenSidebar: null,
  onOpenGameModal: null,
  onDownloadGame: null,
  onDeleteGame: null,
  onFindVersions: null,
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
    menuBtn: document.getElementById("mobile-menu-btn"),
    modeToggle: document.getElementById("mobile-mode-toggle"),
    searchToggle: document.getElementById("mobile-search-toggle"),
    searchBar: document.getElementById("mobile-search-bar"),
    searchInput: document.getElementById("mobile-search-input"),
    searchClear: document.getElementById("mobile-search-clear"),
    consoleBar: document.getElementById("mobile-console-bar"),
    consolePills: document.getElementById("mobile-console-pills"),
    toolbar: document.getElementById("mobile-toolbar"),
    resultCount: document.getElementById("mobile-result-count"),
    showArtToggle: document.getElementById("mobile-show-art-toggle"),
    gameList: document.getElementById("mobile-game-list"),
    bottomNav: document.getElementById("mobile-bottom-nav"),
  };
}

function ensureFilterControls() {
  const { toolbar } = getEls();
  if (!toolbar || document.getElementById("mobile-fav-filter")) return;

  const extraWrap = document.createElement("div");
  extraWrap.style.cssText = "display:flex;align-items:center;gap:8px;flex-wrap:wrap;";
  extraWrap.innerHTML = `
    <button
      id="mobile-fav-filter"
      type="button"
      class="sb-pill-btn"
      style="min-height:34px;padding:4px 10px;font-size:0.78rem;"
    >★ Favoritos</button>
    <button
      id="mobile-dl-filter"
      type="button"
      class="sb-pill-btn"
      style="min-height:34px;padding:4px 10px;font-size:0.78rem;"
    >✓ Baixados</button>
    <select
      id="mobile-region-filter"
      aria-label="Filtrar por região"
      style="min-height:34px;padding:4px 8px;border-radius:var(--radius-sm);background:var(--bg-card);border:1px solid var(--border-color);font-size:0.78rem;"
    >
      <option value="all">Todas Regiões</option>
    </select>
  `;
  toolbar.insertBefore(extraWrap, toolbar.lastElementChild);

  const favBtn = document.getElementById("mobile-fav-filter");
  const dlBtn = document.getElementById("mobile-dl-filter");
  const regSel = document.getElementById("mobile-region-filter");

  if (favBtn) {
    favBtn.addEventListener("click", () => {
      playSwitch();
      onlyFavorites = !onlyFavorites;
      favBtn.classList.toggle("is-active", onlyFavorites);
      applyFiltersAndRender(true);
    });
  }

  if (dlBtn) {
    dlBtn.addEventListener("click", () => {
      playSwitch();
      onlyDownloaded = !onlyDownloaded;
      dlBtn.classList.toggle("is-active", onlyDownloaded);
      applyFiltersAndRender(true);
    });
  }

  if (regSel) {
    regSel.addEventListener("change", () => {
      playSwitch();
      regionFilter = regSel.value || "all";
      applyFiltersAndRender(true);
    });
  }
}

function populateRegionOptions() {
  const regSel = document.getElementById("mobile-region-filter");
  if (!regSel) return;

  const regions = new Set();
  for (const g of allGames) {
    if (g.region) regions.add(g.region);
  }

  const prev = regionFilter;
  const sorted = Array.from(regions).sort();
  regSel.innerHTML = `<option value="all">Todas Regiões</option>` +
    sorted.map((r) => `<option value="${escapeHtml(r)}">${escapeHtml(r)}</option>`).join("");

  if (sorted.includes(prev)) {
    regSel.value = prev;
  } else {
    regionFilter = "all";
    regSel.value = "all";
  }
}

export function renderMobileConsolePills() {
  const { consolePills } = getEls();
  if (!consolePills) return;

  let libCounts = null;
  if (mobileSubView === "library") {
    libCounts = new Map();
    for (const entry of unifiedLibraryCache) {
      libCounts.set(entry.consoleId, (libCounts.get(entry.consoleId) || 0) + 1);
    }
  }

  consolePills.innerHTML = consolesList
    .map((c) => {
      const active = c.id === activeConsoleId;
      const countVal = libCounts ? (libCounts.get(c.id) || 0) : Number(c.count || 0);
      return `
        <button
          type="button"
          class="console-pill ${active ? "is-active" : ""}"
          data-console-id="${escapeHtml(c.id)}"
        >
          <span>${escapeHtml(c.shortName || c.name)}</span>
          <span class="badge" style="font-size:0.68rem;">${countVal.toLocaleString("pt-BR")}</span>
        </button>
      `;
    })
    .join("");
}

function applyFiltersAndRender(resetPage = false) {
  if (resetPage) currentPage = 1;
  const q = searchQuery.trim().toLowerCase();

  filteredGames = allGames.filter((g) => {
    if (onlyFavorites) {
      const key = getGameKey(activeConsoleId, g);
      if (!isFavorite(activeConsoleId, key)) return false;
    }
    if (onlyDownloaded) {
      if (!matchLocalRom(activeConsoleId, g)) return false;
    }
    if (regionFilter !== "all" && g.region !== regionFilter) {
      return false;
    }
    if (q) {
      const hay = `${g.title || ""} ${g.rom || ""} ${g.file || ""} ${g.maker || ""}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });

  renderMobileGames();
}

export function renderMobileGames() {
  const { resultCount, gameList, showArtToggle } = getEls();
  const settings = getSettings();

  if (showArtToggle) {
    showArtToggle.checked = Boolean(settings.showInlineArt);
  }
  if (gameList) {
    gameList.dataset.showArt = settings.showInlineArt ? "true" : "false";
  }

  if (isLoading) {
    if (resultCount) resultCount.textContent = "Carregando catálogo...";
    if (gameList) {
      gameList.innerHTML = `<div class="mobile-game-card"><strong>Carregando jogos...</strong></div>`;
    }
    return;
  }

  const total = filteredGames.length;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  if (currentPage > totalPages) currentPage = totalPages;

  if (resultCount) {
    resultCount.textContent = `${total.toLocaleString("pt-BR")} ${total === 1 ? "jogo" : "jogos"}`;
  }

  if (!gameList) return;

  if (mobileSubView === "downloads") {
    const queue = getDownloadQueue();
    const activeCount = getActiveDownloadCount();
    if (resultCount) {
      resultCount.textContent = activeCount > 0
        ? `${activeCount} download(s) ativo(s)`
        : `${queue.length} registro(s) de download`;
    }

    const headerBar = `
      <div class="mobile-game-card" style="grid-column:1/-1;display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;">
        <div>
          <strong>Downloads Ativos e Recentes</strong>
          <p class="sb-desc">Acompanhe o progresso em tempo real e gerencie a fila.</p>
        </div>
        ${
          queue.some((q) => q.status === "completed" || q.status === "failed" || q.status === "cancelled")
            ? `<button type="button" class="sb-btn-secondary" data-dl-clear="true">Limpar Concluídos</button>`
            : ""
        }
      </div>
    `;

    if (queue.length === 0) {
      gameList.innerHTML = headerBar + `
        <div class="mobile-game-card" style="grid-column:1/-1;">
          <strong>Nenhum download na fila</strong>
          <p class="sb-desc">Toque em "Baixar" em qualquer jogo para acompanhar aqui.</p>
        </div>
      `;
      return;
    }

    const statusLabelMap = {
      queued: "Na fila",
      downloading: "Baixando",
      completed: "Concluído",
      failed: "Erro",
      cancelled: "Cancelado",
    };

    gameList.innerHTML = headerBar + queue.map((item) => {
      const pct = item.totalBytes > 0
        ? Math.min(100, Math.round((item.bytesDownloaded / item.totalBytes) * 100))
        : (item.status === "completed" ? 100 : 0);
      const isActive = item.status === "queued" || item.status === "downloading";
      const badgeClass = item.status === "completed"
        ? "badge-accent"
        : item.status === "failed"
        ? "badge-warn"
        : "";
      const progressText = item.totalBytes > 0
        ? `${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.totalBytes)} (${pct}%)`
        : (item.bytesDownloaded > 0 ? formatBytes(item.bytesDownloaded) : "");

      return `
        <article class="mobile-game-card">
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:8px;">
            <div style="min-width:0;">
              <h3 class="mobile-game-title">${escapeHtml(item.title)}</h3>
              <p class="sb-desc" style="word-break:break-all;">${escapeHtml(item.filename)}</p>
            </div>
            <span class="badge ${badgeClass}">${escapeHtml(statusLabelMap[item.status] || item.status)}</span>
          </div>
          <div class="dl-progress-track">
            <div class="dl-progress-fill" style="width:${pct}%;"></div>
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:8px;">
            <span class="sb-desc">${escapeHtml(item.error || progressText)}</span>
            ${
              isActive
                ? `<button type="button" class="sb-btn-secondary" data-dl-cancel="${escapeHtml(item.id)}">✕ Cancelar</button>`
                : ""
            }
          </div>
        </article>
      `;
    }).join("");
    return;
  }

  if (mobileSubView === "library") {
    const activeConsoleMeta = consolesList.find((c) => c.id === activeConsoleId);
    const activeConsoleName = activeConsoleMeta?.shortName || activeConsoleMeta?.name || activeConsoleId;

    const libFiltered = unifiedLibraryCache
      .map((entry, cacheIdx) => ({ entry, cacheIdx }))
      .filter(({ entry }) => entry.consoleId === activeConsoleId);

    if (resultCount) {
      resultCount.textContent = unifiedLibraryLoading
        ? "Carregando biblioteca..."
        : `${libFiltered.length.toLocaleString("pt-BR")} jogo(s) em ${activeConsoleName}`;
    }

    const syncHeader = `
      <div class="lib-header-panel">
        <div class="lib-header-top">
          <div>
            <strong style="font-size:1rem;">Biblioteca • ${escapeHtml(activeConsoleName)}</strong>
            <p class="sb-desc">${libFiltered.length} jogo(s) neste sistema • ${unifiedLibraryCache.length} no total</p>
          </div>
          <button type="button" class="sb-btn-secondary" data-lib-sync="true" style="min-height:34px;padding:4px 12px;font-size:0.8rem;">🔄 Sincronizar</button>
        </div>
      </div>
    `;

    if (unifiedLibraryLoading) {
      gameList.innerHTML = syncHeader + `<div class="lib-header-panel"><strong>Escaneando pastas...</strong></div>`;
      return;
    }

    if (libFiltered.length === 0) {
      const scannedInfo = getSyncStatus();
      const nonEmptyConsoles = [];
      for (const c of consolesList) {
        const cnt = unifiedLibraryCache.filter((e) => e.consoleId === c.id).length;
        if (cnt > 0) nonEmptyConsoles.push(`${c.shortName || c.name} (${cnt})`);
      }
      gameList.innerHTML = syncHeader + `
        <div class="lib-header-panel">
          <strong>${
            unifiedLibraryCache.length > 0
              ? `Nenhum jogo de ${escapeHtml(activeConsoleName)} encontrado`
              : scannedInfo.lastTotalFiles > 0
              ? `${scannedInfo.lastTotalFiles} arquivo(s) lido(s), mas nenhum correspondeu ao sistema selecionado`
              : "Nenhum arquivo encontrado nas pastas"
          }</strong>
          <p class="sb-desc">${
            nonEmptyConsoles.length > 0
              ? `Jogos encontrados em: <strong>${escapeHtml(nonEmptyConsoles.join(", "))}</strong>. Selecione um deles na barra superior.`
              : "Escolha outro console na barra superior ou configure a pasta em Opções → Pastas."
          }</p>
        </div>
      `;
      return;
    }

    const libTotalPages = Math.max(1, Math.ceil(libFiltered.length / LIB_PAGE_SIZE));
    if (libPage > libTotalPages) libPage = libTotalPages;
    if (libPage < 1) libPage = 1;
    const libStart = (libPage - 1) * LIB_PAGE_SIZE;
    const libSlice = libFiltered.slice(libStart, libStart + LIB_PAGE_SIZE);

    const cards = libSlice.map(({ entry, cacheIdx }) => {
      const { consoleId, consoleName, game, match, isUnmatched } = entry;
      const isMod = match.kind === "mod" || match.kind === "variant";
      const art = getArtUrls(consoleId, game);
      const coverUrl = art.boxUrl || art.snapUrl;
      const badgeLabel = isMod ? "PT-BR/MOD" : (isUnmatched ? "LOCAL" : "✓");
      const sizeStr = match.localSize ? formatBytes(match.localSize) : "";
      const subLine = [consoleName, sizeStr].filter(Boolean).join(" • ");

      return `
        <article class="lib-poster-card">
          <div class="lib-poster-stage" ${!isUnmatched ? `data-lib-details="${cacheIdx}"` : ""}>
            ${
              coverUrl
                ? `<img class="lib-poster-img" src="${escapeHtml(coverUrl)}" alt="" loading="lazy" onerror="this.style.display='none';this.nextElementSibling.style.display='block';" /><span class="lib-poster-placeholder" style="display:none;">🎮</span>`
                : `<span class="lib-poster-placeholder">🎮</span>`
            }
            <span class="lib-poster-badge ${isMod ? "is-mod" : ""}">${badgeLabel}</span>
          </div>
          <div class="lib-poster-body">
            <h3 class="lib-poster-title">${escapeHtml(game.title || match.localName)}</h3>
            <div class="lib-poster-meta">${escapeHtml(subLine)}</div>
          </div>
          <div class="lib-poster-actions">
            <button type="button" class="lib-poster-btn" data-lib-versions="${cacheIdx}">🔎 Versões</button>
            <button type="button" class="lib-poster-del" data-lib-delete="${cacheIdx}" aria-label="Excluir">🗑️</button>
          </div>
        </article>
      `;
    }).join("");

    const libPaginationHtml = libTotalPages > 1
      ? `
        <div class="mobile-game-card" style="grid-column:1/-1;display:flex;align-items:center;justify-content:space-between;gap:10px;">
          <button type="button" class="sb-btn-secondary" data-lib-page="prev" ${libPage <= 1 ? "disabled" : ""}>← Anterior</button>
          <span style="font-size:0.84rem;color:var(--text-secondary);">Página ${libPage} de ${libTotalPages} (${libFiltered.length} jogos)</span>
          <button type="button" class="sb-btn-secondary" data-lib-page="next" ${libPage >= libTotalPages ? "disabled" : ""}>Próxima →</button>
        </div>
      `
      : "";

    gameList.innerHTML = syncHeader + `<div class="lib-grid-wrap">${cards}</div>` + libPaginationHtml;
    return;
  }

  if (mobileSubView === "consoles") {
    if (resultCount) {
      resultCount.textContent = `${consolesList.length} sistemas disponíveis`;
    }
    gameList.innerHTML = consolesList
      .map((c) => {
        const isCurrent = c.id === activeConsoleId;
        const count = Number(c.count || 0).toLocaleString("pt-BR");
        const sizeLabel = c.totalSize ? formatBytes(c.totalSize) : "";
        return `
          <button
            type="button"
            class="mobile-game-card"
            data-mobile-pick-console="${escapeHtml(c.id)}"
            style="text-align:left;cursor:pointer;${isCurrent ? "border-color:var(--accent);box-shadow:0 0 0 1px var(--accent);" : ""}"
          >
            <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
              <strong style="font-size:1.02rem;color:var(--text-primary);">${escapeHtml(c.name || c.shortName || c.id)}</strong>
              ${isCurrent ? '<span class="badge badge-accent">Ativo</span>' : '<span class="badge">Abrir →</span>'}
            </div>
            <div class="mobile-game-meta" style="margin-top:8px;">
              <span class="badge">${count} ROMs</span>
              ${sizeLabel ? `<span class="badge">${sizeLabel}</span>` : ""}
            </div>
          </button>
        `;
      })
      .join("");
    return;
  }

  if (total === 0) {
    gameList.innerHTML = `
      <div class="mobile-game-card" style="grid-column:1/-1;">
        <strong>Nenhum jogo encontrado</strong>
        <p class="sb-desc">Tente limpar a busca ou selecionar outro console.</p>
      </div>
    `;
    return;
  }

  const startIdx = (currentPage - 1) * PAGE_SIZE;
  const pageItems = filteredGames.slice(startIdx, startIdx + PAGE_SIZE);

  const cardsHtml = pageItems
    .map((game, i) => {
      const globalIdx = startIdx + i;
      const key = getGameKey(activeConsoleId, game);
      const fav = isFavorite(activeConsoleId, key);
      const localMatch = matchLocalRom(activeConsoleId, game);
      const art = getArtUrls(activeConsoleId, game);
      const metaBadges = [];
      if (localMatch) {
        const isMod = localMatch.kind === "mod" || localMatch.kind === "variant";
        metaBadges.push(
          `<span class="badge ${isMod ? "badge-warn" : "badge-accent"}" title="${escapeHtml(localMatch.localName)}">${
            isMod ? "✓ Mod / PT-BR" : "✓ Baixado"
          }</span>`
        );
      }
      if (game.region) metaBadges.push(`<span class="badge">${escapeHtml(game.region)}</span>`);
      if (game.year) metaBadges.push(`<span class="badge">${escapeHtml(game.year)}</span>`);
      if (game.maker) metaBadges.push(`<span class="badge">${escapeHtml(game.maker)}</span>`);
      if (game.size) metaBadges.push(`<span class="badge">${formatBytes(game.size)}</span>`);
      if (game.cloneof) metaBadges.push(`<span class="badge badge-warn">Clone</span>`);

      const coverImg = art.boxUrl
        ? `<img class="mobile-game-thumb" style="object-fit:contain;" src="${escapeHtml(art.boxUrl)}" alt="Capa" loading="lazy" onerror="this.style.display='none'" />`
        : "";
      const printImg = art.snapUrl
        ? `<img class="mobile-game-thumb is-print" style="object-fit:contain;" src="${escapeHtml(art.snapUrl)}" alt="Print" loading="lazy" onerror="this.style.display='none'" />`
        : "";

      return `
        <article class="mobile-game-card" data-game-idx="${globalIdx}">
          <div class="mobile-game-card-top">
            ${
              coverImg || printImg
                ? `<div class="mobile-game-art">${coverImg}${printImg}</div>`
                : ""
            }
            <div class="mobile-game-info">
              <h3 class="mobile-game-title">${escapeHtml(game.title || key)}</h3>
              <div class="mobile-game-meta">${metaBadges.join("")}</div>
            </div>
          </div>

          <div class="mobile-game-actions">
            <div style="display:flex;gap:6px;flex-wrap:wrap;">
              <button type="button" class="mobile-dl-btn" data-mobile-action="download" data-idx="${globalIdx}">
                ⬇ Baixar
              </button>
              <button type="button" class="sb-btn-secondary" data-mobile-action="details" data-idx="${globalIdx}">
                Detalhes
              </button>
              ${
                localMatch
                  ? `<button type="button" class="sb-btn-secondary" data-mobile-action="delete" data-idx="${globalIdx}" style="color:#f87171;border-color:rgba(248,113,113,0.4);">🗑️ Excluir</button>`
                  : ""
              }
            </div>
            <div style="display:flex;gap:6px;">
              <button
                type="button"
                class="icon-btn touch-target"
                title="Copiar link"
                aria-label="Copiar link"
                data-mobile-action="copy"
                data-idx="${globalIdx}"
              >🔗</button>
              <button
                type="button"
                class="icon-btn touch-target"
                title="${fav ? "Remover favorito" : "Favoritar"}"
                aria-label="Favoritar"
                data-mobile-action="fav"
                data-idx="${globalIdx}"
                style="${fav ? "border-color:var(--accent);color:var(--accent);" : ""}"
              >${fav ? "★" : "☆"}</button>
            </div>
          </div>
        </article>
      `;
    })
    .join("");

  const paginationHtml =
    totalPages > 1
      ? `
      <div style="grid-column:1/-1;display:flex;align-items:center;justify-content:center;gap:12px;padding:12px 0;">
        <button type="button" class="sb-btn-secondary" data-mobile-page="prev" ${currentPage <= 1 ? "disabled" : ""}>◀ Anterior</button>
        <span class="badge">Página ${currentPage} de ${totalPages}</span>
        <button type="button" class="sb-btn-secondary" data-mobile-page="next" ${currentPage >= totalPages ? "disabled" : ""}>Próxima ▶</button>
      </div>
    `
      : "";

  gameList.innerHTML = cardsHtml + paginationHtml;
}

export function setMobileData({ consoles, consoleId, games, loading }) {
  if (Array.isArray(consoles)) consolesList = consoles;
  if (typeof consoleId === "string") activeConsoleId = consoleId;
  if (Array.isArray(games)) {
    allGames = games;
    populateRegionOptions();
  }
  if (typeof loading === "boolean") isLoading = loading;

  renderMobileConsolePills();

  if (mobileSubView === "library") {
    libraryLocalFilter = null;
    libPage = 1;
    if (!unifiedLibraryLoading && (unifiedLibraryDirty || unifiedLibraryCache.length === 0) && consolesList.length > 0) {
      unifiedLibraryLoading = true;
      renderMobileGames();
      getUnifiedLibrary(consolesList, loadConsoleGames, loadConsoleArt).then((res) => {
        unifiedLibraryCache = res.items || [];
        unifiedLibraryDirty = false;
        unifiedLibraryLoading = false;
        if (mobileSubView === "library") renderMobileGames();
      });
    } else {
      renderMobileGames();
    }
    return;
  }

  applyFiltersAndRender(typeof games !== "undefined");
}

function setBottomNavActive(navKey) {
  const { bottomNav } = getEls();
  if (!bottomNav) return;
  const items = bottomNav.querySelectorAll("button, [data-nav], [data-mobile-nav]");
  for (const item of items) {
    const key =
      item.dataset.nav ||
      item.dataset.mobileNav ||
      item.dataset.tab ||
      item.textContent.trim().toLowerCase();
    item.classList.toggle("is-active", key.includes(navKey));
  }
}

function bindEvents() {
  const {
    menuBtn,
    modeToggle,
    searchToggle,
    searchBar,
    searchInput,
    searchClear,
    consoleBar,
    consolePills,
    showArtToggle,
    gameList,
    bottomNav,
  } = getEls();

  if (menuBtn) {
    menuBtn.addEventListener("click", () => {
      playSwitch();
      callbacks.onOpenSidebar?.();
    });
  }

  if (modeToggle) {
    modeToggle.addEventListener("click", () => {
      playSwitch();
      updateSetting("mode", "tv");
      callbacks.onToast?.("Alternado para Modo TV (Console).");
    });
  }

  if (searchToggle && searchBar) {
    searchToggle.addEventListener("click", () => {
      playSwitch();
      searchBar.classList.remove("hidden");
      const isOpen = searchBar.classList.toggle("is-open");
      searchToggle.setAttribute("aria-expanded", String(isOpen));
      searchToggle.classList.toggle("is-active", isOpen);
      if (isOpen && searchInput) {
        setTimeout(() => searchInput.focus(), 40);
      }
    });
  }

  if (bottomNav) {
    bottomNav.addEventListener("click", (e) => {
      const btn = e.target.closest("button, [data-nav], [data-mobile-nav]");
      if (!btn) return;
      const rawKey = (
        btn.dataset.nav ||
        btn.dataset.mobileNav ||
        btn.dataset.tab ||
        btn.textContent ||
        ""
      )
        .trim()
        .toLowerCase();

      playSwitch();

      if (rawKey.includes("favorit")) {
        mobileSubView = "games";
        onlyFavorites = true;
        const favBtn = document.getElementById("mobile-fav-filter");
        if (favBtn) favBtn.classList.add("is-active");
        setBottomNavActive("favorit");
        renderMobileConsolePills();
        applyFiltersAndRender(true);
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
      } else if (rawKey.includes("bibliot") || rawKey.includes("library")) {
        mobileSubView = "library";
        libraryLocalFilter = null;
        setBottomNavActive("bibliot");
        unifiedLibraryLoading = true;
        renderMobileConsolePills();
        renderMobileGames();
        getUnifiedLibrary(consolesList, loadConsoleGames, loadConsoleArt).then((res) => {
          unifiedLibraryCache = res.items || [];
          unifiedLibraryDirty = false;
          unifiedLibraryLoading = false;
          renderMobileConsolePills();
          if (mobileSubView === "library") renderMobileGames();
        });
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
      } else if (rawKey.includes("download")) {
        mobileSubView = "downloads";
        setBottomNavActive("download");
        renderMobileConsolePills();
        renderMobileGames();
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
      } else if (rawKey.includes("consol")) {
        mobileSubView = "consoles";
        setBottomNavActive("consol");
        renderMobileConsolePills();
        renderMobileGames();
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
      } else if (rawKey.includes("ajust") || rawKey.includes("op") || rawKey.includes("menu")) {
        callbacks.onOpenSidebar?.();
      } else {
        // Explorar
        mobileSubView = "games";
        onlyFavorites = false;
        const favBtn = document.getElementById("mobile-fav-filter");
        if (favBtn) favBtn.classList.remove("is-active");
        setBottomNavActive("explor");
        renderMobileConsolePills();
        applyFiltersAndRender(true);
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
      }
    });
  }

  if (searchInput) {
    searchInput.addEventListener("input", () => {
      searchQuery = searchInput.value;
      if (searchClear) {
        searchClear.classList.toggle("hidden", !searchQuery);
      }
      applyFiltersAndRender(true);
    });
  }

  if (searchClear && searchInput) {
    searchClear.addEventListener("click", () => {
      searchInput.value = "";
      searchQuery = "";
      searchClear.classList.add("hidden");
      applyFiltersAndRender(true);
    });
  }

  if (consolePills) {
    consolePills.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-console-id]");
      if (!btn) return;
      const nextId = btn.dataset.consoleId;
      if (nextId && nextId !== activeConsoleId) {
        playSwitch();
        callbacks.onSelectConsole?.(nextId);
      }
    });
  }

  if (showArtToggle) {
    showArtToggle.addEventListener("change", () => {
      playSwitch();
      updateSetting("showInlineArt", Boolean(showArtToggle.checked));
      if (gameList) {
        gameList.dataset.showArt = showArtToggle.checked ? "true" : "false";
      }
    });
  }

  if (gameList) {
    gameList.addEventListener("click", async (e) => {
      const libPageBtn = e.target.closest("[data-lib-page]");
      if (libPageBtn) {
        playSwitch();
        libPage += libPageBtn.dataset.libPage === "next" ? 1 : -1;
        renderMobileGames();
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }

      const cancelDlBtn = e.target.closest("[data-dl-cancel]");
      if (cancelDlBtn) {
        playSwitch();
        await cancelDownloadItem(cancelDlBtn.dataset.dlCancel);
        renderMobileGames();
        return;
      }

      if (e.target.closest("[data-dl-clear]")) {
        playSwitch();
        clearCompletedDownloads();
        renderMobileGames();
        return;
      }

      if (e.target.closest("[data-lib-pick-folder]")) {
        const native = window.Capacitor?.Plugins?.LocadouraNative;
        if (native && typeof native.pickFolder === "function") {
          playConfirm();
          try {
            const res = await native.pickFolder({ key: activeConsoleId });
            if (res && !res.cancelled && (res.uri || res.displayPath)) {
              updateConsoleFolderSetting(activeConsoleId, res.displayPath || res.uri, res.uri || "");
              unifiedLibraryLoading = true;
              renderMobileGames();
              await syncLocalLibrary();
              const libRes = await getUnifiedLibrary(consolesList, loadConsoleGames, loadConsoleArt);
              unifiedLibraryCache = libRes.items || [];
              unifiedLibraryDirty = false;
              unifiedLibraryLoading = false;
              renderMobileGames();
              callbacks.onToast?.(`Pasta configurada e sincronizada!`);
            }
          } catch {
            callbacks.onToast?.("Não foi possível selecionar a pasta.");
          }
        } else {
          callbacks.onOpenSidebar?.();
        }
        return;
      }

      if (e.target.closest("[data-lib-sync]")) {
        playConfirm();
        unifiedLibraryLoading = true;
        renderMobileGames();
        await syncLocalLibrary();
        const res = await getUnifiedLibrary(consolesList, loadConsoleGames, loadConsoleArt);
        unifiedLibraryCache = res.items || [];
        unifiedLibraryDirty = false;
        unifiedLibraryLoading = false;
        renderMobileConsolePills();
        renderMobileGames();
        callbacks.onToast?.(`Biblioteca sincronizada: ${unifiedLibraryCache.length} jogo(s).`);
        return;
      }

      const libDetBtn = e.target.closest("[data-lib-details]");
      if (libDetBtn) {
        const entry = unifiedLibraryCache[Number(libDetBtn.dataset.libDetails)];
        if (entry) {
          playConfirm();
          callbacks.onOpenGameModal?.(entry.consoleId, entry.game);
        }
        return;
      }

      const libVerBtn = e.target.closest("[data-lib-versions]");
      if (libVerBtn) {
        const entry = unifiedLibraryCache[Number(libVerBtn.dataset.libVersions)];
        if (entry) {
          playConfirm();
          const query = normalizeBaseTitle(entry.game?.title || entry.match?.localName || "");
          callbacks.onFindVersions?.(entry.consoleId, query);
        }
        return;
      }

      const libDelBtn = e.target.closest("[data-lib-delete]");
      if (libDelBtn) {
        const entry = unifiedLibraryCache[Number(libDelBtn.dataset.libDelete)];
        if (entry) {
          playSwitch();
          callbacks.onDeleteGame?.(entry.consoleId, entry.game, entry.match, () => {
            unifiedLibraryCache = unifiedLibraryCache.filter((x) => x !== entry);
            renderMobileGames();
          });
        }
        return;
      }

      const libFilterBtn = e.target.closest("[data-lib-filter]");
      if (libFilterBtn) {
        const fid = libFilterBtn.dataset.libFilter;
        playSwitch();
        if (fid === "all" || fid === "local") {
          libraryLocalFilter = fid;
          renderMobileGames();
        } else {
          libraryLocalFilter = null;
          if (fid !== activeConsoleId) {
            callbacks.onSelectConsole?.(fid);
          } else {
            renderMobileGames();
          }
        }
        return;
      }

      const pickConsoleBtn = e.target.closest("[data-mobile-pick-console]");
      if (pickConsoleBtn) {
        const cid = pickConsoleBtn.dataset.mobilePickConsole || pickConsoleBtn.dataset.consoleId;
        playConfirm();
        if (pickConsoleBtn.dataset.mobilePickConsole) {
          mobileSubView = "games";
          onlyFavorites = false;
          setBottomNavActive("explor");
        }
        if (cid && cid !== activeConsoleId) {
          callbacks.onSelectConsole?.(cid);
        } else {
          applyFiltersAndRender(true);
        }
        document.getElementById("mobile-content-scroll")?.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }

      const pageBtn = e.target.closest("[data-mobile-page]");
      if (pageBtn) {
        playSwitch();
        currentPage += pageBtn.dataset.mobilePage === "next" ? 1 : -1;
        renderMobileGames();
        window.scrollTo({ top: 0, behavior: "smooth" });
        return;
      }

      const actionBtn = e.target.closest("[data-mobile-action]");
      if (!actionBtn) return;

      const idx = Number(actionBtn.dataset.idx);
      const game = filteredGames[idx];
      if (!game) return;

      const action = actionBtn.dataset.mobileAction;
      if (action === "download") {
        playConfirm();
        if (typeof callbacks.onDownloadGame === "function") {
          callbacks.onDownloadGame(activeConsoleId, game);
        } else {
          const url = getDownloadUrl(activeConsoleId, game);
          if (url) window.open(url, "_blank", "noopener,noreferrer");
          callbacks.onToast?.(`Iniciando download: ${game.title || game.rom}`);
        }
      } else if (action === "details") {
        playConfirm();
        callbacks.onOpenGameModal?.(activeConsoleId, game);
      } else if (action === "delete") {
        const localMatch = matchLocalRom(activeConsoleId, game);
        if (localMatch) {
          playSwitch();
          callbacks.onDeleteGame?.(activeConsoleId, game, localMatch, () => {
            renderMobileGames();
          });
        }
      } else if (action === "copy") {
        playConfirm();
        const url = getDownloadUrl(activeConsoleId, game);
        try {
          await navigator.clipboard.writeText(url);
          callbacks.onToast?.("Link de download copiado!");
        } catch {
          callbacks.onToast?.("Não foi possível copiar o link.");
        }
      } else if (action === "fav") {
        playConfirm();
        const key = getGameKey(activeConsoleId, game);
        const nowFav = toggleFavorite(activeConsoleId, key);
        callbacks.onToast?.(nowFav ? "Adicionado aos favoritos!" : "Removido dos favoritos.");
        renderMobileGames();
      }
    });
  }
}

export function initMobileView(opts = {}) {
  callbacks = { ...callbacks, ...opts };
  ensureFilterControls();
  bindEvents();
  subscribeDownloadQueue(() => {
    const active = getActiveDownloadCount();
    const badge = document.getElementById("mnav-dl-badge");
    if (badge) {
      badge.textContent = String(active);
      badge.classList.toggle("hidden", active === 0);
    }
    if (mobileSubView === "downloads") {
      renderMobileGames();
    }
  });
}
