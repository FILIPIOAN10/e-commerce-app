const initialState = {
    adminOrder:null,
    userOrders:null,
    pagination:{},
};

export const orderReducer = (state = initialState, action) => {
    switch(action.type){
        case "GET_ADMIN_ORDERS":
            return {
                ...state,
                adminOrder: action.payload,
                pagination: {
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        case "GET_USER_ORDERS": 
            return {
                ...state,
                userOrders: action.payload,
                pagination: {
                    pageNumber: action.pageNumber,
                    pageSize: action.pageSize,
                    totalElements: action.totalElements,
                    totalPages: action.totalPages,
                    lastPage: action.lastPage,
                },
            };

        default:
            return state;
    }
};