// The storefront catalogue now fetches through useProductQuery (RTK Query).
// What remains here is the admin dashboard listing, which is its own slice.
import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useSearchParams } from "react-router-dom";
import { dashboardProductsAction } from "../store/actions";

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

