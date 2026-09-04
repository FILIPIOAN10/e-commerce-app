import { useSearchParams } from "react-router-dom";
import { useGetProductsQuery } from "../store/api/productApi";

/**
 * Builds the catalogue query string from the URL and fetches through RTK Query.
 *
 * Replaces useProductFilter, which dispatched a thunk into the shared status
 * slice. The hook returns its own status, so /products no longer shows another
 * feature's error or loses its skeleton to another feature's response.
 *
 * The URL remains the single source of truth for filter state; this only
 * translates it into the API's parameter names.
 */
export const buildProductQuery = (searchParams) => {
    const params = new URLSearchParams();

    const currentPage = searchParams.get("page") ? Number(searchParams.get("page")) : 1;
    params.set("pageNumber", String(Math.max(0, currentPage - 1)));

    // `sortBy` in the URL carries the direction, not the field: the storefront
    // only sorts by price. Renaming it is a separate change with a redirect for
    // existing links, so the mapping stays explicit here.
    params.set("sortBy", "price");
    params.set("sortOrder", searchParams.get("sortBy") || "asc");

    const category = searchParams.get("category");
    if (category) params.set("category", category);

    const keyword = searchParams.get("keyword");
    if (keyword) params.set("keyword", keyword);

    return params.toString();
};

const useProductQuery = () => {
    const [searchParams] = useSearchParams();
    return useGetProductsQuery(buildProductQuery(searchParams));
};

export default useProductQuery;
