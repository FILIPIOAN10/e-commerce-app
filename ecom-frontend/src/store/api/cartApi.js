import { apiSlice } from "./apiSlice";

<<<<<<< HEAD
/**
 * Cart mutations backed by RTK Query.
 *
 * The backend response is the source of truth, but the app still keeps the
 * cart in the plain `carts` reducer (read by Cart.jsx, Checkout.jsx, the
 * navbar badge, etc.), so instead of duplicating that state in the RTK Query
 * cache, each mutation syncs its result into the existing reducer via
 * `onQueryStarted`. This gives us RTK Query's built-in loading/error state
 * and request de-duplication while preserving the optimistic-update
 * behaviour already relied upon elsewhere in the app.
 */
=======
>>>>>>> feat/rtk-query-dark-rate-limit
const syncCartToReducer = (dispatch, cart) => {
    dispatch({
        type: "GET_USER_CART_PRODUCTS",
        payload: cart.products,
        totalPrice: cart.totalPrice,
        cartId: cart.cartId,
    });
<<<<<<< HEAD
    localStorage.setItem("cartItems", JSON.stringify(cart.products));
};

const takeCartSnapshot = (getState) => ({
    cart: [...getState().carts.cart],
    totalPrice: getState().carts.totalPrice,
    cartId: getState().carts.cartId,
});

const rollbackCart = (dispatch, previousCart) => {
    dispatch({ type: "ROLLBACK_CART", payload: previousCart });
    localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
};

export const cartApi = apiSlice.injectEndpoints({
    endpoints: (builder) => ({
=======
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
>>>>>>> feat/rtk-query-dark-rate-limit
        increaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/plus`,
                method: "put",
            }),
<<<<<<< HEAD
            async onQueryStarted(productId, { dispatch, getState, queryFulfilled }) {
                const previousCart = takeCartSnapshot(getState);
                dispatch({ type: "OPTIMISTIC_INCREASE_QTY", payload: { productId } });
                try {
                    const { data: cart } = await queryFulfilled;
                    syncCartToReducer(dispatch, cart);
                } catch {
                    rollbackCart(dispatch, previousCart);
                }
            },
        }),

=======
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
>>>>>>> feat/rtk-query-dark-rate-limit
        decreaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/minus`,
                method: "put",
            }),
<<<<<<< HEAD
            async onQueryStarted(productId, { dispatch, getState, queryFulfilled }) {
                const previousCart = takeCartSnapshot(getState);
                dispatch({ type: "OPTIMISTIC_DECREASE_QTY", payload: { productId } });
                try {
                    const { data: cart } = await queryFulfilled;
                    syncCartToReducer(dispatch, cart);
                } catch {
                    rollbackCart(dispatch, previousCart);
=======
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
>>>>>>> feat/rtk-query-dark-rate-limit
                }
            },
        }),
    }),
    overrideExisting: false,
});

<<<<<<< HEAD
export const { useIncreaseCartQuantityMutation, useDecreaseCartQuantityMutation } = cartApi;
=======
export const {
    useGetUserCartQuery,
    useCreateCartMutation,
    useIncreaseCartQuantityMutation,
    useDecreaseCartQuantityMutation,
    useSaveItemForLaterMutation,
    useMoveItemToCartMutation,
    useRemoveFromCartMutation,
} = cartApi;
>>>>>>> feat/rtk-query-dark-rate-limit

export default cartApi;
