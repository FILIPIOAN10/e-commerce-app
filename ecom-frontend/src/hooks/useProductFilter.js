import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useSearchParams } from "react-router-dom";
import { dashboardProductsAction, fetchProducts } from "../store/actions";

const useProductFilter =  () => {

    const [searchParams] = useSearchParams();
    const dispatch = useDispatch();

    useEffect(() => {
        const params = new URLSearchParams();

        const currentPage = searchParams.get("page")
            ? Number(searchParams.get("page")) : 1;

        params.set("pageNumber",currentPage -1);
 

        const sortOrder = searchParams.get("sortBy") || "asc";
        const categoryParams = searchParams.get("category") || null;
        const keyword = searchParams.get("keyword") || null;
        params.set("sortBy","price");
        params.set("sortOrder",sortOrder);

        if(categoryParams){
            params.set("category",categoryParams);
        }

        if (keyword) {
            params.set("keyword",keyword);
        }

        const queryString = params.toString();

        dispatch(fetchProducts(queryString));

    }, [dispatch,searchParams]);
};



export const useDashboardProductFilter = () => {
    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const [searchParams] = useSearchParams();
    const dispatch = useDispatch();


    useEffect(() => {

        if(!user){
            return;
        }
        const params = new URLSearchParams();

        const currentPage = searchParams.get("page")
            ? Number(searchParams.get("page")) : 1;

        params.set("pageNumber", currentPage - 1);

        const queryString = params.toString();
        
        dispatch(dashboardProductsAction(queryString, isAdmin));

    }, [dispatch, searchParams,user,isAdmin]); 
};



















export default useProductFilter;