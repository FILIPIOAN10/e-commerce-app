import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";

const localeFiles = import.meta.glob("../public/locales/**/*.json", { eager: true });

const resources = {};
for (const [path, module] of Object.entries(localeFiles)) {
  const match = path.match(/\/locales\/([^/]+)\/([^/]+)\.json$/);
  if (match) {
    const [, lang, ns] = match;
    if (!resources[lang]) resources[lang] = {};
    resources[lang][ns] = module.default;
  }
}

export const SUPPORTED_LANGUAGES = [
  { code: "en", label: "English", flag: "🇬🇧" },
  { code: "fr", label: "Français", flag: "🇫🇷" },
  { code: "ro", label: "Română", flag: "🇷🇴" },
];

export const DEFAULT_LANGUAGE = "en";

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: DEFAULT_LANGUAGE,
    supportedLngs: SUPPORTED_LANGUAGES.map((l) => l.code),
    ns: ["common", "navbar", "home", "product", "cart", "auth", "about", "contact", "wishlist", "notFound", "userMenu"],
    defaultNS: "common",
    resources,
    detection: {
      order: ["localStorage", "navigator"],
      caches: ["localStorage"],
    },
    interpolation: { escapeValue: false },
    react: { useSuspense: false },
  });

export default i18n;
