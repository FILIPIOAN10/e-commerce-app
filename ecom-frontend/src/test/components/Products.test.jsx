import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'

const ascending = {
  content: [
    { productId: 1, productName: 'Cheapest Mug', price: 10, specialPrice: 9, quantity: 5 },
    { productId: 2, productName: 'Middle Lamp', price: 50, specialPrice: 45, quantity: 5 },
  ],
  totalPages: 125,
  totalElements: 1000,
}

vi.mock('../../hooks/useProductQuery', () => ({
  default: () => ({ data: ascending, isLoading: false, error: null }),
}))
vi.mock('../../store/api/productApi', () => ({
  useGetCategoriesQuery: () => ({}),
}))
vi.mock('../../components/products/Filter', () => ({ default: () => null }))
vi.mock('../../components/shared/ProductCard', () => ({
  default: ({ productName }) => <div data-testid="card">{productName}</div>,
}))

import Products from '../../components/products/Products'

// The shared slice holds whichever product request finished last. Going back to
// a sort order or page already in the RTK Query cache fires no request, so the
// slice still held the previous list - the page sorted ascending showed the
// descending products. The page must render its own query's result.
describe('Products page', () => {
  it('renders the current query result, not the stale shared slice', () => {
    const staleDescending = [{ productId: 99, productName: 'Priciest Sofa', price: 499, specialPrice: 450 }]
    const store = configureStore({
      reducer: {
        products: () => ({ products: staleDescending, categories: [], productPagination: { totalPages: 1 } }),
        auth: () => ({ user: null }),
      },
    })

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/en/products?sortBy=asc']}>
          <Products />
        </MemoryRouter>
      </Provider>
    )

    expect(screen.getAllByTestId('card').map((c) => c.textContent)).toEqual(['Cheapest Mug', 'Middle Lamp'])
    expect(screen.queryByText('Priciest Sofa')).not.toBeInTheDocument()
  })
})
