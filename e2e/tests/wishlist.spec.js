import { test, expect } from '@playwright/test'
import { loginAsAdmin, createCategory, createProduct } from './helpers/api'
import { login } from './helpers/auth'

test.describe('Wishlist flow', () => {
  let product

  test.beforeAll(async () => {
    await loginAsAdmin()
    const category = await createCategory(`Wishlist Category ${Date.now()}`)
    product = await createProduct(category.categoryId, {
      productName: `Wishlist Product ${Date.now()}`,
      description: 'A product for wishlist testing',
      quantity: 10,
      price: 49.99,
      discount: 0,
      specialPrice: 49.99,
      tags: 'wishlist, e2e',
      image: 'cal.png',
    })
  })

  test('user can add and remove a product from wishlist', async ({ page }) => {
    await login(page, 'user1', 'password1')

    await page.goto(`/products/${product.productId}`)
    await page.waitForSelector('h1', { timeout: 10000 })

    const wishlistBtn = page.locator('[data-testid="wishlist-button"]').first()
    if (await wishlistBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await wishlistBtn.click()
      await page.waitForTimeout(1000)
    }

    await page.goto('/wishlist')
    await expect(page).toHaveURL(/\/wishlist/, { timeout: 10000 })
    await expect(page.locator('text=My Wishlist')).toBeVisible()
    await expect(page.locator('text=' + product.productName)).toBeVisible({ timeout: 10000 })

    const removeBtn = page.locator(`[data-testid="wishlist-item"][data-product-id="${product.productId}"] [data-testid="remove-from-wishlist"]`)
    await expect(removeBtn).toBeVisible({ timeout: 5000 })
    await removeBtn.click()
    await expect(page.locator('text=' + product.productName)).toHaveCount(0, { timeout: 10000 })
  })
})
