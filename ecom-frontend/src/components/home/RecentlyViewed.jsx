import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { fetchRecentlyViewedProducts } from "../../store/actions";
import ProductCard from "../shared/ProductCard";

const RecentlyViewed = () => {
    const dispatch = useDispatch();
    const { recentlyViewed } = useSelector((state) => state.products);
    const { user } = useSelector((state) => state.auth);

    useEffect(() => {
        if (user?.id) {
            dispatch(fetchRecentlyViewedProducts());
        }
    }, [dispatch, user?.id]);

    if (!user?.id || !recentlyViewed || recentlyViewed.length === 0) {
        return null;
    }

    return (
        <div className="py-5">
            <div className="flex flex-col justify-center items-center space-y-2 mb-6">
                <h1 className="text-slate-800 text-3xl font-bold dark:text-white">
 Recently Viewed
                </h1>
                <span className="text-slate-700 dark:text-gray-300">
                    Pick up where you left off
                </span>
            </div>
            <div className="pb-6 pt-4 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                {recentlyViewed.slice(0, 10).map((item, i) => (
                    <ProductCard key={i} {...item} />
                ))}
            </div>
        </div>
    );
};

export default RecentlyViewed;
