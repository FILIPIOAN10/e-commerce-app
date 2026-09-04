import { useSearchParams } from "react-router-dom";

/**
 * The admin tables all page the same way: `?page=` in the URL is 1-based for
 * the operator, the API is 0-based. This was copied into three separate filter
 * hooks; keeping it in one place means a paging change lands once.
 */
export const toPageQuery = (searchParams) => {
    const params = new URLSearchParams();
    const currentPage = searchParams.get("page") ? Number(searchParams.get("page")) : 1;
    params.set("pageNumber", String(Math.max(0, currentPage - 1)));
    return params.toString();
};

const usePagedQueryArgs = () => {
    const [searchParams] = useSearchParams();
    return toPageQuery(searchParams);
};

export default usePagedQueryArgs;
