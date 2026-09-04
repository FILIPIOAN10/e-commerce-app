import api from "../../api/api";






export const fetchLowStockSummary = () => async (dispatch) => {
    try {
        const { data } = await api.get('/admin/low-stock/summary');
        dispatch({ type: "FETCH_LOW_STOCK_SUMMARY", payload: data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch low stock summary"
        });
    }
};

