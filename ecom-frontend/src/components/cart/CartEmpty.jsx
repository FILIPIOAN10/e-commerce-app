import { MdArrowBack, MdShoppingCart } from "react-icons/md";
import EmptyState from "../shared/EmptyState";
import { useTranslation } from "react-i18next";

const CartEmpty = () => {
    const { t } = useTranslation("cart");
    return (
        <EmptyState
            icon={MdShoppingCart}
            title={t("cartEmpty")}
            message={t("cartEmptyMessage")}
            action={{ label: t("startShopping"), path: "/", icon: MdArrowBack }}
        />
    );
};

export default CartEmpty;