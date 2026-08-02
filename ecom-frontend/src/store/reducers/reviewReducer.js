const initialState = {
    reviews: [],
    averageRating: 0,
    totalReviews: 0,
    loading: false,
    error: null,
};

const reviewReducer = (state = initialState, action) => {
    switch (action.type) {
        case "fetchReviewsSuccess":
            return {
                ...state,
                reviews: action.payload.content,
                averageRating: action.payload.averageRating,
                totalReviews: action.payload.totalReviews,
                error: null,
            };
        case "addReviewSuccess":
            return {
                ...state,
                reviews: [action.payload, ...state.reviews],
                error: null,
            };
        case "updateReviewSuccess":
            return {
                ...state,
                reviews: state.reviews.map((r) =>
                    r.reviewId === action.payload.reviewId ? action.payload : r
                ),
                error: null,
            };
        case "deleteReviewSuccess":
            return {
                ...state,
                reviews: state.reviews.filter((r) => r.productId !== action.payload),
                error: null,
            };
        case "reviewError":
            return {
                ...state,
                error: action.payload,
            };
        case "clearReviews":
            return initialState;
        default:
            return state;
    }
};

export default reviewReducer;
