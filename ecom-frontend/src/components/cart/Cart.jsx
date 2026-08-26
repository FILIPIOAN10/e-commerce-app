import { MdArrowBack, MdShoppingCart } from "react-icons/md";
import { useSelector } from "react-redux";
import { Link } from "react-router-dom";
import ItemContent from "./ItemContent";
import CartEmpty from "./CartEmpty";
import { formatPrice } from "../../utils/formatPrice";
import Breadcrumb from "../shared/Breadcrumb";
import { useTranslation } from "react-i18next";

const Cart = () => {
    const {cart} = useSelector((state) => state.carts);
    const { t } = useTranslation("cart");
    const activeCart = cart?.filter((item) => !item.savedForLater) || [];
    const savedCart = cart?.filter((item) => item.savedForLater) || [];
    const totalPrice = activeCart?.reduce(
        (acc, cur) => acc + Number(cur?.specialPrice ?? cur?.price) * Number(cur?.quantity), 0
    );

    if(!cart || cart.length === 0)
        return <CartEmpty />;

    return(
        <div className="lg:px-14 sm:px-8 px-4 py-10">

            <Breadcrumb items={[{ label: t("home", { ns: "navbar" }), path: "/" }, { label: t("yourCart") }]} />

            <div className="flex flex-col items-center mb-12">
                <h1 data-testid="cart-heading" className="text-4xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-3">
                    <MdShoppingCart size={36} className="text-gray-700 dark:text-gray-300"/>
                   {t("yourCart")}
                </h1>
                <p className="text-lg text-gray-600 dark:text-gray-400 mt-2">{t("allSelectedItems")}</p>
            </div>
            <div className="grid md:grid-cols-5 grid-cols-4 gap-4 pb-2 font-semibold items-center">
                <div className="md:col-span-2 justify-self-start text-lg text-slate-800 dark:text-gray-200 lg:ps-4">
                    {t("product")}
                </div>
                <div className="justify-self-center text-lg text-slate-800 dark:text-gray-200">
                    {t("price")}
                </div>

                <div className="justify-self-center text-lg text-slate-800 dark:text-gray-200">
                    {t("quantity")}
                </div>

                <div className="justify-self-center text-lg text-slate-800 dark:text-gray-200">
                    {t("total")}
                </div>
            </div>

            <div className="flex flex-col">
                {activeCart.map((item) => <ItemContent key={item.productId} {...item}/>)}
            </div>

            {savedCart.length > 0 && (
                <>
                    <h2 className="text-xl font-semibold mt-8 mb-4 text-slate-700 dark:text-gray-300">{t("savedForLater")}</h2>
                    <div className="flex flex-col opacity-70">
                        {savedCart.map((item) => <ItemContent key={item.productId} {...item}/>)}
                    </div>
                </>
            )}

            <div className="border-t-[1.5px] border-slate-200 dark:border-gray-700 py-4 flex sm:flex-row sm:px-0 flex-col sm:justify-between gap-4 sticky bottom-0 z-20 bg-white/95 dark:bg-gray-900/95 backdrop-blur-sm shadow-lg">
                <div></div>
                <div className="flex text-sm gap-1 flex-col">
                    <div className="flex justify-between w-full md:text-lg text-sm font-semibold">
                        <span>{t("subtotal")}</span>
                        <span>{formatPrice(totalPrice)}</span>
                    </div>
                    <p className="text-slate-500 dark:text-gray-400">
                        {t("taxesAndShipping")}
                    </p>
                    <Link className="w-full flex justify-end" to="/checkout">
                    <button
                    disabled={activeCart.length === 0}
                    data-testid="checkout-button"
                    className="font-semibold w-75 py-2 px-4 rounded-sm  bg-custom-blue text-white  flex items-center justify-center gap-2 hover:text-gray-300 transition duration-500 disabled:opacity-50"
                    >
                    <MdShoppingCart size={20} />

                        {t("checkout")}
                    </button>
                    </Link>

                    <Link className="w-full flex justify-end text-sm" to="/guest-checkout">
                    <button
                    disabled={activeCart.length === 0}
                    data-testid="guest-checkout-button"
                    className="text-blue-500 hover:text-blue-700 dark:hover:text-blue-300 underline mt-2 disabled:opacity-50"
                    >
                        {t("checkoutAsGuest")}
                    </button>
                    </Link>


                    <Link className="flex gap-2 items-center mt-2 text-slate-500 dark:text-gray-400" to="/products">

                    <MdArrowBack/>
                    <span>
                        {t("continueShopping")}
                    </span>
                    </Link>
                </div>
            </div>
        </div>
    );
};

export default Cart;