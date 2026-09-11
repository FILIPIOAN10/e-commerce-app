import { createElement } from "react";
import api from "../../api/api";
import { langPath } from "../../utils/languagePath";
import { writeJson } from "../../utils/safeStorage";

const renderAddedToCartToast = (productName, navigate, toast, t) =>
    createElement(
        "span",
        { className: "flex items-center gap-3" },
        createElement("span", null, `${productName} added to the cart`),
        createElement(
            "button",
            {
                type: "button",
                onClick: () => {
                    navigate(langPath("/cart"));
                    toast.dismiss(t.id);
                },
                className:
                    "shrink-0 rounded-md bg-blue-600 hover:bg-blue-700 px-2 py-1 text-xs font-semibold text-white transition-colors",
            },
            "View Cart"
        )
    );

export const addToCart = (data, qty = 1, toast, navigate) =>
    (dispatch, getState) => {
        const { products } = getState().products;
        const getProduct = products?.find?.(
            (item) => item.productId === data.productId
        );

        const stockQuantity = getProduct ? getProduct.quantity : data.quantity;
        const isQuantityExist = Number(stockQuantity) >= qty;

        if (isQuantityExist) {
            dispatch({ type: "ADD_CART", payload: { ...data, quantity: qty } });
            if (toast) {
                if (navigate) {
                    toast.success((t) => renderAddedToCartToast(data?.productName, navigate, toast, t));
                } else {
                    toast.success(`${data?.productName} added to the cart`);
                }
            }
            writeJson("cartItems", getState().carts.cart);
        } else {
            if (toast) toast.error("Out of stock");
        }
};

const takeCartSnapshot = (getState) => ({
    cart: [...getState().carts.cart],
    totalPrice: getState().carts.totalPrice,
    cartId: getState().carts.cartId,
});

/**
 * The server does not know about an item until something pushes it there:
 * addToCart only writes Redux and localStorage. So the two can disagree — a
 * cart built before signing in, an item added while a server cart already
 * existed, or a database rebuilt under a browser that kept its localStorage
 * (then the local items even carry cartItemIds that no longer resolve).
 *
 * Every disagreement shows up the same way: the server answers "not available
 * in the cart" or "cart not found" for a line the user is looking at. This
 * replaces the server cart with what the user sees, which is the only copy
 * they can reason about, and is what POST /cart/create is for.
 */
export const syncServerCart = () => async (dispatch, getState) => {
    const items = getState().carts.cart.map(({ productId, quantity }) => ({ productId, quantity }));
    if (items.length === 0) return false;
    try {
        await api.post("/cart/create", items);
        const { data } = await api.get("/carts/users/cart");
        dispatch({
            type: "GET_USER_CART_PRODUCTS",
            payload: data.products,
            totalPrice: data.totalPrice,
            cartId: data.cartId,
        });
        writeJson("cartItems", data.products);
        return true;
    } catch {
        return false;
    }
};

// The server's way of saying "my copy of this cart doesn't match yours".
const isCartOutOfSync = (error) => {
    const message = error?.response?.data?.message || "";
    return error?.response?.status === 404
        || message.includes("not available in the cart")
        || message.includes("Cart not found");
};

export const increaseCartQuantity =
    (data, toast) =>
    async (dispatch, getState) => {
        const previousCart = takeCartSnapshot(getState);
        dispatch({
            type: "OPTIMISTIC_INCREASE_QTY",
            payload: { productId: data.productId },
        });

        // No server cart yet — a guest, or a signed-in cart the Cart page is
        // still hydrating. The optimistic update is the whole change, exactly
        // how removeFromCart already behaves. Without this the button called an
        // endpoint that answers 404 "Cart not found" and rolled straight back.
        if (!previousCart.cartId) {
            writeJson("cartItems", getState().carts.cart);
            return;
        }

        const applyServerCart = (cart) => {
            dispatch({
                type: "GET_USER_CART_PRODUCTS",
                payload: cart.products,
                totalPrice: cart.totalPrice,
                cartId: cart.cartId,
            });
            writeJson("cartItems", cart.products);
        };

        try {
            const { data: cart } = await api.put(`/cart/products/${data.productId}/quantity/plus`);
            applyServerCart(cart);
            toast?.success("Quantity increased");
        } catch (error) {
            // The server has a different idea of this cart than the screen
            // does. The optimistic state already carries the increment, so
            // pushing it is both the repair and the change — replaying the PUT
            // afterwards would add a second one.
            if (isCartOutOfSync(error) && await dispatch(syncServerCart())) {
                toast?.success("Quantity increased");
                return;
            }
            dispatch({
                type: "ROLLBACK_CART",
                payload: previousCart,
            });
            writeJson("cartItems", previousCart.cart);
            toast?.error(error?.response?.data?.message || "Failed to increase quantity");
        }
    };

