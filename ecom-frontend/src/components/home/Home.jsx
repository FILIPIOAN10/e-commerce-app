import { useDispatch, useSelector } from "react-redux";
import HeroBanner from "./HeroBanner";
import RecentlyViewed from "./RecentlyViewed";
import { useEffect } from "react";
import { fetchProducts } from "../../store/actions";
import ProductCard from "../shared/ProductCard";
import Loader from "../shared/Loader";
import { FaExclamationTriangle } from "react-icons/fa";



const Home  = () => {
    const dispatch = useDispatch();
    const {products} = useSelector( (state) => state.products);
    const { isLoading, errorMessage} = useSelector(
    (state) => state.errors
    ); 

    useEffect( () => {
        dispatch(fetchProducts());
    },[dispatch]);
    return (
        <div className="lg:px-14 sm:px-8 px-4 dark:bg-gray-950 dark:text-white min-h-screen">
            <div className="py-6">
            <HeroBanner/>
            </div>
            <RecentlyViewed />
            <div className="py-5">
                <div className="flex flex-col justify-center items-center space-y-2">
                    <h1 className="text-slate-800 text-4xl font-bold dark:text-white">Products</h1>
                        <span className="text-slate-700 dark:text-gray-300">
                            Discover our handpicked selection of top-rated items just for you!
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
                        {products &&
                        products?.slice(0,8).map((item,i) => <ProductCard key= {i} {...item}  />  
                        )}
                    </div>
                    )}
            </div>
        </div>

    )
}

export default Home;