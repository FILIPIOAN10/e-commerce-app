import { Link } from "react-router-dom";
import { useLanguage } from "../../context/LanguageContext";
import { withLanguage } from "../../utils/languagePath";

const LangLink = ({ to, ...props }) => {
  const lang = useLanguage();
  const target = typeof to === "string" && to.startsWith("/") ? withLanguage(to, lang) : to;
  return <Link to={target} {...props} />;
};

export default LangLink;
