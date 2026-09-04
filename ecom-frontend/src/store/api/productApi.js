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


/**
 * The three home carousels are one endpoint distinguished by `type`, and each
 * lands in its own reducer key. Kept as data rather than three near-identical
 * thunks, so adding a carousel is one entry here.
 */
export const FEATURED_TYPES = {
    "best-sellers": "SET_BEST_SELLERS",
    "new-arrivals": "SET_NEW_ARRIVALS",
    "on-sale": "SET_ON_SALE",
};

export const buildFeaturedUrl = ({ type, limit = 8 }) =>
    `/public/products/featured?type=${encodeURIComponent(type)}&limit=${limit}`;

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

        /**
         * Each carousel is its own query, so one failing section shows an empty
         * carousel instead of putting its error message over Home's main
         * product grid - which is what the shared status slice did.
         */
        getFeaturedProducts: builder.query({
            query: (args) => ({ url: buildFeaturedUrl(args), method: "get" }),
            providesTags: (result, error, args) => [{ type: "Product", id: `featured-${args.type}` }],
            async onQueryStarted({ type }, { dispatch, queryFulfilled }) {
                const action = FEATURED_TYPES[type];
                if (!action) return;
                try {
                    const { data } = await queryFulfilled;
                    dispatch({ type: action, payload: data });
                } catch {
                    // Empty the carousel rather than leaving the previous page's
                    // items in place under a section that failed to load.
                    dispatch({ type: action, payload: [] });
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
    useGetFeaturedProductsQuery,
    useGetCategoriesQuery,
} = productApi;
export default productApi;
