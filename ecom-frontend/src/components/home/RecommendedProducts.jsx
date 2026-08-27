import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { fetchRecommendedProducts } from "../../store/actions";
import ProductCard from "../shared/ProductCard";
import { FaStar } from "react-icons/fa";
import { useTranslation } from "react-i18next";

const RecommendedProducts = () => {
    const dispatch = useDispatch();
    const { recommendedProducts } = useSelector((state) => state.products);
    const { user } = useSelector((state) => state.auth);
    const { t } = useTranslation("home");

    useEffect(() => {
        if (user) {
            dispatch(fetchRecommendedProducts(8));
        }
    }, [dispatch, user]);

    if (!user || !recommendedProducts || recommendedProducts.length === 0) {
        return null;
    }

    return (
        <div className="py-5">
            <div className="flex flex-col justify-center items-center space-y-2 mb-6">
                <h1 className="text-slate-800 text-3xl font-bold dark:text-white flex items-center gap-2">
                    <FaStar className="text-amber-500" />
                    {t("recommended")}
                </h1>
                <span className="text-slate-700 dark:text-gray-300">
                    {t("recommendedSubtitle")}
                </span>
            </div>
            <div className="pb-6 pt-4 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                {recommendedProducts.map((item) => (
                    <ProductCard key={item.productId} {...item} />
                ))}
            </div>
        </div>
    );
};

export default RecommendedProducts;
