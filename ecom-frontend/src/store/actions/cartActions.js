import { createElement } from "react";
import api from "../../api/api";
import i18n from "../../i18n";

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
                    navigate(`/${i18n.language}/cart`);
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
            localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
        } else {
            if (toast) toast.error("Out of stock");
        }
};

const takeCartSnapshot = (getState) => ({
    cart: [...getState().carts.cart],
    totalPrice: getState().carts.totalPrice,
    cartId: getState().carts.cartId,
});

export const increaseCartQuantity =
    (data, toast) =>
    async (dispatch, getState) => {
        const previousCart = takeCartSnapshot(getState);
        dispatch({
            type: "OPTIMISTIC_INCREASE_QTY",
            payload: { productId: data.productId },
        });
        try {
            const { data: cart } = await api.put(`/cart/products/${data.productId}/quantity/plus`);
            dispatch({
                type: "GET_USER_CART_PRODUCTS",
                payload: cart.products,
                totalPrice: cart.totalPrice,
                cartId: cart.cartId,
            });
            localStorage.setItem("cartItems", JSON.stringify(cart.products));
            toast?.success("Quantity increased");
        } catch (error) {
            dispatch({
                type: "ROLLBACK_CART",
                payload: previousCart,
            });
            localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
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
        try {
            const { data: cart } = await api.put(`/cart/products/${data.productId}/quantity/minus`);
            dispatch({
                type: "GET_USER_CART_PRODUCTS",
                payload: cart.products,
                totalPrice: cart.totalPrice,
                cartId: cart.cartId,
            });
            localStorage.setItem("cartItems", JSON.stringify(cart.products));
            toast?.success("Quantity decreased");
        } catch (error) {
            dispatch({
                type: "ROLLBACK_CART",
                payload: previousCart,
            });
            localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
            toast?.error(error?.response?.data?.message || "Failed to decrease quantity");
        }
    };

export const removeFromCart = (data, toast) => async (dispatch, getState) => {
    const { cartId } = getState().carts;
    const previousCart = takeCartSnapshot(getState);

    dispatch({ type: "OPTIMISTIC_REMOVE_CART_ITEM", payload: { productId: data.productId } });
    localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));

    if (!cartId) {
        toast.success(`${data.productName} removed from cart`);
        return;
    }

    try {
        await api.delete(`/carts/${cartId}/product/${data.productId}`);
        toast.success(`${data.productName} removed from cart`);
    } catch (error) {
        dispatch({ type: "ROLLBACK_CART", payload: previousCart });
        localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
        toast?.error(error?.response?.data?.message || "Failed to remove item");
    }
};

export const createUserCart = (sendCartItems) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.post('/cart/create', sendCartItems);
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
        localStorage.setItem("cartItems", JSON.stringify(getState().carts.cart));
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
        localStorage.setItem("cartItems", JSON.stringify(data.products));
        if (toast) toast.success("Item saved for later");
    } catch (error) {
        if (item) {
            dispatch({ type: "ROLLBACK_CART", payload: previousCart });
            localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
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
        localStorage.setItem("cartItems", JSON.stringify(data.products));
        if (toast) toast.success("Item moved to cart");
    } catch (error) {
        if (item) {
            dispatch({ type: "ROLLBACK_CART", payload: previousCart });
            localStorage.setItem("cartItems", JSON.stringify(previousCart.cart));
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
