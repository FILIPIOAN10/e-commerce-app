import api from "../../api/api";

export const validateCoupon = (code, orderAmount, toast) => async (dispatch, getState) => {
    try {
        const { data } = await api.post("/coupons/validate", { code, orderAmount });
        const { coupon } = getState();
        const current = coupon.appliedCoupons || [];
        if (current.includes(code.toUpperCase())) {
            toast.error("Coupon already applied");
            return;
        }
        const updatedCoupons = [...current, data.coupon.code];
        const finalAmount = orderAmount - data.discountAmount;
        dispatch({
            type: "couponValidateSuccess",
            payload: {
                appliedCoupons: updatedCoupons,
                discountAmount: data.discountAmount,
                finalAmount: finalAmount,
            }
        });
        toast.success(`Coupon applied: ${data.discountAmount} discount`);
    } catch (error) {
        const msg = error?.response?.data?.message || "Invalid coupon code";
        dispatch({ type: "couponError", payload: msg });
        toast.error(msg);
    }
};

export const removeCoupon = (code) => (dispatch, getState) => {
    const { coupon } = getState();
    const updated = (coupon.appliedCoupons || []).filter(c => c !== code);
    dispatch({ type: "couponValidateSuccess", payload: {
        appliedCoupons: updated,
        discountAmount: 0,
        finalAmount: 0,
    }});
};

export const clearCoupon = () => (dispatch) => {
    dispatch({ type: "clearCoupon" });
};

export const fetchAllCoupons = () => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get("/admin/coupons");
        dispatch({ type: "FETCH_COUPONS", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch coupons",
        });
    }
};

export const createCouponAction = (couponData, toast, setOpen) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.post("/admin/coupons", couponData);
        toast.success("Coupon created successfully");
        setOpen(false);
        dispatch({ type: "IS_SUCCESS" });
        await dispatch(fetchAllCoupons());
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to create coupon");
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message });
    }
};

export const updateCouponAction = (couponId, couponData, toast, setOpen) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.put(`/admin/coupons/${couponId}`, couponData);
        toast.success("Coupon updated successfully");
        setOpen(false);
        dispatch({ type: "IS_SUCCESS" });
        await dispatch(fetchAllCoupons());
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to update coupon");
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message });
    }
};

export const deleteCouponAction = (couponId, toast, setOpen) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        await api.delete(`/admin/coupons/${couponId}`);
        toast.success("Coupon deleted successfully");
        setOpen(false);
        dispatch({ type: "IS_SUCCESS" });
        await dispatch(fetchAllCoupons());
    } catch (error) {
        toast.error(error?.response?.data?.message || "Failed to delete coupon");
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message });
    }
};
