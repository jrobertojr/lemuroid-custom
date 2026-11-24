/**
 * Slide-over Options Sidebar for Locadoura.
 * Manages 6 sections: modo, som, temas, controle, pastas, embreve.
 * All interactive elements carry data-focusable="true" for full TV D-pad navigation.
 */

import {
  THEMES,
  SOUND_PROFILES,
  GAMEPAD_STYLES,
  SOON_TOOLS,
  getSettings,
  updateSetting,
  updateFolderSetting,
  updateConsoleFolderSetting,
  resetSettings,
  resolveEffectiveMode,
} from "./state.js";
import { loadConsoles } from "./catalog.js";
import { syncLocalLibrary, getSyncStatus } from "./library-sync.js";
import { playConfirm, playBack, playSwitch, testSound } from "./sound.js";
import { getConnectedGamepads, triggerRumble, getButtonLabels } from "./gamepad.js";
import { focusFirst, refreshFocus } from "./focus-engine.js";

const TAB_ORDER = ["pastas", "som", "controle"];

const THEME_SWATCHES = {
  clean: ["#f8fafc", "#2563eb", "#0f172a"],
  retro80s: ["#120726", "#ff2a85", "#00f0ff"],
  nextgen: ["#0a0e1a", "#38bdf8", "#818cf8"],
  cyber: ["#050806", "#22c55e", "#f59e0b"],
  handheld: ["#2b3325", "#8bac0f", "#0f380f"],
};

const FOLDER_META = [
  {
    key: "cache",
    label: "Pasta de Cache",
    hint: "Miniaturas, capas, prints e índices offline.",
  },
  {
    key: "games",
    label: "Pasta de Jogos / ROMs",
    hint: "Destino padrão dos downloads por console.",
  },
  {
    key: "temp",
    label: "Pasta de Conversão / Temp",
    hint: "Área temporária para futuras conversões CHD/CSO.",
  },
  {
    key: "saves",
    label: "Pasta de Saves / Backups",
    hint: "Diretório para backups de saves e configurações.",
  },
];

let activeTab = "pastas";
let isOpen = false;
let consoleFoldersSubmenuOpen = false;
let cachedConsoles = [];
let testerRafId = null;
let onToastCb = null;
let onCloseCb = null;

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function getElements() {
  return {
    sidebar: document.getElementById("sidebar"),
    overlay: document.getElementById("sidebar-overlay"),
    closeBtn: document.getElementById("sidebar-close-btn"),
    tabsNav: document.getElementById("sidebar-tabs"),
    content: document.getElementById("sidebar-content"),
  };
}

function showNotice(msg) {
  if (typeof onToastCb === "function") {
    onToastCb(msg);
  }
}

function stopGamepadTesterLoop() {
  if (testerRafId !== null && typeof cancelAnimationFrame === "function") {
    cancelAnimationFrame(testerRafId);
    testerRafId = null;
  }
}

function startGamepadTesterLoop() {
  stopGamepadTesterLoop();
  if (!isOpen || activeTab !== "controle") return;

  const tick = () => {
    if (!isOpen || activeTab !== "controle") {
      testerRafId = null;
      return;
    }

    const statusEl = document.getElementById("sb-gamepad-status-text");
    const gridEl = document.getElementById("sb-gamepad-tester-grid");
    const pads = getConnectedGamepads();

    if (statusEl) {
      if (pads.length > 0) {
        statusEl.textContent = `Conectado: ${pads[0].id}`;
        statusEl.dataset.connected = "true";
      } else {
        statusEl.textContent = "Nenhum controle físico conectado (usando teclado)";
        statusEl.dataset.connected = "false";
      }
    }

    if (gridEl) {
      const pressedSet = new Set(pads.length > 0 ? pads[0].pressedButtons : []);
      const pills = gridEl.querySelectorAll("[data-gp-btn]");
      for (const pill of pills) {
        const idx = Number(pill.dataset.gpBtn);
        pill.classList.toggle("is-pressed", pressedSet.has(idx));
      }
    }

    testerRafId = requestAnimationFrame(tick);
  };

  testerRafId = requestAnimationFrame(tick);
}

