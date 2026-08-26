import { test, expect } from '@playwright/test'
import { loginAsAdmin, createCategory, createProduct } from './helpers/api'

test.describe('Guest checkout flow', () => {
  let product

  test.beforeAll(async () => {
    await loginAsAdmin()
    const category = await createCategory(`Guest Category ${Date.now()}`)
    product = await createProduct(category.categoryId, {
      productName: `Guest Product ${Date.now()}`,
      description: 'A product for guest checkout testing',
      quantity: 10,
      price: 59.99,
      discount: 0,
      specialPrice: 59.99,
      tags: 'guest, e2e',
      image: 'cal.png',
    })
  })

  test('guest can add product to cart and complete checkout', async ({ page }) => {
    await page.goto(`/en/products/${product.productId}`)
    await page.waitForSelector('h1', { timeout: 10000 })

    const addToCartBtn = page.locator('button:has-text("Add to Cart")').first()
    if (await addToCartBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addToCartBtn.click()
      await page.waitForTimeout(1000)
    }

    await page.goto('/en/guest-checkout')
    await expect(page).toHaveURL(/\/guest-checkout/, { timeout: 10000 })
    await expect(page.locator('text=Guest Checkout')).toBeVisible()

    const uniqueEmail = `guest_${Date.now()}@test.com`
    await page.fill('input[name="email"]', uniqueEmail)
    await page.fill('input[name="buildingName"]', 'Building 1')
    await page.fill('input[name="street"]', 'Main Street 1')
    await page.fill('input[name="city"]', 'Bucharest')
    await page.fill('input[name="state"]', 'Bucharest')
    await page.fill('input[name="country"]', 'Romania')
    await page.fill('input[name="pincode"]', '123456')

    await page.click('button:has-text("Place Order")')

    await expect(page).toHaveURL(/track-order/, { timeout: 15000 })
  })
})
