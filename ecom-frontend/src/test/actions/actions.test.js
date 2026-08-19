import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProducts, addToCart, removeFromCart, logOutUser } from '../../store/actions'

vi.mock('../../api/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '../../api/api'

describe('fetchProducts action', () => {
  const mockDispatch = vi.fn()
  const mockGetState = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('dispatches FETCH_PRODUCTS on success', async () => {
    api.get.mockResolvedValue({
      data: {
        content: [{ productId: 1, productName: 'Widget' }],
        pageNumber: 0,
        pageSize: 10,
        totalElements: 1,
        totalPages: 1,
        lastPage: true,
      },
    })

    await fetchProducts('pageNumber=0&pageSize=10')(mockDispatch, mockGetState)

    const types = mockDispatch.mock.calls.map((c) => c[0].type)
    expect(types).toContain('IS_FETCHING')
    expect(types).toContain('FETCH_PRODUCTS')
    expect(types).toContain('IS_SUCCESS')
  })

  it('dispatches IS_ERROR on failure', async () => {
    api.get.mockRejectedValue({ response: { data: { message: 'Server down' } } })

    await fetchProducts('pageNumber=0')(mockDispatch, mockGetState)

    const types = mockDispatch.mock.calls.map((c) => c[0].type)
    expect(types).toContain('IS_FETCHING')
    expect(types).toContain('IS_ERROR')
  })
})

describe('addToCart action', () => {
  const mockDispatch = vi.fn()
  const mockToast = { success: vi.fn(), error: vi.fn() }

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('adds product when in stock', () => {
    const mockGetState = () => ({
      products: {
        products: [{ productId: 1, quantity: 10, productName: 'Widget' }],
      },
      carts: { cart: [] },
    })

    addToCart({ productId: 1, productName: 'Widget', price: 10 }, 2, mockToast)(
      mockDispatch,
      mockGetState
    )

    expect(mockDispatch).toHaveBeenCalledWith({
      type: 'ADD_CART',
      payload: { productId: 1, productName: 'Widget', price: 10, quantity: 2 },
    })
    expect(mockToast.success).toHaveBeenCalled()
  })

  it('shows error toast when out of stock', () => {
    const mockGetState = () => ({
      products: {
        products: [{ productId: 1, quantity: 0, productName: 'Widget' }],
      },
      carts: { cart: [] },
    })

    addToCart({ productId: 1, productName: 'Widget', price: 10 }, 1, mockToast)(
      mockDispatch,
      mockGetState
    )

    expect(mockDispatch).not.toHaveBeenCalled()
    expect(mockToast.error).toHaveBeenCalledWith('Out of stock')
  })
})

describe('removeFromCart action', () => {
  const mockDispatch = vi.fn()
  const mockGetState = () => ({ carts: { cart: [{ productId: 1 }], cartId: null, totalPrice: 0 } })
  const mockToast = { success: vi.fn(), error: vi.fn() }

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('dispatches OPTIMISTIC_REMOVE_CART_ITEM and shows toast', async () => {
    await removeFromCart({ productId: 1, productName: 'Widget' }, mockToast)(
      mockDispatch,
      mockGetState
    )

    expect(mockDispatch).toHaveBeenCalledWith({
      type: 'OPTIMISTIC_REMOVE_CART_ITEM',
      payload: { productId: 1 },
    })
    expect(mockToast.success).toHaveBeenCalled()
  })
})

describe('logOutUser action', () => {
  it('dispatches LOG_OUT and removes auth from localStorage', () => {
    const mockDispatch = vi.fn()
    const mockNavigate = vi.fn()

    localStorage.setItem('auth', JSON.stringify({ id: 1 }))

    logOutUser(mockNavigate)(mockDispatch)

    expect(mockDispatch).toHaveBeenCalledWith({ type: 'LOG_OUT' })
    expect(localStorage.getItem('auth')).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })
})
