import { createApi } from "@reduxjs/toolkit/query/react";
import { axiosBaseQuery } from "./axiosBaseQuery";

/**
 * Central RTK Query slice. Endpoints are injected from feature-specific API files.
 */
export const apiSlice = createApi({
    reducerPath: "api",
    baseQuery: axiosBaseQuery(),
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
    ],
    endpoints: () => ({}),
});
