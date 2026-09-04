/**
 * localStorage access that cannot take the application down.
 *
 * Both guards matter because the hydration reads happen at *module scope* — in
 * store.js and authReducer.js, before React mounts. A throw there means
 * main.jsx never runs, so the page stays permanently blank and no error
 * boundary can catch it. The two ways that happens:
 *
 *   - the stored value is not valid JSON: a truncated write, a partial save
 *     after a quota error, a hand-edited devtools entry;
 *   - `localStorage` is absent or throws on access: blocked site data, a
 *     private window, or a test environment that does not expose it (jsdom
 *     under Node 26 leaves window.localStorage undefined).
 *
 * A bad value is also removed, so the next load starts clean rather than
 * tripping over the same entry forever.
 */
const store = () => {
    try {
        return typeof localStorage !== "undefined" && localStorage ? localStorage : null;
    } catch {
        // Accessing the property itself throws when the browser blocks site data.
        return null;
    }
};

export const readJson = (key, fallback = null) => {
    const s = store();
    if (!s) return fallback;
    try {
        const raw = s.getItem(key);
        return raw ? JSON.parse(raw) : fallback;
    } catch {
        try {
            s.removeItem(key);
        } catch {
            // Nothing further we can do; the fallback still applies.
        }
        return fallback;
    }
};

export const writeJson = (key, value) => {
    const s = store();
    if (!s) return false;
    try {
        s.setItem(key, JSON.stringify(value));
        return true;
    } catch {
        return false;
    }
};

/** Plain-string variants, for values that were never JSON (e.g. the theme). */
export const readString = (key, fallback = null) => {
    const s = store();
    if (!s) return fallback;
    try {
        const raw = s.getItem(key);
        return raw === null ? fallback : raw;
    } catch {
        return fallback;
    }
};

export const writeString = (key, value) => {
    const s = store();
    if (!s) return false;
    try {
        s.setItem(key, value);
        return true;
    } catch {
        return false;
    }
};

export const removeKey = (key) => {
    const s = store();
    if (!s) return;
    try {
        s.removeItem(key);
    } catch {
        // ignore
    }
};
