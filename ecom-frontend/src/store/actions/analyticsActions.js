import api from "../../api/api";

export const analyticsAction = () => async (dispatch, getState) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get('/admin/app/analytics');
        dispatch({
            type: "FETCH_ANALYTICS",
            payload: data,
        });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch analytics data",
        });
    }
};

export const fetchSalesChartData = () => async (dispatch) => {
    try {
        const { data } = await api.get('/admin/app/analytics/sales');
        dispatch({ type: "FETCH_SALES_CHART", payload: data.data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch sales chart data",
        });
    }
};

export const fetchTopProductsChartData = () => async (dispatch) => {
    try {
        const { data } = await api.get('/admin/app/analytics/top-products');
        dispatch({ type: "FETCH_TOP_PRODUCTS_CHART", payload: data.data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch top products chart data",
        });
    }
};

export const fetchOrderStatusChartData = () => async (dispatch) => {
    try {
        const { data } = await api.get('/admin/app/analytics/order-status');
        dispatch({ type: "FETCH_ORDER_STATUS_CHART", payload: data.data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch order status chart data",
        });
    }
};

export const fetchRevenueByCategoryChartData = () => async (dispatch) => {
    try {
        const { data } = await api.get('/admin/app/analytics/revenue-by-category');
        dispatch({ type: "FETCH_REVENUE_BY_CATEGORY_CHART", payload: data.data });
    } catch (error) {
        dispatch({
            type: "IS_ERROR",
            payload: error?.response?.data?.message || "Failed to fetch revenue by category"
        });
    }
};

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

export const fetchActivityLogs = (pageNumber = 0, pageSize = 20) => async (dispatch) => {
    try {
        dispatch({ type: "IS_FETCHING" });
        const { data } = await api.get(`/admin/activity-logs?pageNumber=${pageNumber}&pageSize=${pageSize}`);
        dispatch({ type: "FETCH_ACTIVITY_LOGS", payload: data });
        dispatch({ type: "IS_SUCCESS" });
    } catch (error) {
        dispatch({ type: "IS_ERROR", payload: error?.response?.data?.message || "Failed to fetch activity logs" });
    }
};
