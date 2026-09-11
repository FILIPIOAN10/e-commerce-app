import toast from "react-hot-toast";
import api from "../../api/api";
import { langPath } from "../../utils/languagePath";
import { removeKey, writeJson } from "../../utils/safeStorage";
import { apiSlice } from "../api/apiSlice";

// Mutations stay as thunks for now, but they refresh the list by
// invalidating the RTK Query cache rather than re-dispatching a read
// thunk. That keeps one owner of request status and lets the read
// thunks be deleted.

// Key a checkout attempt so a double-click or a retried request creates one
// order, not two. A Stripe retry carries the same PaymentIntent id, which makes
// the best natural key; fall back to a random id for cash-on-delivery.
const checkoutIdempotencyKey = (payload) =>
    payload?.pgPaymentId ||
    (typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `co-${Date.now()}-${Math.random().toString(36).slice(2)}`);

export const updateOrderStatusFromDashboard =
    (orderId, orderStatus, toast, setLoader, isAdmin) => async (dispatch, getState) => {
    try {
        setLoader(true);
        const { user } = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/orders/" : "/seller/orders/";

        const { data } = await api.put(`${endpoint}${orderId}/status`, { status: orderStatus });

        toast.success(data.message || "Order updated successfully");
        dispatch(apiSlice.util.invalidateTags(["Order"]));
    } catch (error) {
        toast.error(error?.response?.data?.message || "Internal Server Error");
    } finally {
        setLoader(false);
    }
};

export const getFilteredOrdersList = () => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get("/orders/list");
        dispatch({
            type: "FETCH_FILTERED_ORDERS",
            payload: data,
        });
        dispatch({ type: "IS_SUCCESS" });
        return data;
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch orders",
        });
    }
};

export const previewOrder = (addressId) => async (dispatch, getState) => {
    try {
        const { coupon: { appliedCoupons } } = getState();
        const payload = { addressId, couponCodes: appliedCoupons };
        const { data } = await api.post("/order/preview", payload);
        dispatch({ type: "orderSummarySuccess", payload: data });
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to preview order";
        if (toast) toast.error(msg);
    }
};

export const estimateShipping = (addressId, cartTotal) => async (dispatch) => {
    try {
        const { data } = await api.get(`/order/shipping/${addressId}?cartTotal=${cartTotal}`);
        dispatch({ type: "orderSummarySuccess", payload: { shippingCost: data, discountAmount: 0, totalAmount: 0, appliedCoupons: [] } });
    } catch {
        // silent
    }
};

export const placeGuestOrder = (payload, setLoading, navigate, toast) => async (dispatch) => {
    try {
        const { data } = await api.post("/public/orders/guest", payload, {
            headers: { "Idempotency-Key": checkoutIdempotencyKey(payload) },
        });
        setLoading(false);
        toast.success(`Order placed: #${data.orderId}`);
        dispatch({ type: "CLEAR_CART" });
        navigate(langPath("/track-order"));
    } catch (error) {
        setLoading(false);
        toast.error(error?.response?.data?.message || "Failed to place guest order");
    }
};

export const createStripePaymentSecret = (sendData) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.post("/order/stripe-client-secret", sendData);
        dispatch({ type: "CLIENT_SECRET", payload: data });
        writeJson("client-secret", data);
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to create client secret");
    }
};

/**
 * 409 on a checkout POST does not mean the payment failed — it means this
 * exact Idempotency-Key is still being processed, and the server will replay
 * the original response once it lands. Treating it as a failure told a customer
 * whose order had just been accepted that their payment had not gone through.
 *
 * A repeat is normal: React runs effects twice in development, and in
 * production a double click or a network retry does the same. Send the same
 * key again and wait for the answer the first attempt is producing.
 */
const postWithIdempotencyRetry = async (url, payload, key, attempts = 4) => {
    for (let attempt = 0; ; attempt++) {
        try {
            return await api.post(url, payload, { headers: { "Idempotency-Key": key } });
        } catch (error) {
            const inFlight = error?.response?.status === 409;
            if (!inFlight || attempt >= attempts - 1) throw error;
            await new Promise((resolve) => setTimeout(resolve, 250 * 2 ** attempt));
        }
    }
};

export const stripePaymentConfirmation = (sendData, setErrorMesssage, setLoadng, toast) => async (dispatch, getState) => {
    try {
        const { coupon: { appliedCoupons } } = getState();
        const payload = { ...sendData };
        if (appliedCoupons && appliedCoupons.length > 0) {
            payload.couponCodes = appliedCoupons;
        }
        const response = await postWithIdempotencyRetry(
            "/order/users/payments/online",
            payload,
            checkoutIdempotencyKey(payload)
        );
        if (response.data) {
            removeKey("CHECKOUT_ADDRESS");
            removeKey("cartItems");
            removeKey("client-secret");
            dispatch({ type: "REMOVE_CLIENT_SECRET_ADDRESS" });
            dispatch({ type: "CLEAR_CART" });
            dispatch({ type: "clearCoupon" });
            toast.success("Order Accepted");
        } else {
            setErrorMesssage("Payment Failed. Please try again.");
        }
    } catch {
        setErrorMesssage("Payment Failed. Please try again.");
    }
};
