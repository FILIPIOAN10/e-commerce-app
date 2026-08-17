import { test, expect } from '@playwright/test'
import { loginAsAdmin, createCategory, createProduct } from './helpers/api'
import { login } from './helpers/auth'

test.describe('Compare flow', () => {
  let product

  test.beforeAll(async () => {
    const token = await loginAsAdmin()
    const category = await createCategory(token, `Compare Category ${Date.now()}`)
    product = await createProduct(token, category.categoryId, {
      productName: `Compare Product ${Date.now()}`,
      description: 'A product for compare testing',
      quantity: 10,
      price: 79.99,
      discount: 5,
      specialPrice: 75.99,
      tags: 'compare, e2e',
      image: 'cal.png',
    })
  })

  test('user can add a product to compare and view comparison table', async ({ page }) => {
    await login(page, 'user1', 'password1')

    await page.goto(`/products/${product.productId}`)
    await page.waitForSelector('h1', { timeout: 10000 })

    const compareBtn = page.locator('button:has-text("Compare")').first()
    if (await compareBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await compareBtn.click()
      await page.waitForTimeout(1000)
    }

    await page.goto('/compare')
    await expect(page).toHaveURL(/\/compare/, { timeout: 10000 })
    await expect(page.locator('text=Compare Products')).toBeVisible()
    await expect(page.locator('text=' + product.productName)).toBeVisible({ timeout: 10000 })
  })
})
