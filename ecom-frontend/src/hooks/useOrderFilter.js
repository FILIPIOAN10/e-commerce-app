import { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { useSearchParams } from "react-router-dom";
import { getOrdersForDashboard } from "../store/actions";

const useOrderFilter = () => {
    const [searchParams] = useSearchParams();
    const dispatch = useDispatch();

    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    // ✅ adaugă seller
    const isSeller = Boolean(user?.roles?.includes("ROLE_SELLER"));

    useEffect(() => {
        if (!user) return;
        // ✅ doar admin sau seller fetch-uiesc comenzile din dashboard
        if (!isAdmin && !isSeller) return;

        const params = new URLSearchParams();
        const currentPage = searchParams.get("page")
            ? Number(searchParams.get("page")) : 1;
        params.set("pageNumber", currentPage - 1);

        const queryString = params.toString();
        dispatch(getOrdersForDashboard(queryString, isAdmin));

    }, [dispatch, searchParams, user, isAdmin, isSeller]);
};

export default useOrderFilter;