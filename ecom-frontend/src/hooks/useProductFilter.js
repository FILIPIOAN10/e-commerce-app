import { useSelector } from "react-redux";
import { useGetDashboardProductsQuery } from "../store/api/adminApi";
import usePagedQueryArgs from "./usePagedQueryArgs";

/**
 * Admin/seller product listing.
 *
 * The storefront catalogue moved to useProductQuery (RTK Query) first; this is
 * the dashboard equivalent. Admins list every product, sellers only their own,
 * so the role selects the endpoint. Returns its own status, so AdminProducts no
 * longer reads the shared slice.
 */
export const useDashboardProductFilter = () => {
    const { user } = useSelector((state) => state.auth);
    const isAdmin = Boolean(user?.roles?.includes("ROLE_ADMIN"));
    const queryString = usePagedQueryArgs();

    return useGetDashboardProductsQuery({ isAdmin, queryString }, { skip: !user });
};

export default useDashboardProductFilter;
