import { describe, it, expect, vi } from "vitest";

// i18n is a heavy singleton; stub it so the pure path helpers can be tested in isolation.
vi.mock("../../i18n", () => ({
  default: { resolvedLanguage: "en", language: "en" },
  SUPPORTED_LANGUAGES: [{ code: "en" }, { code: "fr" }, { code: "ro" }],
  DEFAULT_LANGUAGE: "en",
}));

const {
  normalizeLang,
  currentLang,
  langPath,
  withLanguage,
  dropFirstSegment,
  stripLangPrefix,
} = await import("../../utils/languagePath");

describe("languagePath", () => {
  it("normalizeLang reduces a region tag and rejects unknown codes", () => {
    expect(normalizeLang("en-US")).toBe("en");
    expect(normalizeLang("FR")).toBe("fr");
    expect(normalizeLang("de")).toBe("en"); // unsupported → default
    expect(normalizeLang(undefined)).toBe("en");
  });

  it("currentLang returns a supported base code", () => {
    expect(currentLang()).toBe("en");
  });

  it("withLanguage / langPath build one canonical form (no trailing slash on the root)", () => {
    expect(withLanguage("/login", "en")).toBe("/en/login");
    expect(withLanguage("", "fr")).toBe("/fr");
    expect(withLanguage("/", "fr")).toBe("/fr");
    expect(withLanguage("cart", "ro")).toBe("/ro/cart");
    expect(langPath("/login")).toBe("/en/login");
    expect(langPath("")).toBe("/en");
  });

  it("dropFirstSegment removes the leading segment whatever it is", () => {
    expect(dropFirstSegment("/en-US/login")).toBe("/login");
    expect(dropFirstSegment("/xx/products/12")).toBe("/products/12");
    expect(dropFirstSegment("/en")).toBe("");
  });

  it("stripLangPrefix removes the leading segment only when it is a real code", () => {
    expect(stripLangPrefix("/en/products")).toBe("/products");
    expect(stripLangPrefix("/ro")).toBe("/");
    expect(stripLangPrefix("/eu/rates")).toBe("/eu/rates"); // 'eu' is not a language we serve
    expect(stripLangPrefix("/products")).toBe("/products");
  });
});
