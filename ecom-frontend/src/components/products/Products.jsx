import { FaExclamationTriangle } from "react-icons/fa";
import ProductCard from "../shared/ProductCard";
import { useDispatch, useSelector } from "react-redux";
import { useEffect } from "react";
import { fetchCategories } from "../../store/actions";
import Filter from "./Filter";
import useProductFilter from "../../hooks/useProductFilter";
import ProductSkeleton from "../shared/ProductSkeleton";
import Breadcrumb from "../shared/Breadcrumb";
import Paginations from "../shared/Paginations";


const Products  = () => {

    const { isLoading, errorMessage} = useSelector(
        (state) => state.errors
    ); 

    const {products,categories,pagination} = useSelector(
        (state) => state.products
    )
    const dispatch = useDispatch();
    useProductFilter();

    useEffect( () => {
        dispatch(fetchCategories());
    }, [dispatch]);
  
    return (
        <div className="lg:px-14 sm:px-8 px-4 py-14 2xl:w-[90%] 2xl:mx-auto dark:bg-gray-950 dark:text-white min-h-screen">
            <Breadcrumb items={[{ label: "Home", path: "/" }, { label: "Products" }]} />
            <Filter categories = {categories ? categories : []}/>
            { isLoading ? (
                    <ProductSkeleton />
            ) : errorMessage ? (
                <div className="flex justify-center items-center h-50">
                    <FaExclamationTriangle className="text-slate-800 text-3xl mr-2 dark:text-white"/>
                    <span className="text-slate-800 text-lg font-medium dark:text-white">
                        {errorMessage}
                    </span>
                </div>
            ) : (
                <div className="min-h-175">
                    <div className="pb-6 pt-14 grid 2xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 gap-y-6 gap-x-6">
                        {products && 
                        products.map((item) => <ProductCard key={item.productId} {...item} />
                        )}
                    </div>
                    <div className="flex justify-center pt-10"> 
                        <Paginations 
                         numberOfPage = {pagination?.totalPages}
                         totalProducts = {pagination?.totalElements}/>
                    </div>
                </div>
            )}
        </div>
    )
}
export default Products;
