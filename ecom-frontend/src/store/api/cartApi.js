import { apiSlice } from "./apiSlice";

const syncCartToReducer = (dispatch, cart) => {
    dispatch({
        type: "GET_USER_CART_PRODUCTS",
        payload: cart.products,
        totalPrice: cart.totalPrice,
        cartId: cart.cartId,
    });
};

const cartApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
        getUserCart: builder.query({
            query: () => ({ url: "/carts/users/cart", method: "get" }),
            providesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // error is already handled by the mutation/query hook
                }
            },
        }),
        createCart: builder.mutation({
            query: (cartItems) => ({
                url: "/cart/create",
                method: "post",
                body: cartItems,
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
        increaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/plus`,
                method: "put",
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
        decreaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/minus`,
                method: "put",
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
        saveItemForLater: builder.mutation({
            query: (cartItemId) => ({
                url: `/cart/items/${cartItemId}/save-for-later`,
                method: "put",
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
        moveItemToCart: builder.mutation({
            query: (cartItemId) => ({
                url: `/cart/items/${cartItemId}/move-to-cart`,
                method: "put",
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
        removeFromCart: builder.mutation({
            query: ({ cartId, productId }) => ({
                url: `/carts/${cartId}/product/${productId}`,
                method: "delete",
            }),
            invalidatesTags: ["Cart"],
            async onQueryStarted(_, { dispatch, queryFulfilled }) {
                try {
                    const { data } = await queryFulfilled;
                    syncCartToReducer(dispatch, data);
                } catch {
                    // handled by hook
                }
            },
        }),
    }),
    overrideExisting: false,
});

export const {
    useGetUserCartQuery,
    useCreateCartMutation,
    useIncreaseCartQuantityMutation,
    useDecreaseCartQuantityMutation,
    useSaveItemForLaterMutation,
    useMoveItemToCartMutation,
    useRemoveFromCartMutation,
} = cartApi;

export default cartApi;
