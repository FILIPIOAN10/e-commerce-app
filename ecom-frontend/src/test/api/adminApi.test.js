import { describe, it, expect } from 'vitest'
import {
  ANALYTICS_CHARTS,
  buildChartUrl,
  buildLowStockUrl,
  buildRoleScopedUrl,
  pagedPayload,
} from '../../store/api/adminApi'
import { toPageQuery } from '../../hooks/usePagedQueryArgs'

describe('buildRoleScopedUrl', () => {
  it('sends admins to the admin endpoint', () => {
    expect(buildRoleScopedUrl({ isAdmin: true, resource: '/orders' })).toBe('/admin/orders')
  })

  it('sends everyone else to the seller endpoint', () => {
    // A seller must only ever see their own rows; picking the wrong base here
    // would expose the whole tenant's data.
    expect(buildRoleScopedUrl({ isAdmin: false, resource: '/orders' })).toBe('/seller/orders')
  })

  it('appends a query string only when there is one', () => {
    expect(buildRoleScopedUrl({ isAdmin: true, resource: '/products', queryString: 'pageNumber=2' }))
      .toBe('/admin/products?pageNumber=2')
    expect(buildRoleScopedUrl({ isAdmin: true, resource: '/products', queryString: '' }))
      .toBe('/admin/products')
  })
})

describe('buildLowStockUrl', () => {
  it('scopes by role and carries paging', () => {
    expect(buildLowStockUrl({ isAdmin: true, pageNumber: 1, pageSize: 10 }))
      .toBe('/admin/low-stock?pageNumber=1&pageSize=10')
    expect(buildLowStockUrl({ isAdmin: false, pageNumber: 0, pageSize: 10 }))
      .toBe('/seller/low-stock?pageNumber=0&pageSize=10')
  })

  it('defaults paging', () => {
    expect(buildLowStockUrl({ isAdmin: true })).toBe('/admin/low-stock?pageNumber=0&pageSize=10')
  })
})

describe('ANALYTICS_CHARTS', () => {
  it('maps every dashboard chart to its own reducer action', () => {
    // A missing entry would silently render an empty chart with no error.
    expect(ANALYTICS_CHARTS).toEqual({
      sales: 'FETCH_SALES_CHART',
      'top-products': 'FETCH_TOP_PRODUCTS_CHART',
      'order-status': 'FETCH_ORDER_STATUS_CHART',
      'revenue-by-category': 'FETCH_REVENUE_BY_CATEGORY_CHART',
    })
  })

  it('builds each chart url from its key', () => {
    Object.keys(ANALYTICS_CHARTS).forEach((chart) => {
      expect(buildChartUrl(chart)).toBe(`/admin/app/analytics/${chart}`)
    })
  })
})

describe('pagedPayload', () => {
  it('flattens a Spring page into the reducer shape', () => {
    expect(pagedPayload({
      content: [{ id: 1 }],
      pageNumber: 2, pageSize: 10, totalElements: 25, totalPages: 3, lastPage: false,
    })).toEqual({
      payload: [{ id: 1 }],
      pageNumber: 2, pageSize: 10, totalElements: 25, totalPages: 3, lastPage: false,
    })
  })
})

describe('toPageQuery', () => {
  it('converts the 1-based URL page to a 0-based pageNumber', () => {
    expect(toPageQuery(new URLSearchParams('page=4'))).toBe('pageNumber=3')
  })

  it('defaults to the first page', () => {
    expect(toPageQuery(new URLSearchParams(''))).toBe('pageNumber=0')
  })

  it('never emits a negative page', () => {
    expect(toPageQuery(new URLSearchParams('page=0'))).toBe('pageNumber=0')
    expect(toPageQuery(new URLSearchParams('page=-3'))).toBe('pageNumber=0')
  })
})