function renderModoTab(settings) {
  const effective = resolveEffectiveMode(settings.mode);
  const modes = [
    { id: "auto", label: "Automático", desc: "Detecta tela larga / TV ou toque automaticamente." },
    { id: "mobile", label: "Modo Mobile", desc: "Interface compacta otimizada para toque vertical." },
    { id: "tv", label: "Modo TV (Console)", desc: "Dashboard 10-foot com Hero e navegação D-Pad." },
  ];

  return `
    <div class="sb-section">
      <div>
        <h3 class="sb-group-title">Modo de Exibição</h3>
        <p class="sb-desc">Modo ativo neste momento: <strong>${effective === "tv" ? "TV (Console)" : "Mobile"}</strong></p>
      </div>
      <div class="sb-theme-grid">
        ${modes
          .map(
            (m) => `
          <button
            type="button"
            class="sb-theme-card ${settings.mode === m.id ? "is-active" : ""}"
            data-action="set-mode"
            data-value="${m.id}"
            data-focusable="true"
            data-focus-group="sidebar"
          >
            <div>
              <strong>${m.label}</strong>
              <p class="sb-desc">${m.desc}</p>
            </div>
            <span class="badge ${settings.mode === m.id ? "badge-accent" : ""}">
              ${settings.mode === m.id ? "Ativo" : "Selecionar"}
            </span>
          </button>
        `
          )
          .join("")}
      </div>
    </div>
  `;
}

function renderSomTab(settings) {
  const volPct = Math.round((settings.soundVolume ?? 0.65) * 100);
  return `
    <div class="sb-section">
      <div class="sb-row">
        <div>
          <h3 class="sb-group-title">Sons de Navegação</h3>
          <p class="sb-desc">Efeitos sonoros ao mover o foco e confirmar ações.</p>
        </div>
        <input
          id="sb-sound-enabled"
          class="sb-toggle"
          type="checkbox"
          ${settings.soundEnabled ? "checked" : ""}
          data-action="toggle-sound"
          data-focusable="true"
          data-focus-group="sidebar"
        />
      </div>

      <div class="sb-slider-row">
        <div class="sb-slider-head">
          <label for="sb-sound-volume">Volume</label>
          <span id="sb-sound-volume-val">${volPct}%</span>
        </div>
        <input
          id="sb-sound-volume"
          class="sb-range"
          type="range"
          min="0"
          max="100"
          step="5"
          value="${volPct}"
          data-action="set-volume"
          data-focusable="true"
          data-focus-group="sidebar"
        />
      </div>

      <div>
        <h3 class="sb-group-title">Perfil Sonoro</h3>
        <div class="sb-theme-grid" style="margin-top:8px;">
          ${SOUND_PROFILES.map(
            (p) => `
            <button
              type="button"
              class="sb-theme-card ${settings.soundProfile === p.id ? "is-active" : ""}"
              data-action="set-sound-profile"
              data-value="${p.id}"
              data-focusable="true"
              data-focus-group="sidebar"
            >
              <div>
                <strong>${escapeHtml(p.name)}</strong>
                <p class="sb-desc">${escapeHtml(p.description)}</p>
              </div>
              <span class="badge ${settings.soundProfile === p.id ? "badge-accent" : ""}">
                ${settings.soundProfile === p.id ? "Ativo" : "Usar"}
              </span>
            </button>
          `
          ).join("")}
        </div>
      </div>

      <button
        type="button"
        class="sb-btn"
        data-action="test-sound"
        data-focusable="true"
        data-focus-group="sidebar"
      >🔊 Testar Som</button>
    </div>
  `;
}

function renderTemasTab(settings) {
  return `
    <div class="sb-section">
      <div>
        <h3 class="sb-group-title">Temas Visuais</h3>
        <p class="sb-desc">Cada tema transforma cores, tipografia, bordas e atmosfera.</p>
      </div>
      <div class="sb-theme-grid">
        ${THEMES.map((t) => {
          const swatches = THEME_SWATCHES[t.id] || ["#111", "#38bdf8", "#fff"];
          const active = settings.theme === t.id;
          return `
            <button
              type="button"
              class="sb-theme-card ${active ? "is-active" : ""}"
              data-action="set-theme"
              data-value="${t.id}"
              data-focusable="true"
              data-focus-group="sidebar"
            >
              <div>
                <strong>${escapeHtml(t.name)}</strong>
                <p class="sb-desc">${escapeHtml(t.description)}</p>
              </div>
              <div style="display:flex;align-items:center;gap:8px;">
                <div class="sb-theme-swatch">
                  ${swatches.map((c) => `<span style="background:${c}"></span>`).join("")}
                </div>
                <span class="badge ${active ? "badge-accent" : ""}">${active ? "Ativo" : escapeHtml(t.badge)}</span>
              </div>
            </button>
          `;
        }).join("")}
      </div>
    </div>
  `;
}

