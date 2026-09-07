import { Helmet } from "react-helmet-async";
import { useLocation } from "react-router-dom";
import { SUPPORTED_LANGUAGES, DEFAULT_LANGUAGE } from "../../i18n";
import { stripLangPrefix } from "../../utils/languagePath";

const siteUrl = import.meta.env.VITE_SITE_URL || "https://e-shop.example.com";

const LanguageMeta = () => {
  const location = useLocation();
  const pathWithoutLang = stripLangPrefix(location.pathname) || "/";

  return (
    <Helmet>
      {SUPPORTED_LANGUAGES.map((lang) => (
        <link
          key={lang.code}
          rel="alternate"
          hrefLang={lang.code}
          href={`${siteUrl}/${lang.code}${pathWithoutLang}`}
        />
      ))}
      <link
        rel="alternate"
        hrefLang="x-default"
        href={`${siteUrl}/${DEFAULT_LANGUAGE}${pathWithoutLang}`}
      />
      <link
        rel="canonical"
        href={`${siteUrl}${location.pathname}`}
      />
    </Helmet>
  );
};

export default LanguageMeta;
