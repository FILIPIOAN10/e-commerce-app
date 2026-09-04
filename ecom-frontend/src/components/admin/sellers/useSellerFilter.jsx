import { useGetSellersQuery } from "../../../store/api/adminApi";
import usePagedQueryArgs from "../../../hooks/usePagedQueryArgs";

/**
 * Sellers listing, paged from the URL. Returns its own status so the screen no
 * longer reads the shared error slice, where any other admin page's failure
 * would have been rendered here.
 */
const useSellerFilter = () => useGetSellersQuery(usePagedQueryArgs());

export default useSellerFilter;
