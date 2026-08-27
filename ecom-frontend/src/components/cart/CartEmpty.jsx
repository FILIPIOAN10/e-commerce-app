import { MdArrowBack, MdShoppingCart } from "react-icons/md";
import EmptyState from "../shared/EmptyState";
import { useTranslation } from "react-i18next";
import { useLanguage } from "../../context/LanguageContext";

const CartEmpty = () => {
    const { t } = useTranslation("cart");
    const lang = useLanguage();
    return (
        <EmptyState
            icon={MdShoppingCart}
            title={t("cartEmpty")}
            message={t("cartEmptyMessage")}
            action={{ label: t("startShopping"), path: `/${lang}`, icon: MdArrowBack }}
        />
    );
};

export default CartEmpty;