import { configureStore } from "@reduxjs/toolkit";
import { productReducer } from "./ProductReducer";
import { errorReducer } from "./errorReducer";
import { cartReducer } from "./cartReducer";
import { authReducer } from "./authReducer";
import { paymentMethodReducer } from "./paymentMethodReducer";
import { adminReducer } from "./adminReducer";
import { orderReducer } from "./orderReducer";
import { sellerReducer } from "./sellerReducer";
import wishlistReducer from "./wishlistReducer";
import reviewReducer from "./reviewReducer";
import questionReducer from "./questionReducer";
import couponReducer from "./couponReducer";
import { notificationReducer } from "./notificationReducer";
import { apiSlice } from "../api/apiSlice";

const user = localStorage.getItem("auth")
    ? JSON.parse(localStorage.getItem("auth"))
    : null;

const cartItems = localStorage.getItem("cartItems")
    ? JSON.parse(localStorage.getItem("cartItems"))
    : [];

const compareItems = localStorage.getItem("compareItems")
    ? JSON.parse(localStorage.getItem("compareItems"))
    : [];

const selectedUserCheckoutAddress = localStorage.getItem("CHECKOUT_ADDRESS")
    ? JSON.parse(localStorage.getItem("CHECKOUT_ADDRESS"))
    : null;

const initialState = {
    auth: { user: user, selectedUserCheckoutAddress },
    carts: { cart: cartItems },
    products: { compareList: compareItems },
};

export const store = configureStore({
    reducer: {
        products: productReducer,
        errors: errorReducer,
        carts: cartReducer,
        auth: authReducer,
        payment: paymentMethodReducer,
        admin: adminReducer,
        order: orderReducer,
        seller: sellerReducer,
        wishlist: wishlistReducer,
        review: reviewReducer,
        question: questionReducer,
        coupon: couponReducer,
        notification: notificationReducer,
        [apiSlice.reducerPath]: apiSlice.reducer,
    },
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(apiSlice.middleware),
    preloadedState: initialState,
    middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(apiSlice.middleware),
});

export default store;