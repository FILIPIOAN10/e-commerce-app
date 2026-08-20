import { apiSlice } from "./apiSlice";

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
const syncCartToReducer = (dispatch, cart) => {
    dispatch({
        type: "GET_USER_CART_PRODUCTS",
        payload: cart.products,
        totalPrice: cart.totalPrice,
        cartId: cart.cartId,
    });
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
        increaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/plus`,
                method: "put",
            }),
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

        decreaseCartQuantity: builder.mutation({
            query: (productId) => ({
                url: `/cart/products/${productId}/quantity/minus`,
                method: "put",
            }),
            async onQueryStarted(productId, { dispatch, getState, queryFulfilled }) {
                const previousCart = takeCartSnapshot(getState);
                dispatch({ type: "OPTIMISTIC_DECREASE_QTY", payload: { productId } });
                try {
                    const { data: cart } = await queryFulfilled;
                    syncCartToReducer(dispatch, cart);
                } catch {
                    rollbackCart(dispatch, previousCart);
                }
            },
        }),
    }),
    overrideExisting: false,
});

export const { useIncreaseCartQuantityMutation, useDecreaseCartQuantityMutation } = cartApi;

export default cartApi;
