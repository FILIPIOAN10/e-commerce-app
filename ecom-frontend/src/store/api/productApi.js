import { apiSlice } from "./apiSlice";

/**
 * Catalogue reads backed by RTK Query.
 *
 * Why this slice first: /products is where the shared-status bug actually
 * bit. Products.jsx read the one global { isLoading, errorMessage }, so a
 * failure in an unrelated feature — notifications, say — replaced the entire
 * product grid with that feature's error, and a categories request finishing
 * first used to clear the skeleton while the products request was still in
 * flight. Each hook below owns its own loading and error state, so neither
 * can happen.
 *
 * Results are still synced into the existing `products` reducer, following
 * cartApi: Navbar, ProductCard and the compare list all read that slice, and
 * duplicating catalogue state in two places would be worse than the problem
 * being solved. RTK Query owns request status; the reducer stays the store of
 * record for the data itself.
 */

/**
 * Keyword searches go to the semantic endpoint, but only when no category is
 * pinned — with a category the plain listing already filters correctly, and
 * the semantic ranking would fight it. Extracted so the URL rule lives in one
 * place rather than inside a thunk.
 */
export const buildProductsUrl = (queryString = "") => {
    const searchParams = new URLSearchParams(queryString);
    const keyword = searchParams.get("keyword");
    const category = searchParams.get("category");

    if (keyword && !category) {
        searchParams.delete("keyword");
        searchParams.set("q", keyword);
        searchParams.set("semantic", "true");
        return `/public/products/search?${searchParams.toString()}`;
    }
    return `/public/products?${queryString}`;
};

const toPagination = (data) => ({
    pageNumber: data.pageNumber,
    pageSize: data.pageSize,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    lastPage: data.lastPage,
});

const productApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getProducts: builder.query({
            query: (queryString = "") => ({ url: buildProductsUrl(queryString), method: "get" }),
            providesTags: ["Product"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    dispatch({ type: "FETCH_PRODUCTS", payload: data.content, ...toPagination(data) });
                } catch {
                    // The hook surfaces the error to the screen that asked for it.
                }
            },
        }),

        getProductById: builder.query({
            query: (productId) => ({ url: `/public/products/${productId}`, method: "get" }),
            providesTags: (result, error, productId) => [{ type: "Product", id: productId }],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    dispatch({ type: "FETCH_PRODUCT", payload: data });
                } catch {
                    // The detail page renders its own not-found state from the hook.
                }
            },
        }),

        getCategories: builder.query({
            query: (queryString = "") => ({
                url: queryString ? `/public/categories?${queryString}` : "/public/categories",
                method: "get",
            }),
            providesTags: ["Category"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    dispatch({ type: "FETCH_CATEGORIES", payload: data.content, ...toPagination(data) });
                } catch {
                    // Previously dispatched IS_ERROR, which put a categories
                    // failure on whatever screen happened to be mounted.
                }
            },
        }),
    }),
});

export const {
    useGetProductsQuery,
    useGetProductByIdQuery,
    useGetCategoriesQuery,
} = productApi;
export default productApi;
