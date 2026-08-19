const initialState = {
    cart: [],
    totalPrice: 0,
    cartId: null,
}

const calculateCartTotal = (cart) =>
    cart
        .filter((item) => !item.savedForLater)
        .reduce(
            (sum, item) =>
                sum +
                Number(item.specialPrice ?? item.price ?? 0) *
                Number(item.quantity ?? 0),
            0
        );

const updateCartItem = (cart, productId, updater) =>
    cart.map((item) =>
        item.productId === productId ? updater(item) : item
    );

export const cartReducer = (state = initialState, action) =>{
    switch(action.type){
        case "ADD_CART": {
            const productToAdd = action.payload;
            const existingProduct = state.cart.find(
                (item) =>item.productId === productToAdd.productId
            );

            if(existingProduct) {
                const updatedCart = state.cart.map((item)=> {
                    if(item.productId === productToAdd.productId){
                        return productToAdd;
                    } else{
                        return item;
                    }
                });
                return {
                    ...state,
                    cart :updatedCart,
                    totalPrice: calculateCartTotal(updatedCart),
                }
            } else{
                const newCart = [...state.cart, productToAdd];
                return {
                    ...state,
                    cart: newCart,
                    totalPrice: calculateCartTotal(newCart),
                };
            }
        }

        case "REMOVE_CART": {
            const updatedCart = state.cart.filter(
                (item) => item.productId !== action.payload.productId
            );
            return {
                ...state,
                cart: updatedCart,
                totalPrice: calculateCartTotal(updatedCart),
            };
        }

        case "GET_USER_CART_PRODUCTS":
            return {
                ...state,
                cart: action.payload,
                totalPrice: action.totalPrice,
                cartId: action.cartId,

            };

        case "OPTIMISTIC_INCREASE_QTY": {
            const updatedCart = updateCartItem(
                state.cart,
                action.payload.productId,
                (item) => ({ ...item, quantity: item.quantity + 1 })
            );
            return {
                ...state,
                cart: updatedCart,
                totalPrice: calculateCartTotal(updatedCart),
            };
        }

        case "OPTIMISTIC_DECREASE_QTY": {
            const updatedCart = updateCartItem(
                state.cart,
                action.payload.productId,
                (item) => ({ ...item, quantity: Math.max(1, item.quantity - 1) })
            );
            return {
                ...state,
                cart: updatedCart,
                totalPrice: calculateCartTotal(updatedCart),
            };
        }

        case "OPTIMISTIC_REMOVE_CART_ITEM": {
            const updatedCart = state.cart.filter(
                (item) => item.productId !== action.payload.productId
            );
            return {
                ...state,
                cart: updatedCart,
                totalPrice: calculateCartTotal(updatedCart),
            };
        }

        case "OPTIMISTIC_TOGGLE_SAVE_FOR_LATER": {
            const updatedCart = updateCartItem(
                state.cart,
                action.payload.productId,
                (item) => ({ ...item, savedForLater: !item.savedForLater })
            );
            return {
                ...state,
                cart: updatedCart,
                totalPrice: calculateCartTotal(updatedCart),
            };
        }

        case "ROLLBACK_CART":
            return {
                ...state,
                cart: action.payload.cart,
                totalPrice: action.payload.totalPrice,
                cartId: action.payload.cartId,
            };

        case "CLEAR_CART":
            return{ cart:[], totalPrice :0, cartId:null};
            default:
                return state;
    }
}