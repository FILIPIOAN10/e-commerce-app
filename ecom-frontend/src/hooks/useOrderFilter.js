import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import {  useSearchParams } from "react-router-dom";
import { getOrdersForDashboard } from "../store/actions";

const useOrderFilter =  () => {
    //access the search params from url
    const [searchParams] = useSearchParams();
    // call actions
    const dispatch = useDispatch();

    const {user} = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    useEffect(() => {
        if(!user){
            return;
        }
        const params = new URLSearchParams();

        const currentPage = searchParams.get("page")
            ? Number(searchParams.get("page")) : 1;

        params.set("pageNumber",currentPage -1);
 

        const queryString = params.toString();
        console.log("QUERY STRING",queryString);

        dispatch(getOrdersForDashboard(queryString,isAdmin));

    }, [dispatch,searchParams,user,isAdmin]);
};

export default useOrderFilter;