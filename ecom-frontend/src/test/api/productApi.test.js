import { describe, it, expect } from 'vitest'
import { buildProductsUrl } from '../../store/api/productApi'
import { buildProductQuery } from '../../hooks/useProductQuery'

describe('buildProductsUrl', () => {
  it('uses the plain listing when there is no keyword', () => {
    expect(buildProductsUrl('pageNumber=0&sortBy=price')).toBe(
      '/public/products?pageNumber=0&sortBy=price'
    )
  })

  it('routes a bare keyword to semantic search', () => {
    const url = buildProductsUrl('keyword=laptop&pageNumber=0')
    expect(url).toContain('/public/products/search?')
    expect(url).toContain('q=laptop')
    expect(url).toContain('semantic=true')
    expect(url).not.toContain('keyword=')
  })

  it('keeps a keyword on the plain listing when a category is pinned', () => {
    // Semantic ranking would fight the category filter, so the listing wins.
    const url = buildProductsUrl('keyword=laptop&category=Electronics')
    expect(url).toContain('/public/products?')
    expect(url).not.toContain('semantic=true')
  })
})

describe('buildProductQuery', () => {
  const query = (search) => new URLSearchParams(buildProductQuery(new URLSearchParams(search)))

  it('converts a 1-based page in the URL to a 0-based pageNumber', () => {
    expect(query('page=3').get('pageNumber')).toBe('2')
  })

  it('defaults to the first page', () => {
    expect(query('').get('pageNumber')).toBe('0')
  })

  it('never emits a negative page', () => {
    expect(query('page=0').get('pageNumber')).toBe('0')
    expect(query('page=-5').get('pageNumber')).toBe('0')
  })

  it('treats the URL sortBy as a direction, sorting by price', () => {
    const q = query('sortBy=desc')
    expect(q.get('sortBy')).toBe('price')
    expect(q.get('sortOrder')).toBe('desc')
  })

  it('defaults the direction to ascending', () => {
    expect(query('').get('sortOrder')).toBe('asc')
  })

  it('passes category and keyword through only when present', () => {
    expect(query('').has('category')).toBe(false)
    expect(query('').has('keyword')).toBe(false)
    const q = query('category=Books&keyword=java')
    expect(q.get('category')).toBe('Books')
    expect(q.get('keyword')).toBe('java')
  })
})
