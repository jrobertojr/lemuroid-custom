/**
 * Pure Web Audio API UI sound synthesizer for Locadoura.
 * Supports 3 profiles: "retro80s" (chiptune), "modern" (soft), "arcade" (coin-op).
 */

import { getSettings } from "./state.js";

let audioCtx = null;
let unlockBound = false;

function getAudioContextClass() {
  if (typeof window === "undefined") return null;
  return window.AudioContext || window.webkitAudioContext || null;
}

/**
 * Ensures AudioContext exists and is resumed (call on user interaction).
 */
export function unlockAudio() {
  const Ctx = getAudioContextClass();
  if (!Ctx) return null;
  if (!audioCtx) {
    try {
      audioCtx = new Ctx();
    } catch {
      return null;
    }
  }
  if (audioCtx.state === "suspended") {
    audioCtx.resume().catch(() => {});
  }
  return audioCtx;
}

function bindAutoUnlock() {
  if (unlockBound || typeof window === "undefined") return;
  unlockBound = true;
  const handler = () => {
    unlockAudio();
  };
  window.addEventListener("pointerdown", handler, { passive: true, once: true });
  window.addEventListener("keydown", handler, { passive: true, once: true });
}

bindAutoUnlock();

function playToneSequence(steps, overrideOptions = {}) {
  const settings = getSettings();
  const enabled =
    overrideOptions.soundEnabled !== undefined
      ? overrideOptions.soundEnabled
      : settings.soundEnabled;
  if (!enabled) return;

  const volume =
    overrideOptions.soundVolume !== undefined
      ? overrideOptions.soundVolume
      : settings.soundVolume;
  if (volume <= 0.001) return;

  const ctx = unlockAudio();
  if (!ctx) return;

  const now = ctx.currentTime;
  for (const step of steps) {
    const start = now + (step.delay || 0);
    const dur = step.duration || 0.06;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = step.type || "sine";
    osc.frequency.setValueAtTime(step.freq, start);
    if (step.freqEnd && step.freqEnd !== step.freq) {
      osc.frequency.exponentialRampToValueAtTime(
        Math.max(20, step.freqEnd),
        start + dur
      );
    }

    const peak = Math.max(0.0001, Math.min(0.45, (step.gain || 0.25) * volume));
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.linearRampToValueAtTime(peak, start + Math.min(0.01, dur * 0.2));
    gain.gain.exponentialRampToValueAtTime(0.0001, start + dur);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(start);
    osc.stop(start + dur + 0.01);
  }
}

const PROFILE_RECIPES = {
  retro80s: {
    move: [{ freq: 520, freqEnd: 620, duration: 0.045, type: "square", gain: 0.14 }],
    confirm: [
      { freq: 523.25, duration: 0.055, type: "square", gain: 0.16, delay: 0 },
      { freq: 783.99, duration: 0.09, type: "square", gain: 0.18, delay: 0.05 },
    ],
    back: [
      { freq: 440, freqEnd: 260, duration: 0.08, type: "triangle", gain: 0.2 },
    ],
    switch: [
      { freq: 392, duration: 0.04, type: "square", gain: 0.14, delay: 0 },
      { freq: 587.33, duration: 0.06, type: "triangle", gain: 0.18, delay: 0.035 },
    ],
    error: [
      { freq: 180, freqEnd: 120, duration: 0.11, type: "sawtooth", gain: 0.18, delay: 0 },
      { freq: 140, freqEnd: 95, duration: 0.13, type: "sawtooth", gain: 0.16, delay: 0.1 },
    ],
  },
  modern: {
    move: [{ freq: 680, freqEnd: 740, duration: 0.035, type: "sine", gain: 0.16 }],
    confirm: [
      { freq: 587.33, duration: 0.06, type: "sine", gain: 0.18, delay: 0 },
      { freq: 880, duration: 0.1, type: "triangle", gain: 0.16, delay: 0.045 },
    ],
    back: [{ freq: 480, freqEnd: 320, duration: 0.075, type: "sine", gain: 0.18 }],
    switch: [
      { freq: 440, duration: 0.04, type: "sine", gain: 0.14, delay: 0 },
      { freq: 660, duration: 0.065, type: "sine", gain: 0.15, delay: 0.03 },
    ],
    error: [
      { freq: 240, freqEnd: 170, duration: 0.12, type: "triangle", gain: 0.22 },
    ],
  },
  arcade: {
    move: [{ freq: 740, freqEnd: 880, duration: 0.035, type: "triangle", gain: 0.2 }],
    confirm: [
      { freq: 987.77, duration: 0.055, type: "square", gain: 0.15, delay: 0 },
      { freq: 1318.5, duration: 0.14, type: "square", gain: 0.16, delay: 0.055 },
    ],
    back: [
      { freq: 520, freqEnd: 240, duration: 0.09, type: "square", gain: 0.14 },
    ],
    switch: [
      { freq: 659.25, duration: 0.04, type: "square", gain: 0.13, delay: 0 },
      { freq: 880, duration: 0.055, type: "square", gain: 0.14, delay: 0.035 },
    ],
    error: [
      { freq: 200, freqEnd: 110, duration: 0.15, type: "square", gain: 0.16 },
    ],
  },
};

function playCue(cueName, overrideOptions = {}) {
  const settings = getSettings();
  const profileId = overrideOptions.soundProfile || settings.soundProfile || "retro80s";
  const profile = PROFILE_RECIPES[profileId] || PROFILE_RECIPES.retro80s;
  const steps = profile[cueName];
  if (!steps) return;
  playToneSequence(steps, overrideOptions);
}

export function playMove() {
  playCue("move");
}

export function playConfirm() {
  playCue("confirm");
}

export function playBack() {
  playCue("back");
}

export function playSwitch() {
  playCue("switch");
}

export function playError() {
  playCue("error");
}

/**
 * Plays a short confirmation chime regardless of soundEnabled (for the "Testar Som" button),
 * or respecting the current profile & volume.
 */
export function testSound(profileOverride) {
  playCue("confirm", {
    soundEnabled: true,
    soundProfile: profileOverride,
  });
}
