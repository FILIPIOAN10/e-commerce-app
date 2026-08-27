import { useDispatch, useSelector } from "react-redux";
import HeroBanner from "./HeroBanner";
import RecentlyViewed from "./RecentlyViewed";
import RecommendedProducts from "./RecommendedProducts";
import HomeSection from "./HomeSection";
import { useEffect } from "react";
import { fetchProducts, fetchBestSellers, fetchNewArrivals, fetchOnSaleProducts } from "../../store/actions";
import ProductCard from "../shared/ProductCard";
import Loader from "../shared/Loader";
import { FaExclamationTriangle, FaFireAlt, FaTags, FaBoxOpen } from "react-icons/fa";
import { useTranslation } from "react-i18next";



const Home  = () => {
    const dispatch = useDispatch();
    const {products, bestSellers, newArrivals, onSaleProducts} = useSelector( (state) => state.products);
    const { isLoading, errorMessage} = useSelector(
    (state) => state.errors
    );
    const { t } = useTranslation("home");

    useEffect( () => {
        dispatch(fetchProducts());
        dispatch(fetchBestSellers(8));
        dispatch(fetchNewArrivals(8));
        dispatch(fetchOnSaleProducts(8));
    },[dispatch]);
    return (
        <div className="lg:px-14 sm:px-8 px-4 dark:bg-gray-950 dark:text-white min-h-screen">
            <div className="py-6">
            <HeroBanner/>
            </div>
            <RecentlyViewed />
            <RecommendedProducts />
            <HomeSection
                title={t("bestSellers")}
                subtitle={t("bestSellersSubtitle")}
                icon={<FaFireAlt className="text-orange-500" />}
                products={bestSellers}
            />
            <HomeSection
                title={t("newArrivals")}
                subtitle={t("newArrivalsSubtitle")}
                icon={<FaBoxOpen className="text-blue-500" />}
                products={newArrivals}
            />
            <HomeSection
                title={t("onSale")}
                subtitle={t("onSaleSubtitle")}
                icon={<FaTags className="text-green-500" />}
                products={onSaleProducts}
            />
            <div className="py-5">
                <div className="flex flex-col justify-center items-center space-y-2">
                    <h1 className="text-slate-800 text-4xl font-bold dark:text-white">{t("products")}</h1>
                        <span className="text-slate-700 dark:text-gray-300">
                            {t("productsSubtitle")}
                        </span>
                   
                </div>
                {isLoading ? (
                    <Loader/>
                ) : errorMessage ?  (
                <div className="flex justify-center items-center h-50">
                    <FaExclamationTriangle className="text-slate-800 text-3xl mr-2 dark:text-white"/>
                    <span className="text-slate-800 text-lg font-medium dark:text-white">
                        {errorMessage}
                    </span>
                </div>
                ) : (
            
                    <div className="pb-6 pt-14 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                        {products?.slice(0,8).map((item) => <ProductCard key={item.productId} {...item} />
                        )}
                    </div>
                    )}
            </div>
        </div>

    )
}

export default Home;