function renderControleTab(settings) {
  const dzPct = Math.round((settings.deadzone ?? 0.25) * 100);
  const testerButtons = [
    { idx: 0, label: "A / ✕" },
    { idx: 1, label: "B / ○" },
    { idx: 2, label: "X / □" },
    { idx: 3, label: "Y / △" },
    { idx: 12, label: "▲ Up" },
    { idx: 13, label: "▼ Down" },
    { idx: 14, label: "◀ Left" },
    { idx: 15, label: "▶ Right" },
    { idx: 4, label: "L1 / LB" },
    { idx: 5, label: "R1 / RB" },
    { idx: 8, label: "Select" },
    { idx: 9, label: "Start" },
  ];

  return `
    <div class="sb-section">
      <img
        src="assets/img/controller-hero.jpg"
        alt="Controle Gamepad"
        class="sb-controller-hero"
        style="width:100%;max-height:140px;object-fit:contain;border-radius:var(--radius-md);background:var(--bg-card);border:1px solid var(--border-color);"
        onerror="this.style.display='none'"
      />
      <div>
        <h3 class="sb-group-title">Estilo dos Ícones em Tela</h3>
        <div class="sb-pill-group" style="margin-top:8px;">
          ${GAMEPAD_STYLES.map(
            (g) => `
            <button
              type="button"
              class="sb-pill-btn ${settings.gamepadStyle === g.id ? "is-active" : ""}"
              data-action="set-gamepad-style"
              data-value="${g.id}"
              data-focusable="true"
              data-focus-group="sidebar"
            >${escapeHtml(g.name)}</button>
          `
          ).join("")}
        </div>
      </div>

      <div class="sb-row">
        <div>
          <strong>Analógico como D-Pad</strong>
          <p class="sb-desc">Permite navegar com o direcional analógico esquerdo.</p>
        </div>
        <input
          class="sb-toggle"
          type="checkbox"
          ${settings.analogAsDpad ? "checked" : ""}
          data-action="toggle-analog"
          data-focusable="true"
          data-focus-group="sidebar"
        />
      </div>

      <div class="sb-slider-row">
        <div class="sb-slider-head">
          <label for="sb-deadzone">Zona Morta (Deadzone)</label>
          <span id="sb-deadzone-val">${dzPct}%</span>
        </div>
        <input
          id="sb-deadzone"
          class="sb-range"
          type="range"
          min="15"
          max="60"
          step="5"
          value="${dzPct}"
          data-action="set-deadzone"
          data-focusable="true"
          data-focus-group="sidebar"
        />
      </div>

      <div class="sb-row">
        <div>
          <strong>Vibração Háptica</strong>
          <p class="sb-desc">Pulso leve ao confirmar ações no controle.</p>
        </div>
        <input
          class="sb-toggle"
          type="checkbox"
          ${settings.rumbleEnabled ? "checked" : ""}
          data-action="toggle-rumble"
          data-focusable="true"
          data-focus-group="sidebar"
        />
      </div>

      <button
        type="button"
        class="sb-btn-secondary"
        data-action="test-rumble"
        data-focusable="true"
        data-focus-group="sidebar"
      >🎮 Testar Vibração</button>

      <div>
        <h3 class="sb-group-title">Testador ao Vivo</h3>
        <div id="sb-gamepad-status-text" class="sb-gamepad-status" style="margin-top:8px;">
          Verificando controles...
        </div>
        <div id="sb-gamepad-tester-grid" class="sb-tester-grid" style="margin-top:8px;">
          ${testerButtons
            .map(
              (b) =>
                `<div class="sb-tester-pill" data-gp-btn="${b.idx}">${escapeHtml(b.label)}</div>`
            )
            .join("")}
        </div>
      </div>
    </div>
  `;
}

