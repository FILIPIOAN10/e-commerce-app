const initialState = {
    appliedCoupons: [],
    discountAmount: 0,
    shippingCost: 0,
    taxAmount: 0,
    finalAmount: 0,
    loading: false,
    error: null,
    coupons: [],
};

const couponReducer = (state = initialState, action) => {
    switch (action.type) {
        case "couponValidateSuccess":
            return {
                ...state,
                appliedCoupons: action.payload.appliedCoupons,
                discountAmount: action.payload.discountAmount,
                shippingCost: action.payload.shippingCost || 0,
                finalAmount: action.payload.finalAmount,
                error: null,
            };
        case "orderSummarySuccess":
            return {
                ...state,
                discountAmount: action.payload.discountAmount,
                shippingCost: action.payload.shippingCost,
                taxAmount: action.payload.taxAmount || 0,
                finalAmount: action.payload.totalAmount,
                appliedCoupons: action.payload.appliedCoupons,
                error: null,
            };
        case "couponError":
            return {
                ...state,
                appliedCoupons: [],
                discountAmount: 0,
                shippingCost: 0,
                taxAmount: 0,
                finalAmount: 0,
                error: action.payload,
            };
        case "clearCoupon":
            return {
                ...state,
                appliedCoupons: [],
                discountAmount: 0,
                shippingCost: 0,
                taxAmount: 0,
                finalAmount: 0,
                error: null,
            };
        case "FETCH_COUPONS":
            return {
                ...state,
                coupons: action.payload,
            };
        default:
            return state;
    }
};

export default couponReducer;
