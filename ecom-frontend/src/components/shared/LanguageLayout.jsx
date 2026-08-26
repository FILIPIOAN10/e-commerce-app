import { useEffect } from "react";
import { Outlet, useParams, Navigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { LanguageContext } from "../../context/LanguageContext";
import { SUPPORTED_LANGUAGES, DEFAULT_LANGUAGE } from "../../i18n";

const validCodes = SUPPORTED_LANGUAGES.map((l) => l.code);

const LanguageLayout = () => {
  const { lang } = useParams();
  const { i18n } = useTranslation();

  const currentLang = validCodes.includes(lang) ? lang : DEFAULT_LANGUAGE;

  useEffect(() => {
    if (i18n.language !== currentLang) {
      i18n.changeLanguage(currentLang);
    }
  }, [currentLang, i18n]);

  if (!validCodes.includes(lang)) {
    return <Navigate to={`/${DEFAULT_LANGUAGE}`} replace />;
  }

  return (
    <LanguageContext.Provider value={currentLang}>
      <Outlet />
    </LanguageContext.Provider>
  );
};

export default LanguageLayout;
