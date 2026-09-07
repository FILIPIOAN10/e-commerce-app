import { useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { useLanguage } from "../context/LanguageContext";
import { withLanguage } from "../utils/languagePath";

/**
 * useNavigate(), language-aware — the imperative counterpart to <LangLink>, for
 * redirects after a form submit or a successful login. An absolute string path
 * gets the active `/:lang` prefix; a number (history move) or a relative path
 * passes straight through.
 *
 * Memoised so it is safe in a useEffect dependency list.
 */
export function useLangNavigate() {
  const navigate = useNavigate();
  const lang = useLanguage();

  return useCallback(
    (path, options) => {
      if (typeof path === "number") return navigate(path);
      if (typeof path === "string" && path.startsWith("/")) {
        return navigate(withLanguage(path, lang), options);
      }
      return navigate(path, options);
    },
    [navigate, lang]
  );
}

export default useLangNavigate;
