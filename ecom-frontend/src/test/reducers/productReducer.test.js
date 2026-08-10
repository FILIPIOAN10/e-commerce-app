import { describe, it, expect } from 'vitest'
import { productReducer } from '../../store/reducers/ProductReducer'

describe('productReducer', () => {
  it('returns initial state for unknown action', () => {
    const state = productReducer(undefined, { type: 'UNKNOWN' })
    expect(state.products).toBeNull()
    expect(state.categories).toBeNull()
    expect(state.compareList).toEqual([])
  })

  it('fetches products with pagination', () => {
    const action = {
      type: 'FETCH_PRODUCTS',
      payload: [{ productId: 1, productName: 'Widget' }],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      lastPage: true,
    }
    const state = productReducer(undefined, action)
    expect(state.products).toHaveLength(1)
    expect(state.pagination.pageNumber).toBe(0)
    expect(state.pagination.totalElements).toBe(1)
  })

  it('fetches categories', () => {
    const action = {
      type: 'FETCH_CATEGORIES',
      payload: [{ categoryId: 1, categoryName: 'Electronics' }],
      pageNumber: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      lastPage: true,
    }
    const state = productReducer(undefined, action)
    expect(state.categories).toHaveLength(1)
    expect(state.categories[0].categoryName).toBe('Electronics')
  })

  it('deletes a product and updates totalElements', () => {
    const initialState = {
      products: [{ productId: 1 }, { productId: 2 }],
      pagination: { totalElements: 2 },
    }
    const state = productReducer(initialState, { type: 'DELETE_PRODUCT_SUCCESS', payload: 1 })
    expect(state.products).toHaveLength(1)
    expect(state.products[0].productId).toBe(2)
    expect(state.pagination.totalElements).toBe(1)
  })

  it('adds to compare list (max 3)', () => {
    let state = productReducer(undefined, {
      type: 'ADD_TO_COMPARE',
      payload: { productId: 1 },
    })
    expect(state.compareList).toHaveLength(1)

    state = productReducer(state, { type: 'ADD_TO_COMPARE', payload: { productId: 2 } })
    state = productReducer(state, { type: 'ADD_TO_COMPARE', payload: { productId: 3 } })
    expect(state.compareList).toHaveLength(3)

    // Adding a 4th should be ignored
    state = productReducer(state, { type: 'ADD_TO_COMPARE', payload: { productId: 4 } })
    expect(state.compareList).toHaveLength(3)
  })

  it('does not add duplicate to compare list', () => {
    const initialState = { compareList: [{ productId: 1 }] }
    const state = productReducer(initialState, { type: 'ADD_TO_COMPARE', payload: { productId: 1 } })
    expect(state.compareList).toHaveLength(1)
  })

  it('removes from compare list', () => {
    const initialState = { compareList: [{ productId: 1 }, { productId: 2 }] }
    const state = productReducer(initialState, { type: 'REMOVE_FROM_COMPARE', payload: 1 })
    expect(state.compareList).toHaveLength(1)
    expect(state.compareList[0].productId).toBe(2)
  })

  it('clears compare list', () => {
    const initialState = { compareList: [{ productId: 1 }, { productId: 2 }] }
    const state = productReducer(initialState, { type: 'CLEAR_COMPARE' })
    expect(state.compareList).toEqual([])
  })
})
