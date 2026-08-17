import toast from "react-hot-toast";
import api from "../../api/api";

export const getOrdersForDashboard = (queryString = "", isAdmin) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { user } = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/orders" : "/seller/orders";
        const requestUrl = queryString ? `${endpoint}?${queryString}` : endpoint;
        const { data } = await api.get(requestUrl);
        dispatch({
            type: "GET_ADMIN_ORDERS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch orders data",
        });
    }
};

export const updateOrderStatusFromDashboard =
    (orderId, orderStatus, toast, setLoader, isAdmin) => async (dispatch, getState) => {
    try {
        setLoader(true);
        const { user } = getState().auth;
        const adminRequest = isAdmin ?? Boolean(user?.roles?.includes("ROLE_ADMIN"));
        const endpoint = adminRequest ? "/admin/orders/" : "/seller/orders/";

        const { data } = await api.put(`${endpoint}${orderId}/status`, { status: orderStatus });

        toast.success(data.message || "Order updated successfully");
        await dispatch(getOrdersForDashboard("", adminRequest));
    } catch (error) {
        toast.error(error?.response?.data?.message || "Internal Server Error");
    } finally {
        setLoader(false);
    }
};

export const getUserOrders = (queryString = "") => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });

        const requestUrl = queryString ? `/orders/my-orders?${queryString}` : "/orders/my-orders";

        const { data } = await api.get(requestUrl);

        dispatch({
            type: "GET_USER_ORDERS",
            payload: data.content,
            pageNumber: data.pageNumber,
            pageSize: data.pageSize,
            totalElements: data.totalElements,
            totalPages: data.totalPages,
            lastPage: data.lastPage,
        });

        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch your orders",
        });
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
        const { coupon: { appliedCoupons }, auth: { email } } = getState();
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
    } catch (error) {
        // silent
    }
};

export const placeGuestOrder = (payload, setLoading, navigate, toast) => async (dispatch) => {
    try {
        const { data } = await api.post("/public/orders/guest", payload);
        setLoading(false);
        toast.success(`Order placed: #${data.orderId}`);
        dispatch({ type: "CLEAR_CART" });
        navigate("/track-order");
    } catch (error) {
        setLoading(false);
        toast.error(error?.response?.data?.message || "Failed to place guest order");
    }
};

export const createStripePaymentSecret = (sendData) => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.post("/order/stripe-client-secret", sendData);
        dispatch({ type: "CLIENT_SECRET", payload: data });
        localStorage.setItem("client-secret", JSON.stringify(data));
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to create client secret");
    }
};

export const stripePaymentConfirmation = (sendData, setErrorMesssage, setLoadng, toast) => async (dispatch, getState) => {
    try {
        const { coupon: { appliedCoupons } } = getState();
        const payload = { ...sendData };
        if (appliedCoupons && appliedCoupons.length > 0) {
            payload.couponCodes = appliedCoupons;
        }
        const response = await api.post("/order/users/payments/online", payload);
        if (response.data) {
            localStorage.removeItem("CHECKOUT_ADDRESS");
            localStorage.removeItem("cartItems");
            localStorage.removeItem("client-secret");
            dispatch({ type: "REMOVE_CLIENT_SECRET_ADDRESS" });
            dispatch({ type: "CLEAR_CART" });
            dispatch({ type: "clearCoupon" });
            toast.success("Order Accepted");
        } else {
            setErrorMesssage("Payment Failed. Please try again.");
        }
    } catch (error) {
        setErrorMesssage("Payment Failed. Please try again.");
    }
};
