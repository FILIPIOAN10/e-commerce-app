import { readJson } from "../../utils/safeStorage";

// Rehydrate the session from the browser. Module scope, so it must not throw.
const savedAuth = readJson("auth", null);

const initialState = {
    user: savedAuth, // 🟢 Acum, la refresh, își ia datele înapoi din localStorage
    address: [],
    selectedUserCheckoutAddress: null,
    clientSecret: null // Este bine să îl declari și aici dacă îl folosești mai jos
}

export const authReducer = (state = initialState, action) =>{
    switch(action.type) {
        
        case "LOGIN_USER":
            return {...state, user: action.payload};
        case "USER_ADDRESS":
            return {...state, address: action.payload};
        case "SELECT_CHECKOUT_ADDRESS":
            return {...state, selectedUserCheckoutAddress: action.payload};
        case "REMOVE_CHECKOUT_ADDRESS":
            return {...state, selectedUserCheckoutAddress: null};
        case "CLIENT_SECRET":
            return {...state, clientSecret: action.payload};
        case "REMOVE_CLIENT_SECRET_ADDRESS":
            return {...state, clientSecret: null, selectedUserCheckoutAddress: null};
        case "LOG_OUT":
            // 🟢 Păstrează structura inițială a obiectului și la LOG_OUT ca să nu crape proprietățile
            return { 
                user: null,
                address: [],
                selectedUserCheckoutAddress: null,
                clientSecret: null
            };
        default:
            return state;
            
    }
};