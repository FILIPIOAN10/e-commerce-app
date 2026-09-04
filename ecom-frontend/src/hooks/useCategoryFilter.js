import { useGetCategoriesQuery } from "../store/api/productApi";
import usePagedQueryArgs from "./usePagedQueryArgs";

/**
 * Admin categories listing, paged from the URL.
 *
 * Returns its own status. The screen previously read `categoryLoader` and
 * `errorMessage` from the shared slice, which the create/update/delete category
 * thunks also write to — so a failed delete blanked the whole table behind an
 * error page instead of showing a toast over the rows that were still there.
 *
 * refetchOnMountOrArgChange because those mutations are still thunks writing
 * straight to the reducer, so a cached list could otherwise resurrect a deleted
 * category on remount.
 */
const useCategoryFilter = () =>
    useGetCategoriesQuery(usePagedQueryArgs(), { refetchOnMountOrArgChange: true });

export default useCategoryFilter;
