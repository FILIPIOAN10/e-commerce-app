import { createApi } from "@reduxjs/toolkit/query/react";
import { axiosBaseQuery } from "./axiosBaseQuery";

/**
 * Central RTK Query slice. Endpoints are injected from feature-specific API files.
 */
export const apiSlice = createApi({
    reducerPath: "api",
    baseQuery: axiosBaseQuery(),
    // Many screens still read the legacy Redux slices, which endpoints fill
    // from onQueryStarted - and onQueryStarted runs only when a request does.
    // A cache hit (going back to a page, a sort order or a filter already
    // seen) fired no request, so the slice kept whatever the LAST request
    // wrote: /products sorted ascending showed the descending list, and the
    // storefront and Admin > Products, which share FETCH_PRODUCTS, could show
    // each other's rows. Refetching on mount and on every argument change keeps
    // every mirrored slice in step with what the screen asked for.
    refetchOnMountOrArgChange: true,
    tagTypes: [
        "Auth",
        "Cart",
        "Product",
        "Order",
        "Category",
        "Wishlist",
        "Review",
        "Question",
        "Coupon",
        "Notification",
        "Address",
        "Bundle",
        "Subscription",
        "Analytics",
        "ActivityLog",
        "PromoCampaign",
        "Seller",
    ],
    endpoints: () => ({}),
});
