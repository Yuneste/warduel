/**
 * Sound
 * Every sound is synthesized with the Web Audio API: no audio files to load or license.
 * Browsers only allow audio after a user gesture, so the context is created on the first tap or key.
 */

import { storage } from './storage.js';

const MUTE_KEY = 'mathwars.muted';
const VOLUME = 0.5;

let ctx = null;
let master = null;
let noiseBuffer = null;
let muted = storage.get(MUTE_KEY) === '1';

function ready() {
    if (muted || !ctx) return false;
    if (ctx.state === 'suspended') ctx.resume();
    return true;
}

// One short note with a quick attack and a soft exponential release
function note(freq, start, duration, { type = 'sine', gain = 0.25, glideTo = null } = {}) {
    const t = ctx.currentTime + start;
    const osc = ctx.createOscillator();
    const amp = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(freq, t);
    if (glideTo) osc.frequency.exponentialRampToValueAtTime(glideTo, t + duration);
    amp.gain.setValueAtTime(0.0001, t);
    amp.gain.exponentialRampToValueAtTime(gain, t + 0.012);
    amp.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    osc.connect(amp).connect(master);
    osc.start(t);
    osc.stop(t + duration + 0.05);
}

// Filtered noise: a chalk tap when short, a chalk scratch when the filter sweeps
function noise(start, duration, { from = 2500, to = from, q = 1.2, gain = 0.2 } = {}) {
    const t = ctx.currentTime + start;
    const src = ctx.createBufferSource();
    src.buffer = noiseBuffer;
    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.Q.value = q;
    filter.frequency.setValueAtTime(from, t);
    filter.frequency.exponentialRampToValueAtTime(to, t + duration);
    const amp = ctx.createGain();
    amp.gain.setValueAtTime(0.0001, t);
    amp.gain.exponentialRampToValueAtTime(gain, t + 0.008);
    amp.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    src.connect(filter).connect(amp).connect(master);
    src.start(t, Math.random() * 0.5);
    src.stop(t + duration + 0.05);
}

const NOTES = { C5: 523.25, D5: 587.33, E5: 659.25, G5: 783.99, A5: 880, B5: 987.77, C6: 1046.5, E6: 1318.5,
    A4: 440, F4: 349.23, D4: 293.66, A3: 220 };

export const sound = {
    // Call from any user gesture; safe to call repeatedly
    unlock() {
        if (ctx) {
            if (ctx.state === 'suspended') ctx.resume();
            return;
        }
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass) return;
        ctx = new AudioContextClass();
        master = ctx.createGain();
        master.gain.value = VOLUME;
        master.connect(ctx.destination);
        noiseBuffer = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
        const data = noiseBuffer.getChannelData(0);
        for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
    },

    isMuted() {
        return muted;
    },

    setMuted(value) {
        muted = value;
        storage.set(MUTE_KEY, value ? '1' : '0');
    },

    tap() {
        if (!ready()) return;
        noise(0, 0.035, { from: 3200, q: 2, gain: 0.18 });
    },

    correct() {
        if (!ready()) return;
        noise(0, 0.12, { from: 1800, to: 4200, q: 3, gain: 0.12 });
        note(NOTES.E5, 0.02, 0.12, { gain: 0.18 });
        note(NOTES.B5, 0.09, 0.2, { gain: 0.18 });
    },

    wrong() {
        if (!ready()) return;
        note(150, 0, 0.22, { type: 'triangle', gain: 0.22, glideTo: 105 });
    },

    countdown(step) {
        if (!ready()) return;
        note(NOTES.A4, 0, 0.12, { type: 'triangle', gain: 0.2 });
        if (step === 3) note(NOTES.A3, 0, 0.12, { type: 'sine', gain: 0.12 });
    },

    go() {
        if (!ready()) return;
        note(NOTES.A5, 0, 0.3, { type: 'triangle', gain: 0.22 });
        noise(0, 0.18, { from: 1500, to: 5000, q: 2, gain: 0.08 });
    },

    tick() {
        if (!ready()) return;
        noise(0, 0.03, { from: 5200, q: 6, gain: 0.12 });
        note(1200, 0, 0.04, { type: 'square', gain: 0.03 });
    },

    opponentPoint() {
        if (!ready()) return;
        note(NOTES.D4, 0, 0.08, { type: 'sine', gain: 0.07 });
    },

    streak(length) {
        if (!ready()) return;
        const steps = length >= 10 ? [NOTES.C5, NOTES.E5, NOTES.G5, NOTES.C6, NOTES.E6] : [NOTES.C5, NOTES.E5, NOTES.G5];
        steps.forEach((freq, i) => note(freq, 0.18 + i * 0.07, 0.14, { type: 'triangle', gain: 0.14 }));
    },

    // Someone wants to play: a bright double ding, twice
    challenge() {
        if (!ready()) return;
        [0, 0.5].forEach(offset => {
            note(NOTES.E6, offset, 0.16, { type: 'sine', gain: 0.16 });
            note(NOTES.B5, offset + 0.14, 0.24, { type: 'sine', gain: 0.14 });
        });
    },

    matchFound() {
        if (!ready()) return;
        note(NOTES.G5, 0, 0.1, { gain: 0.16 });
        note(NOTES.C6, 0.1, 0.18, { gain: 0.16 });
    },

    win() {
        if (!ready()) return;
        [NOTES.C5, NOTES.E5, NOTES.G5, NOTES.C6].forEach((freq, i) =>
            note(freq, i * 0.11, i === 3 ? 0.6 : 0.16, { type: 'triangle', gain: 0.2 }));
        noise(0.33, 0.5, { from: 2000, to: 7000, q: 0.8, gain: 0.06 });
    },

    lose() {
        if (!ready()) return;
        [NOTES.A4, NOTES.F4, NOTES.D4].forEach((freq, i) =>
            note(freq, i * 0.16, i === 2 ? 0.5 : 0.18, { type: 'sine', gain: 0.18 }));
    },

    draw() {
        if (!ready()) return;
        note(NOTES.E5, 0, 0.18, { type: 'triangle', gain: 0.16 });
        note(NOTES.E5, 0.2, 0.3, { type: 'triangle', gain: 0.16 });
    }
};
