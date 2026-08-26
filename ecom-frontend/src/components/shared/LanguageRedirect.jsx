import { Navigate } from "react-router-dom";
import { DEFAULT_LANGUAGE } from "../../i18n";

const LanguageRedirect = () => {
  return <Navigate to={`/${DEFAULT_LANGUAGE}`} replace />;
};

export default LanguageRedirect;