function resolveFolderPlanForConsole(consoleId, settings) {
  const rootPath = (window.__lemuroidGlobalRomPath || settings.folders?.games || "Pasta Global").replace(/\/+$/, "");
  const plan = window.__lemuroidFolderPlan?.[consoleId.toLowerCase()];
  if (plan) {
    return {
      folder: plan.folder || consoleId.toUpperCase(),
      fullPath: plan.fullPath || `${rootPath}/${plan.folder || consoleId.toUpperCase()}`,
      source: plan.source || "suggested",
    };
  }
  const custom = settings.consoleFolders?.[consoleId];
  if (custom) {
    return { folder: custom, fullPath: custom.includes("/") ? custom : `${rootPath}/${custom}`, source: "custom" };
  }
  const fallback = consoleId.toUpperCase();
  return { folder: fallback, fullPath: `${rootPath}/${fallback}`, source: "suggested" };
}

function renderPastasTab(settings) {
  const rootPath = (window.__lemuroidGlobalRomPath || settings.folders?.games || "Nenhuma pasta configurada").replace(/\/+$/, "");
  const planEntries = cachedConsoles.map((c) => ({
    console: c,
    info: resolveFolderPlanForConsole(c.id, settings),
  }));
  const detectedCount = planEntries.filter((e) => e.info.source === "detected").length;
  const suggestedCount = planEntries.filter((e) => e.info.source === "suggested").length;
  const customCount = planEntries.filter((e) => e.info.source === "custom").length;

  if (consoleFoldersSubmenuOpen) {
    return `
      <div class="sb-section">
        <button
          type="button"
          class="sb-btn-secondary"
          data-action="close-console-folders"
          data-focusable="true"
          data-focus-group="sidebar"
        >← Voltar para Pastas</button>
        <div>
          <h3 class="sb-group-title">Pastas por Sistema</h3>
          <p class="sb-desc">Prioridade automática: pastas que o Lemuroid já identificou (${detectedCount}) + sugestões (${suggestedCount}) criadas ao baixar. Pressione A para personalizar.</p>
        </div>
        ${
          customCount > 0
            ? `<button
                type="button"
                class="sb-btn-secondary"
                data-action="clear-all-console-folders"
                data-focusable="true"
                data-focus-group="sidebar"
              >↺ Restaurar Todas para Padrão do Lemuroid (${customCount})</button>`
            : ""
        }
        ${planEntries
          .map(({ console: c, info }) => {
            const badgeClass =
              info.source === "detected" || info.source === "custom" ? "badge-accent" : "";
            const badgeLabel =
              info.source === "detected"
                ? "✓ Detectada (Lemuroid)"
                : info.source === "custom"
                ? "⚙ Personalizada"
                : "★ Sugestão (criar ao baixar)";
            return `
              <button
                type="button"
                class="sb-console-row"
                data-action="pick-console-folder"
                data-console-id="${escapeHtml(c.id)}"
                data-focusable="true"
                data-focus-group="sidebar"
              >
                <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;">
                  <strong>${escapeHtml(c.name || c.shortName || c.id)}</strong>
                  <span class="badge ${badgeClass}">${badgeLabel}</span>
                </div>
                <span class="sb-desc" style="font-family:monospace;font-size:11px;opacity:0.9;">${escapeHtml(info.fullPath)}</span>
              </button>
            `;
          })
          .join("")}
      </div>
    `;
  }

  const syncStatus = getSyncStatus();
  const syncLabel = syncStatus.isSyncing
    ? "Sincronizando..."
    : syncStatus.lastSyncAt
    ? `${syncStatus.lastTotalFiles} arquivo(s) reconhecido(s) na biblioteca`
    : "Integrada à biblioteca do VideoGame";

  return `
    <div class="sb-section">
      <div class="sb-folder-card">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
          <strong>Pasta Raiz Unificada</strong>
          <span class="badge badge-accent">VideoGame</span>
        </div>
        <p class="sb-desc" style="font-family:monospace;font-size:11.5px;margin-top:4px;">${escapeHtml(rootPath)}</p>
        <p class="sb-desc">${escapeHtml(syncLabel)}</p>
      </div>

      <button
        type="button"
        class="sb-btn"
        data-action="open-console-folders"
        data-focusable="true"
        data-focus-group="sidebar"
      >📂 Pastas por Sistema (${detectedCount} detectadas • ${suggestedCount} sugestões) →</button>

      <button
        type="button"
        class="sb-btn-secondary"
        data-action="sync-library"
        data-focusable="true"
        data-focus-group="sidebar"
      >🔄 Sincronizar Biblioteca Local Agora</button>

      <button
        type="button"
        class="sb-btn-secondary"
        data-action="clear-all-console-folders"
        data-focusable="true"
        data-focus-group="sidebar"
      >↺ Usar Sempre Padrão Detectado pelo Lemuroid</button>
    </div>
  `;
}

