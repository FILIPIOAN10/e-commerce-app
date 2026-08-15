const initialState = {
    analytics: {},
    salesChart: [],
    topProductsChart: [],
    orderStatusChart: [],
    revenueByCategoryChart: [],
    lowStockSummary: null,
    activityLogs: [],
    activityLogTotal: 0,
    promoCampaigns: [],
    promoCampaignTotal: 0,
}

export const adminReducer = (state = initialState,action) =>{
    switch(action.type){
        case "FETCH_ANALYTICS":
            return{
                ...state,
                analytics: action.payload
            };
        case "FETCH_SALES_CHART":
            return {
                ...state,
                salesChart: action.payload
            };
        case "FETCH_TOP_PRODUCTS_CHART":
            return {
                ...state,
                topProductsChart: action.payload
            };
        case "FETCH_ORDER_STATUS_CHART":
            return {
                ...state,
                orderStatusChart: action.payload
            };
        case "FETCH_REVENUE_BY_CATEGORY_CHART":
            return {
                ...state,
                revenueByCategoryChart: action.payload
            };
        case "FETCH_LOW_STOCK_SUMMARY":
            return {
                ...state,
                lowStockSummary: action.payload
            };
        case "FETCH_ACTIVITY_LOGS":
            return {
                ...state,
                activityLogs: action.payload.content,
                activityLogTotal: action.payload.totalElements
            };
        case "FETCH_PROMO_CAMPAIGNS":
            return {
                ...state,
                promoCampaigns: action.payload.content,
                promoCampaignTotal: action.payload.totalElements
            };
        default:
           return state;

    }
};