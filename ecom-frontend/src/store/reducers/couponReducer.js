const initialState = {
    appliedCoupon: null,
    discountAmount: 0,
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
                appliedCoupon: action.payload.coupon,
                discountAmount: action.payload.discountAmount,
                finalAmount: action.payload.finalAmount,
                error: null,
            };
        case "couponError":
            return {
                ...state,
                appliedCoupon: null,
                discountAmount: 0,
                finalAmount: 0,
                error: action.payload,
            };
        case "clearCoupon":
            return {
                ...state,
                appliedCoupon: null,
                discountAmount: 0,
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