function renderEmBreveTab() {
  return `
    <div class="sb-section">
      <div>
        <h3 class="sb-group-title">Ferramentas Nativas (Em Breve)</h3>
        <p class="sb-desc">Recursos planejados para a Fase 2 no Android APK e Desktop.</p>
      </div>
      ${SOON_TOOLS.map(
        (tool) => `
        <button
          type="button"
          class="sb-coming-card"
          data-action="soon-tool"
          data-tool-name="${escapeHtml(tool.name)}"
          data-focusable="true"
          data-focus-group="sidebar"
          style="text-align:left;"
        >
          <div class="sb-coming-head">
            <strong>${escapeHtml(tool.name)}</strong>
            <span class="sb-badge-soon">EM BREVE</span>
          </div>
          <p class="sb-desc">${escapeHtml(tool.description)}</p>
        </button>
      `
      ).join("")}
    </div>
  `;
}

export function renderSidebarContent() {
  const { tabsNav, content } = getElements();
  if (!content) return;

  const labels = getButtonLabels();
  const l1El = document.getElementById("sb-tab-l1");
  const r1El = document.getElementById("sb-tab-r1");
  if (l1El) l1El.innerHTML = labels.prevTab.svg;
  if (r1El) r1El.innerHTML = labels.nextTab.svg;

  const footerHints = document.getElementById("sidebar-footer-hints");
  if (footerHints) {
    footerHints.innerHTML = `
      <span>${labels.prevTab.svg} ${labels.nextTab.svg} Abas</span>
      <span>${labels.confirm.svg} Selecionar</span>
      <span>${labels.back.svg} ${consoleFoldersSubmenuOpen ? "Voltar" : "Fechar"}</span>
    `;
  }

  if (tabsNav) {
    const btns = tabsNav.querySelectorAll("[data-sidebar-tab]");
    for (const btn of btns) {
      btn.classList.toggle("is-active", btn.dataset.sidebarTab === activeTab);
    }
  }

  const settings = getSettings();
  let html = "";
  switch (activeTab) {
    case "pastas":
      html = renderPastasTab(settings);
      break;
    case "som":
      html = renderSomTab(settings);
      break;
    case "controle":
      html = renderControleTab(settings);
      break;
    default:
      html = renderPastasTab(settings);
  }

  content.innerHTML = html;

  if (activeTab === "controle" && isOpen) {
    startGamepadTesterLoop();
  } else {
    stopGamepadTesterLoop();
  }
}

export function setSidebarTab(tabId) {
  if (!TAB_ORDER.includes(tabId)) return;
  activeTab = tabId;
  if (tabId !== "pastas") {
    consoleFoldersSubmenuOpen = false;
  }
  renderSidebarContent();
  if (isOpen) {
    refreshFocus({ silent: true });
  }
}

export function cycleSidebarTab(delta) {
  const idx = TAB_ORDER.indexOf(activeTab);
  const nextIdx = (idx + delta + TAB_ORDER.length) % TAB_ORDER.length;
  playSwitch();
  setSidebarTab(TAB_ORDER[nextIdx]);
}

export function openSidebar(initialTab) {
  const { sidebar, overlay } = getElements();
  if (!sidebar) return;

  if (initialTab && TAB_ORDER.includes(initialTab)) {
    activeTab = initialTab;
  }

  isOpen = true;
  sidebar.setAttribute("data-focus-scope", "sidebar");
  sidebar.setAttribute("aria-hidden", "false");
  sidebar.classList.add("is-open");
  if (overlay) {
    overlay.classList.remove("hidden");
    overlay.setAttribute("aria-hidden", "false");
  }

  renderSidebarContent();
  focusFirst(sidebar, { silent: true });
}

export function closeSidebar() {
  const { sidebar, overlay } = getElements();
  if (!sidebar || !isOpen) return;

  if (consoleFoldersSubmenuOpen) {
    consoleFoldersSubmenuOpen = false;
    renderSidebarContent();
    focusFirst(sidebar, { silent: true });
    return;
  }

  isOpen = false;
  stopGamepadTesterLoop();
  sidebar.classList.remove("is-open");
  sidebar.setAttribute("aria-hidden", "true");
  sidebar.removeAttribute("data-focus-scope");
  if (overlay) {
    overlay.classList.add("hidden");
    overlay.setAttribute("aria-hidden", "true");
  }

  if (typeof onCloseCb === "function") {
    onCloseCb();
  }
}

