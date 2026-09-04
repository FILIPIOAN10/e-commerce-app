import { useSelector } from "react-redux";
import { useGetDashboardOrdersQuery } from "../store/api/adminApi";
import usePagedQueryArgs from "./usePagedQueryArgs";

/**
 * Dashboard orders. Admins see every order, sellers only their own, so the role
 * selects the endpoint — and a customer with neither role must not fetch at
 * all, which `skip` expresses directly rather than as an early return inside an
 * effect.
 */
const useOrderFilter = () => {
    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const isSeller = Boolean(user?.roles?.includes("ROLE_SELLER"));
    const queryString = usePagedQueryArgs();

    return useGetDashboardOrdersQuery(
        { isAdmin, queryString },
        { skip: !user || (!isAdmin && !isSeller) },
    );
};

export default useOrderFilter;
