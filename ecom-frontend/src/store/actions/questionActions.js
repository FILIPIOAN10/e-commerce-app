import api from "../../api/api";

export const fetchProductQuestions = (productId, pageNumber = 0, pageSize = 10) => async (dispatch) => {
    try {
        dispatch({ type: "questionError", payload: null });
        const { data } = await api.get(`/products/${productId}/questions?pageNumber=${pageNumber}&pageSize=${pageSize}`);
        dispatch({ type: "fetchQuestionsSuccess", payload: data });
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to fetch questions";
        dispatch({ type: "questionError", payload: msg });
    }
};

export const askQuestion = (productId, question, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/products/${productId}/questions`, { question });
        toast.success(data.message);
        dispatch(fetchProductQuestions(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to ask question";
        toast.error(msg);
    }
};

export const answerQuestion = (productId, questionId, answer, toast) => async (dispatch) => {
    try {
        const { data } = await api.post(`/products/${productId}/questions/${questionId}/answer`, { answer });
        toast.success(data.message);
        dispatch(fetchProductQuestions(productId));
    } catch (error) {
        const msg = error?.response?.data?.message || "Failed to answer question";
        toast.error(msg);
    }
};
