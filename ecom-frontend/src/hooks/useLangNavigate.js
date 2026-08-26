import { useNavigate } from "react-router-dom";
import { useLanguage } from "../context/LanguageContext";

export const useLangNavigate = () => {
  const navigate = useNavigate();
  const lang = useLanguage();

  return (path, options) => {
    const target = path.startsWith("/") ? `/${lang}${path}` : path;
    navigate(target, options);
  };
};
