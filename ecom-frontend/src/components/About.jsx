import { useEffect, useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import ProductCard from "./shared/ProductCard";
import { fetchBestSellers } from "../store/actions";
import Loader from "./shared/Loader";
import { useTranslation } from "react-i18next";

const About = () => {
    const dispatch = useDispatch();
    const { bestSellers } = useSelector((state) => state.products);
    const hasFetched = useRef(false);
    const [fetchDone, setFetchDone] = useState(false);
    const { t } = useTranslation("about");

    useEffect(() => {
        if (!hasFetched.current) {
            hasFetched.current = true;
            dispatch(fetchBestSellers(3)).finally(() => setFetchDone(true));
        }
    }, [dispatch]);

    return (
        <div className="max-w-7xl mx-auto px-4 py-8 dark:bg-gray-950 dark:text-white min-h-screen">
            <h1 className="text-slate-800 text-4xl font-bold text-center mb-12 dark:text-white">
                {t("aboutUs")}
            </h1>
            <div className="flex flex-col lg:flex-row justify-between items-center mb-12">
                <div className="w-full md:w-1/2 text-center md:text-left">
                    <p className="text-lg mb-4">
                        {t("aboutDescription")}
                    </p>
                </div>
                <div className="w-full md:w-1/2 mb-6 md:mb-0">
                    <img
                        src="https://embarkx.com/sample/placeholder.png"
                        alt="About Us"
                        className="w-full h-auto rounded-lg shadow-lg transform transition-transform duration-300 hover:scale-105"
                    />
                </div>
            </div>

            <div className="py-7 space-y-8">
                <h1 className="text-slate-800 text-4xl font-bold text-center dark:text-white">{t("ourProducts")}</h1>
                {bestSellers && bestSellers.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {bestSellers.slice(0, 3).map((product) => (
                            <ProductCard
                                key={product.productId}
                                {...product}
                                about
                            />
                        ))}
                    </div>
                ) : fetchDone ? (
                    <p className="text-center text-gray-500 dark:text-gray-400 py-10">{t("noProductsAvailable")}</p>
                ) : (
                    <div className="flex justify-center py-10">
                        <Loader />
                    </div>
                )}
            </div>
        </div>
    );
};

export default About;
