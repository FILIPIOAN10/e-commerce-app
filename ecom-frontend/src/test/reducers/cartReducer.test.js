import { describe, it, expect } from 'vitest'
import { cartReducer } from '../../store/reducers/cartReducer'

describe('cartReducer', () => {
  it('returns initial state for unknown action', () => {
    const state = cartReducer(undefined, { type: 'UNKNOWN' })
    expect(state).toEqual({ cart: [], totalPrice: 0, cartId: null })
  })

  it('adds a new product to empty cart', () => {
    const action = {
      type: 'ADD_CART',
      payload: { productId: 1, productName: 'Widget', quantity: 2, price: 10 },
    }
    const state = cartReducer(undefined, action)
    expect(state.cart).toHaveLength(1)
    expect(state.cart[0].productId).toBe(1)
  })

  it('updates quantity when adding an existing product', () => {
    const initialState = {
      cart: [{ productId: 1, productName: 'Widget', quantity: 2, price: 10 }],
      totalPrice: 0,
      cartId: null,
    }
    const action = {
      type: 'ADD_CART',
      payload: { productId: 1, productName: 'Widget', quantity: 5, price: 10 },
    }
    const state = cartReducer(initialState, action)
    expect(state.cart).toHaveLength(1)
    expect(state.cart[0].quantity).toBe(5)
  })

  it('removes a product from cart', () => {
    const initialState = {
      cart: [
        { productId: 1, productName: 'Widget', quantity: 2 },
        { productId: 2, productName: 'Gadget', quantity: 1 },
      ],
      totalPrice: 0,
      cartId: null,
    }
    const action = { type: 'REMOVE_CART', payload: { productId: 1 } }
    const state = cartReducer(initialState, action)
    expect(state.cart).toHaveLength(1)
    expect(state.cart[0].productId).toBe(2)
  })

  it('clears the cart', () => {
    const initialState = {
      cart: [{ productId: 1, quantity: 2 }],
      totalPrice: 100,
      cartId: 5,
    }
    const state = cartReducer(initialState, { type: 'CLEAR_CART' })
    expect(state.cart).toEqual([])
    expect(state.totalPrice).toBe(0)
    expect(state.cartId).toBeNull()
  })

  it('loads user cart from server', () => {
    const action = {
      type: 'GET_USER_CART_PRODUCTS',
      payload: [{ productId: 10, quantity: 3 }],
      totalPrice: 150,
      cartId: 99,
    }
    const state = cartReducer(undefined, action)
    expect(state.cart).toEqual([{ productId: 10, quantity: 3 }])
    expect(state.totalPrice).toBe(150)
    expect(state.cartId).toBe(99)
  })
})
