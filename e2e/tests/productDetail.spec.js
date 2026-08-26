import { test, expect } from '@playwright/test'
import { loginAsAdmin, createCategory, createProduct } from './helpers/api'

test.describe('Product detail page', () => {
  let product

  test.beforeAll(async () => {
    await loginAsAdmin()
    const category = await createCategory(`E2E Category ${Date.now()}`)
    product = await createProduct(category.categoryId, {
      productName: `E2E Product ${Date.now()}`,
      description: 'E2E product description sample',
      quantity: 10,
      price: 99.99,
      discount: 10,
      specialPrice: 89.99,
      tags: 'e2e, test',
      image: 'cal.png',
    })
  })

  test('loads product information and actions', async ({ page }) => {
    await page.goto(`/en/products/${product.productId}`)

    await expect(page.locator('h1')).toContainText(product.productName, { timeout: 10000 })
    await expect(page.locator('text=E2E product description sample')).toBeVisible()
    await expect(page.locator('text=Add to Cart')).toBeVisible()
    await expect(page.locator('text=Compare')).toBeVisible()

    await page.click('text=Q&A')
    await expect(page.locator('text=No questions yet')).toBeVisible()

    await page.click('text=Similar Products')
    await expect(page.locator('text=Similar Products')).toBeVisible()
  })
})