export function isSidebarOpen() {
  return isOpen;
}

async function handlePickFolder(folderKey, consoleId = null) {
  const applySelection = async (displayPath, uri) => {
    if (consoleId) {
      updateConsoleFolderSetting(consoleId, displayPath, uri);
      const native = window.Capacitor?.Plugins?.LocadouraNative;
      if (native && typeof native.setConsoleFolderOverride === "function") {
        try {
          const res = await native.setConsoleFolderOverride(consoleId, uri);
          if (res?.folderPlan) window.__lemuroidFolderPlan = res.folderPlan;
        } catch {}
      }
      syncLocalLibrary().catch(() => {});
    } else {
      updateFolderSetting(folderKey, displayPath, uri);
      if (folderKey === "games") syncLocalLibrary().catch(() => {});
    }
    renderSidebarContent();
    if (isOpen) refreshFocus({ silent: true });
  };

  try {
    const native = window.Capacitor?.Plugins?.LocadouraNative;
    if (native && typeof native.pickFolder === "function") {
      const result = await native.pickFolder({ key: consoleId || folderKey });
      if (result?.cancelled) return;
      if (result && (result.uri || result.displayPath || result.path)) {
        const displayPath = result.displayPath || result.path || result.uri;
        const uri = result.uri || "";
        applySelection(displayPath, uri);
        showNotice(`Pasta selecionada: ${displayPath}`);
        return;
      }
    }

    if (typeof window.showDirectoryPicker === "function") {
      const dirHandle = await window.showDirectoryPicker();
      if (dirHandle && dirHandle.name) {
        applySelection(dirHandle.name, "");
        showNotice(`Pasta selecionada: ${dirHandle.name}`);
        return;
      }
    }

    const current = consoleId
      ? getSettings().consoleFolders?.[consoleId] || `${getSettings().folders?.games || "Locadoura/ROMs"}/${consoleId}`
      : getSettings().folders?.[folderKey] || "Locadoura";
    const manual = typeof window.prompt === "function"
      ? window.prompt("Informe o caminho da pasta:", current)
      : null;
    if (manual && manual.trim()) {
      applySelection(manual.trim(), "");
      showNotice(`Pasta atualizada: ${manual.trim()}`);
    } else {
      showNotice("No navegador, edite o caminho manualmente. O seletor nativo SAF será usado no APK Android.");
    }
  } catch (err) {
    if (err && err.name === "AbortError") return;
    showNotice("Não foi possível abrir o seletor de pastas. Edite o caminho manualmente.");
  }
}