export const decreaseCartQuantity =
    (data, toast) =>
    async (dispatch, getState) => {
        const previousCart = takeCartSnapshot(getState);
        dispatch({
            type: "OPTIMISTIC_DECREASE_QTY",
            payload: { productId: data.productId },
        });

        // See increaseCartQuantity: no server cart, no server call.
        if (!previousCart.cartId) {
            writeJson("cartItems", getState().carts.cart);
            return;
        }

        try {
            const { data: cart } = await api.put(`/cart/products/${data.productId}/quantity/minus`);
            dispatch({
                type: "GET_USER_CART_PRODUCTS",
                payload: cart.products,
                totalPrice: cart.totalPrice,
                cartId: cart.cartId,
            });
            writeJson("cartItems", cart.products);
            toast?.success("Quantity decreased");
        } catch (error) {
            // See increaseCartQuantity.
            if (isCartOutOfSync(error) && await dispatch(syncServerCart())) {
                toast?.success("Quantity decreased");
                return;
            }
            dispatch({
                type: "ROLLBACK_CART",
                payload: previousCart,
            });
            writeJson("cartItems", previousCart.cart);
            toast?.error(error?.response?.data?.message || "Failed to decrease quantity");
        }
    };

export const removeFromCart = (data, toast) => async (dispatch, getState) => {
    const { cartId } = getState().carts;
    const previousCart = takeCartSnapshot(getState);

    dispatch({ type: "OPTIMISTIC_REMOVE_CART_ITEM", payload: { productId: data.productId } });
    writeJson("cartItems", getState().carts.cart);

    if (!cartId) {
        toast.success(`${data.productName} removed from cart`);
        return;
    }

    try {
        await api.delete(`/carts/${cartId}/product/${data.productId}`);
        toast.success(`${data.productName} removed from cart`);
    } catch (error) {
        dispatch({ type: "ROLLBACK_CART", payload: previousCart });
        writeJson("cartItems", previousCart.cart);
        toast?.error(error?.response?.data?.message || "Failed to remove item");
    }
};

export const createUserCart = (sendCartItems) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.post('/cart/create', sendCartItems);
        // Close *this* action's IS_FETCHING before delegating. isLoading is a
        // count of in-flight requests, not a boolean, and getUserCart balances
        // only its own pair — so leaving this one open left the count stuck
        // above zero for the rest of the session, and every screen that renders
        // on isLoading (checkout's address step first) showed skeletons forever.
        dispatch({ type: "IS_SUCCESS" });
        await dispatch(getUserCart());
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to create cart items",
        });
    }
};

export const getUserCart = () => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get('/carts/users/cart');

        dispatch({
            type: "GET_USER_CART_PRODUCTS",
            payload: data.products,
            totalPrice: data.totalPrice,
            cartId: data.cartId
        });
        writeJson("cartItems", getState().carts.cart);
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch cart items",
        });
    }
};

export const saveItemForLater = (cartItemId, toast) => async (dispatch, getState) => {
    const item = getState().carts.cart.find((i) => i.cartItemId === cartItemId);
    const previousCart = takeCartSnapshot(getState);

    if (item) {
        dispatch({
            type: "OPTIMISTIC_TOGGLE_SAVE_FOR_LATER",
            payload: { productId: item.productId },
        });
    }

    try {
        const { data } = await api.put(`/cart/items/${cartItemId}/save-for-later`);
        dispatch({
            type: "GET_USER_CART_PRODUCTS",
            payload: data.products,
            totalPrice: data.totalPrice,
            cartId: data.cartId,
        });
        writeJson("cartItems", data.products);
        if (toast) toast.success("Item saved for later");
    } catch (error) {
        if (item) {
            dispatch({ type: "ROLLBACK_CART", payload: previousCart });
            writeJson("cartItems", previousCart.cart);
        }
        if (toast) toast.error(error?.response?.data?.message || "Failed to save item");
    }
};

export const moveItemToCart = (cartItemId, toast) => async (dispatch, getState) => {
    const item = getState().carts.cart.find((i) => i.cartItemId === cartItemId);
    const previousCart = takeCartSnapshot(getState);

    if (item) {
        dispatch({
            type: "OPTIMISTIC_TOGGLE_SAVE_FOR_LATER",
            payload: { productId: item.productId },
        });
    }

    try {
        const { data } = await api.put(`/cart/items/${cartItemId}/move-to-cart`);
        dispatch({
            type: "GET_USER_CART_PRODUCTS",
            payload: data.products,
            totalPrice: data.totalPrice,
            cartId: data.cartId,
        });
        writeJson("cartItems", data.products);
        if (toast) toast.success("Item moved to cart");
    } catch (error) {
        if (item) {
            dispatch({ type: "ROLLBACK_CART", payload: previousCart });
            writeJson("cartItems", previousCart.cart);
        }
        if (toast) toast.error(error?.response?.data?.message || "Failed to move item");
    }
};

export const createCartWithFilteredItems =
    (cartItems, toast, setLoader) => async (dispatch) => {
        try {
            setLoader(true);
            const { data } = await api.post("/cart/create", cartItems);
            toast.success("Cart updated! Items with invalid quantity were filtered out.");
            dispatch({ type: "IS_SUCCESS" });
            return data;
        } catch (error) {
            toast.error(error?.response?.data?.message || "Failed to create cart");
            dispatch({
                type: "IS_ERROR",
                payload: error?.response?.data?.message || "Failed to created cart",
            });
        } finally {
            setLoader(false);
        }
    };
