import { createApi } from "@reduxjs/toolkit/query/react";
import { axiosBaseQuery } from "./axiosBaseQuery";

/**
 * Central RTK Query slice. Feature-specific endpoints (cart, auth, ...) are
 * injected from their own files via `apiSlice.injectEndpoints`, so this file
 * stays small and new domains don't need to touch the store setup.
 */
export const apiSlice = createApi({
    reducerPath: "api",
    baseQuery: axiosBaseQuery(),
    tagTypes: ["Cart"],
    endpoints: () => ({}),
});

export default apiSlice;
