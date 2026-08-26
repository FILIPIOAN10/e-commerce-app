import { Link } from "react-router-dom";
import { FaHome, FaExclamationTriangle } from "react-icons/fa";
import { useTranslation } from "react-i18next";

const NotFound = () => {
    const { t } = useTranslation("notFound");
    return (
        <div className="min-h-[calc(100vh-64px)] flex flex-col items-center justify-center dark:bg-gray-950 px-4">
            <FaExclamationTriangle className="text-6xl text-slate-300 dark:text-gray-600 mb-6" />
            <h1 className="text-6xl font-bold text-slate-800 dark:text-white mb-2">{t("404")}</h1>
            <p className="text-xl text-slate-600 dark:text-gray-400 mb-6">
                {t("notFoundMessage")}
            </p>
            <Link
                to="/"
                className="flex items-center gap-2 bg-button-gradient text-white px-6 py-3 rounded-md font-medium hover:opacity-90 transition"
            >
                <FaHome /> {t("backToHome")}
            </Link>
        </div>
    );
};

export default NotFound;
