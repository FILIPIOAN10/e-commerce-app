import { apiSlice } from "./apiSlice";

/**
 * Admin reads backed by RTK Query.
 *
 * These nine screens were the last consumers of the shared
 * { isLoading, errorMessage } slice. Because they all read the same object,
 * a failure on any one of them was rendered by whichever admin screen the
 * operator happened to have open — the dashboard could show "Failed to fetch
 * coupons" while its own charts had loaded fine. Every query below owns its
 * status, so an error can only be shown by the screen that caused it.
 *
 * Results are synced into the existing reducers via onQueryStarted, matching
 * cartApi and productApi: the admin tables read those slices directly, so RTK
 * Query owns request status while the reducers stay the store of record.
 */

/**
 * The four dashboard charts are one shape repeated: a path segment, a reducer
 * action, and a response that nests its payload under `data`. Kept as data so
 * adding a chart is one entry rather than another near-identical thunk.
 */
export const ANALYTICS_CHARTS = {
    sales: "FETCH_SALES_CHART",
    "top-products": "FETCH_TOP_PRODUCTS_CHART",
    "order-status": "FETCH_ORDER_STATUS_CHART",
    "revenue-by-category": "FETCH_REVENUE_BY_CATEGORY_CHART",
};

export const buildChartUrl = (chart) => `/admin/app/analytics/${chart}`;

/**
 * Products, orders and low stock are each served by two endpoints, and which
 * one applies depends on the caller's role: a seller may only see their own
 * rows. The thunks read the store directly to decide, which hid the rule and
 * made it untestable; here the role is an argument.
 */
export const buildRoleScopedUrl = ({ isAdmin, resource, queryString = "" }) => {
    const base = `${isAdmin ? "/admin" : "/seller"}${resource}`;
    return queryString ? `${base}?${queryString}` : base;
};

export const buildLowStockUrl = ({ isAdmin, pageNumber = 0, pageSize = 10 }) =>
    buildRoleScopedUrl({
        isAdmin,
        resource: "/low-stock",
        queryString: `pageNumber=${pageNumber}&pageSize=${pageSize}`,
    });

export const pagedPayload = (data) => ({
    payload: data.content,
    pageNumber: data.pageNumber,
    pageSize: data.pageSize,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    lastPage: data.lastPage,
});

/** Dispatches `action` with the fetched body, or does nothing if the call failed. */
const syncOnSuccess = (action, transform = (d) => ({ payload: d })) =>
    async function onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
            const { data } = await queryFulfilled;
            dispatch({ type: action, ...transform(data) });
        } catch {
            // The screen that made this request renders the error itself.
        }
    };

const adminApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getAnalytics: builder.query({
            query: () => ({ url: "/admin/app/analytics", method: "get" }),
            providesTags: ["Analytics"],
            onQueryStarted: syncOnSuccess("FETCH_ANALYTICS"),
        }),

        getAnalyticsChart: builder.query({
            query: (chart) => ({ url: buildChartUrl(chart), method: "get" }),
            async onQueryStarted(chart, { dispatch, queryFulfilled }) {
                const action = ANALYTICS_CHARTS[chart];
                if (!action) return;
                try {
                    const { data } = await queryFulfilled;
                    // These endpoints wrap their series under `data`.
                    dispatch({ type: action, payload: data.data });
                } catch {
                    // One chart failing must not blank the other three.
                }
            },
        }),

        getActivityLogs: builder.query({
            query: ({ pageNumber = 0, pageSize = 20 } = {}) => ({
                url: `/admin/activity-logs?pageNumber=${pageNumber}&pageSize=${pageSize}`,
                method: "get",
            }),
            providesTags: ["ActivityLog"],
            onQueryStarted: syncOnSuccess("FETCH_ACTIVITY_LOGS"),
        }),

        getLowStockProducts: builder.query({
            query: (args) => ({ url: buildLowStockUrl(args), method: "get" }),
            providesTags: ["Product"],
            onQueryStarted: syncOnSuccess("FETCH_LOW_STOCK_PRODUCTS", pagedPayload),
        }),

        getPromoCampaigns: builder.query({
            query: ({ pageNumber = 0, pageSize = 10 } = {}) => ({
                url: `/admin/promo-campaigns?pageNumber=${pageNumber}&pageSize=${pageSize}`,
                method: "get",
            }),
            providesTags: ["PromoCampaign"],
            onQueryStarted: syncOnSuccess("FETCH_PROMO_CAMPAIGNS"),
        }),

        getDashboardProducts: builder.query({
            query: ({ isAdmin, queryString = "" }) => ({
                url: buildRoleScopedUrl({ isAdmin, resource: "/products", queryString }),
                method: "get",
            }),
            providesTags: ["Product"],
            onQueryStarted: syncOnSuccess("FETCH_PRODUCTS", pagedPayload),
        }),

        getDashboardOrders: builder.query({
            query: ({ isAdmin, queryString = "" }) => ({
                url: buildRoleScopedUrl({ isAdmin, resource: "/orders", queryString }),
                method: "get",
            }),
            providesTags: ["Order"],
            onQueryStarted: syncOnSuccess("GET_ADMIN_ORDERS", pagedPayload),
        }),

        getSellers: builder.query({
            query: (queryString = "") => ({
                url: queryString ? `/auth/sellers?${queryString}` : "/auth/sellers",
                method: "get",
            }),
            providesTags: ["Seller"],
            onQueryStarted: syncOnSuccess("GET_SELLERS", pagedPayload),
        }),

        getMyOrders: builder.query({
            query: (queryString = "") => ({
                url: queryString ? `/orders/my-orders?${queryString}` : "/orders/my-orders",
                method: "get",
            }),
            providesTags: ["Order"],
            onQueryStarted: syncOnSuccess("GET_USER_ORDERS", pagedPayload),
        }),

        getCoupons: builder.query({
            query: () => ({ url: "/admin/coupons", method: "get" }),
            providesTags: ["Coupon"],
            onQueryStarted: syncOnSuccess("FETCH_COUPONS"),
        }),
    }),
});

export const {
    useGetAnalyticsQuery,
    useGetAnalyticsChartQuery,
    useGetActivityLogsQuery,
    useGetLowStockProductsQuery,
    useGetPromoCampaignsQuery,
    useGetDashboardProductsQuery,
    useGetDashboardOrdersQuery,
    useGetSellersQuery,
    useGetMyOrdersQuery,
    useGetCouponsQuery,
} = adminApi;

export default adminApi;
