import { Link } from "react-router-dom";
import { useLanguage } from "../../context/LanguageContext";

const LangLink = ({ to, ...props }) => {
  const lang = useLanguage();
  const target = to.startsWith("/") ? `/${lang}${to}` : to;
  return <Link to={target} {...props} />;
};

export default LangLink;
