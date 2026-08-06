const initialState = {
    analytics: {},
    salesChart: [],
    topProductsChart: [],
    orderStatusChart: [],
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
        default:
           return state;

    }
};