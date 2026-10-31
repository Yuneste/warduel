/**
 * Storage
 * localStorage for small per-device conveniences (nickname, mute). Private windows and
 * blocked site data make it throw or come back empty, so every access is guarded.
 */

export const storage = {
    get(key, fallback = null) {
        try {
            const value = window.localStorage.getItem(key);
            return value === null ? fallback : value;
        } catch {
            return fallback;
        }
    },

    set(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch {
            // not available: the setting just isn't remembered
        }
    }
};
