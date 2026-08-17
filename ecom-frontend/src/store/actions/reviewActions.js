import toast from "react-hot-toast";
import api from "../../api/api";

export const fetchProductReviews = (productId, pageNumber = 0, pageSize = 10) => async (dispatch) => {
    try {
        dispatch({ type: "reviewError", payload: null });
        const { data } = await api.get(`/public/products/${productId}/reviews?pageNumber=${pageNumber}&pageSize=${pageSize}`);
        dispatch({ type: "fetchReviewsSuccess", payload: data });
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to fetch reviews";
        dispatch({ type: "reviewError", payload: msg });
    }
};

export const addReview = (productId, rating, comment, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/users/reviews/${productId}`, { rating, comment });
        dispatch({ type: "addReviewSuccess", payload: { productId, rating, comment } });
        toast.success(data.message);
        dispatch(fetchProductReviews(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to add review";
        toast.error(msg);
    }
};

export const updateReview = (productId, rating, comment, toast) => async (dispatch) => {
    try {
        const { data } = await api.put(`/users/reviews/${productId}`, { rating, comment });
        dispatch({ type: "updateReviewSuccess", payload: { productId, rating, comment } });
        toast.success(data.message);
        dispatch(fetchProductReviews(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to update review";
        toast.error(msg);
    }
};

export const deleteReview = (productId, toast) => async (dispatch) => {
    try {
        const { data } = await api.delete(`/users/reviews/${productId}`);
        dispatch({ type: "deleteReviewSuccess", payload: productId });
        toast.success(data.message);
        dispatch(fetchProductReviews(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to delete review";
        toast.error(msg);
    }
};

export const markReviewHelpful = (productId, reviewId, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/users/reviews/${reviewId}/helpful`);
        toast.success(data.message);
        dispatch(fetchProductReviews(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to mark helpful";
        toast.error(msg);
    }
};

export const markReviewUnhelpful = (productId, reviewId, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/users/reviews/${reviewId}/unhelpful`);
        toast.success(data.message);
        dispatch(fetchProductReviews(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to mark unhelpful";
        toast.error(msg);
    }
};
