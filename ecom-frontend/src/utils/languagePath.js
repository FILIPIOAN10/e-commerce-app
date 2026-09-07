import i18n, { SUPPORTED_LANGUAGES, DEFAULT_LANGUAGE } from "../i18n";

// The single place any "/:lang/..." path is stripped or built. Everything else
// (actions, the API client, the layout, the meta tags, the switcher) goes
// through here, so a region-tagged code like "en-US" or a stale detector value
// can never produce a "/en-US/login" that the router then bounces to "/en".

const CODES = SUPPORTED_LANGUAGES.map((l) => l.code);

/** A raw language tag reduced to a supported base code, or the default. */
export const normalizeLang = (raw) => {
  const base = String(raw || "").toLowerCase().split("-")[0];
  return CODES.includes(base) ? base : DEFAULT_LANGUAGE;
};

/** The active UI language as a supported code — safe to put in a URL. */
export const currentLang = () =>
  normalizeLang(i18n.resolvedLanguage || i18n.language);

/** Build a language-prefixed path. `langPath("/login")` → "/en/login". */
export const langPath = (path = "") => withLanguage(path, currentLang());

/**
 * Prefix `path` with `lang`. One canonical form so the switcher and the
 * canonical tag never disagree: the root of a language is `/en`, never `/en/`.
 */
export const withLanguage = (path = "", lang = DEFAULT_LANGUAGE) => {
  if (!path || path === "/") return `/${lang}`;
  return `/${lang}${path.startsWith("/") ? path : `/${path}`}`;
};

/** Drop the first path segment (whatever it is) — used when redirecting an unknown :lang. */
export const dropFirstSegment = (pathname = "") =>
  pathname.replace(/^\/[^/]+/, "");

/** Drop the leading segment only when it is a real language code. */
export const stripLangPrefix = (pathname = "") => {
  const [, first, ...rest] = pathname.split("/");
  return CODES.includes(first) ? `/${rest.join("/")}` : pathname;
};
