import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProducts, addToCart, removeFromCart, logOutUser, createUserCart } from '../../store/actions'

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
    expect(mockNavigate).toHaveBeenCalledWith('/en/login')
  })
})

describe('createUserCart action', () => {
  const mockGetState = () => ({ carts: { cart: [], cartId: 1, totalPrice: 0 } })

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // isLoading is a count of in-flight requests, so an action that opens
  // IS_FETCHING and never closes it leaves every isLoading-driven screen
  // (checkout's address step first) stuck on skeletons for the rest of the
  // session. Assert the pair balances rather than that it merely fires.
  it('balances IS_FETCHING with a closing status action', async () => {
    api.post.mockResolvedValue({ data: 'created' })
    api.get.mockResolvedValue({ data: { products: [], totalPrice: 0, cartId: 1 } })

    const dispatch = vi.fn((action) =>
      typeof action === 'function' ? action(dispatch, mockGetState) : action
    )

    await createUserCart([{ productId: 1, quantity: 1 }])(dispatch)

    const types = dispatch.mock.calls.map((c) => c[0]?.type).filter(Boolean)
    const opened = types.filter((t) => t === 'IS_FETCHING').length
    const closed = types.filter((t) => t === 'IS_SUCCESS' || t === 'IS_ERROR').length
    expect(opened).toBeGreaterThan(0)
    expect(closed).toBe(opened)
  })

  it('closes IS_FETCHING when the request fails', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'boom' } } })

    const dispatch = vi.fn()
    await createUserCart([{ productId: 1, quantity: 1 }])(dispatch)

    const types = dispatch.mock.calls.map((c) => c[0]?.type).filter(Boolean)
    expect(types.filter((t) => t === 'IS_FETCHING').length).toBe(
      types.filter((t) => t === 'IS_SUCCESS' || t === 'IS_ERROR').length
    )
  })
})
