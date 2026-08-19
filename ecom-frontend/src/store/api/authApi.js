import { apiSlice } from "./apiSlice";

const authApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        login: builder.mutation({
            query: (credentials) => ({
                url: "/auth/signin",
                method: "post",
                body: credentials,
            }),
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    if (!data.needs2FA) {
                        dispatch({ type: "LOGIN_USER", payload: data });
                        localStorage.setItem("auth", JSON.stringify(data));
                    }
                } catch {
                    // handled by hook
                }
            },
            invalidatesTags: ["Auth"],
        }),
        register: builder.mutation({
            query: (payload) => ({
                url: "/auth/signup",
                method: "post",
                body: payload,
            }),
            invalidatesTags: ["Auth"],
        }),
        getUserDetails: builder.query({
            query: () => ({ url: "/auth/user", method: "get" }),
            providesTags: ["Auth"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    dispatch({ type: "LOGIN_USER", payload: data });
                    localStorage.setItem("auth", JSON.stringify(data));
                } catch {
                    // handled by hook
                }
            },
        }),
        logout: builder.mutation({
            query: () => ({ url: "/auth/signout", method: "post" }),
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    await queryFulfilled;
                } finally {
                    dispatch({ type: "LOG_OUT" });
                    localStorage.removeItem("auth");
                }
            },
            invalidatesTags: ["Auth"],
        }),
    }),
    overrideExisting: false,
});

export const {
    useLoginMutation,
    useRegisterMutation,
    useGetUserDetailsQuery,
    useLazyGetUserDetailsQuery,
    useLogoutMutation,
} = authApi;

export default authApi;
