import { useEffect } from "react";
import { Outlet, useParams, useLocation, Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { LanguageContext } from "../../context/LanguageContext";
import { SUPPORTED_LANGUAGES, DEFAULT_LANGUAGE } from "../../i18n";
import { dropFirstSegment } from "../../utils/languagePath";
import LanguageMeta from "./LanguageMeta";

const validCodes = SUPPORTED_LANGUAGES.map((l) => l.code);

const LanguageLayout = () => {
  const { lang } = useParams();
  const location = useLocation();
  const { i18n } = useTranslation();

  const currentLang = validCodes.includes(lang) ? lang : DEFAULT_LANGUAGE;

  useEffect(() => {
    if (i18n.language !== currentLang) {
      i18n.changeLanguage(currentLang);
    }
  }, [currentLang, i18n]);

  if (!validCodes.includes(lang)) {
    // An unknown first segment (a region tag like "en-US", or a real path that
    // was navigated without its prefix) — swap in the default language but keep
    // where the user was going, instead of dumping them on the home page.
    const rest = dropFirstSegment(location.pathname) + location.search + location.hash;
    return <Navigate to={`/${DEFAULT_LANGUAGE}${rest}`} replace />;
  }

  return (
    <LanguageContext.Provider value={currentLang}>
      <LanguageMeta />
      <Outlet />
    </LanguageContext.Provider>
  );
};

export default LanguageLayout;