function bindEvents() {
  const { sidebar, overlay, closeBtn, tabsNav, content } = getElements();
  if (!sidebar) return;

  if (closeBtn) {
    closeBtn.addEventListener("click", () => {
      playBack();
      closeSidebar();
    });
  }

  if (overlay) {
    overlay.addEventListener("click", () => {
      playBack();
      closeSidebar();
    });
  }

  if (tabsNav) {
    tabsNav.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-sidebar-tab]");
      if (!btn) return;
      playSwitch();
      setSidebarTab(btn.dataset.sidebarTab);
    });
  }

  if (content) {
    content.addEventListener("click", (e) => {
      const target = e.target.closest("[data-action]");
      if (!target) return;
      const action = target.dataset.action;

      switch (action) {
        case "set-mode":
          playConfirm();
          updateSetting("mode", target.dataset.value);
          renderSidebarContent();
          break;
        case "set-theme":
          playConfirm();
          updateSetting("theme", target.dataset.value);
          renderSidebarContent();
          showNotice(`Tema alterado para ${target.querySelector("strong")?.textContent || target.dataset.value}`);
          break;
        case "set-sound-profile":
          updateSetting("soundProfile", target.dataset.value);
          testSound(target.dataset.value);
          renderSidebarContent();
          break;
        case "test-sound":
          testSound();
          break;
        case "set-gamepad-style":
          playConfirm();
          updateSetting("gamepadStyle", target.dataset.value);
          renderSidebarContent();
          break;
        case "test-rumble":
          playConfirm();
          triggerRumble(160, 0.5, 0.7);
          showNotice("Pulso de vibração enviado ao controle.");
          break;
        case "pick-folder":
          playConfirm();
          handlePickFolder(target.dataset.folderKey);
          break;
        case "sync-library":
          playConfirm();
          showNotice("Iniciando rescan da biblioteca do VideoGame...");
          syncLocalLibrary({ triggerRescan: true }).then((st) => {
            renderSidebarContent();
            showNotice(`Rescan iniciado • ${st.lastTotalFiles} arquivo(s) reconhecido(s).`);
          });
          break;
        case "open-console-folders":
          playSwitch();
          consoleFoldersSubmenuOpen = true;
          renderSidebarContent();
          if (isOpen) refreshFocus({ silent: true });
          break;
        case "close-console-folders":
          playBack();
          consoleFoldersSubmenuOpen = false;
          renderSidebarContent();
          if (isOpen) refreshFocus({ silent: true });
          break;
        case "pick-console-folder":
          playConfirm();
          handlePickFolder("games", target.dataset.consoleId);
          break;
        case "clear-console-folder":
          playSwitch();
          updateConsoleFolderSetting(target.dataset.consoleId, "", "");
          window.Capacitor?.Plugins?.LocadouraNative?.setConsoleFolderOverride?.(target.dataset.consoleId, "")
            .then((res) => {
              if (res?.folderPlan) window.__lemuroidFolderPlan = res.folderPlan;
              renderSidebarContent();
              if (isOpen) refreshFocus({ silent: true });
            })
            .catch(() => renderSidebarContent());
          showNotice("Pasta do sistema restaurada para o padrão do Lemuroid.");
          break;
        case "clear-all-console-folders":
          playSwitch();
          cachedConsoles.forEach((c) => updateConsoleFolderSetting(c.id, "", ""));
          window.Capacitor?.Plugins?.LocadouraNative?.setConsoleFolderOverride?.("__ALL__", "")
            .then((res) => {
              if (res?.folderPlan) window.__lemuroidFolderPlan = res.folderPlan;
              renderSidebarContent();
              if (isOpen) refreshFocus({ silent: true });
            })
            .catch(() => renderSidebarContent());
          showNotice("Todas as pastas restauradas para o padrão detectado pelo Lemuroid.");
          break;
        case "soon-tool":
          playSwitch();
          showNotice(`${target.dataset.toolName}: disponível em breve na Fase 2 (APK Nativo).`);
          break;
        case "reset-settings":
          playConfirm();
          resetSettings();
          renderSidebarContent();
          showNotice("Configurações restauradas para o padrão.");
          break;
        default:
          break;
      }
    });

    content.addEventListener("change", (e) => {
      const target = e.target;
      if (!target || !target.dataset) return;
      const action = target.dataset.action;

      switch (action) {
        case "toggle-sound":
          updateSetting("soundEnabled", Boolean(target.checked));
          if (target.checked) playConfirm();
          break;
        case "toggle-analog":
          updateSetting("analogAsDpad", Boolean(target.checked));
          playConfirm();
          break;
        case "toggle-rumble":
          updateSetting("rumbleEnabled", Boolean(target.checked));
          playConfirm();
          break;
        case "edit-folder":
          updateFolderSetting(target.dataset.folderKey, target.value.trim() || "Locadoura");
          showNotice("Caminho da pasta salvo.");
          break;
        case "edit-console-folder":
          updateConsoleFolderSetting(target.dataset.consoleId, target.value.trim());
          showNotice("Pasta do sistema salva.");
          break;
        default:
          break;
      }
    });

    content.addEventListener("input", (e) => {
      const target = e.target;
      if (!target || !target.dataset) return;
      const action = target.dataset.action;

      if (action === "set-volume") {
        const val = Number(target.value) / 100;
        const label = document.getElementById("sb-sound-volume-val");
        if (label) label.textContent = `${target.value}%`;
        updateSetting("soundVolume", val);
      } else if (action === "set-deadzone") {
        const val = Number(target.value) / 100;
        const label = document.getElementById("sb-deadzone-val");
        if (label) label.textContent = `${target.value}%`;
        updateSetting("deadzone", val);
      }
    });
  }
}

export function initSidebar({ onToast, onClose } = {}) {
  onToastCb = onToast || null;
  onCloseCb = onClose || null;
  bindEvents();
  renderSidebarContent();
  loadConsoles()
    .then((list) => {
      cachedConsoles = Array.isArray(list) ? list : [];
      if (activeTab === "pastas") renderSidebarContent();
    })
    .catch(() => {});
}
