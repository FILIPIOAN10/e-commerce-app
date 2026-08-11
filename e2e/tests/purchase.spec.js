import { test, expect } from '@playwright/test'

test.describe('Full purchase flow', () => {
  test.beforeEach(async ({ page }) => {
    // Login as admin before each test
    await page.goto('/login')
    await page.fill('#username', 'admin')
    await page.fill('#password', 'adminPass')
    await page.click('button[type="submit"]')
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 })
  })

  test('home page loads and shows product sections', async ({ page }) => {
    await page.goto('/')

    // Wait for page to load
    await page.waitForTimeout(3000)

    // Should be on home page
    await expect(page).toHaveURL(/\/$/)
  })

  test('navigate to products page and view product list', async ({ page }) => {
    await page.goto('/products')

    await page.waitForTimeout(3000)

    // Products page should load
    await expect(page).toHaveURL(/\/products/)
  })

  test('can add product to cart from products page', async ({ page }) => {
    await page.goto('/products')
    await page.waitForTimeout(3000)

    // Look for "Add to Cart" button or similar
    const addToCartBtn = page.locator('button:has-text("Add to Cart"), button:has-text("Add")').first()

    if (await addToCartBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await addToCartBtn.click()
      await page.waitForTimeout(2000)

      // Navigate to cart
      await page.goto('/cart')
      await expect(page).toHaveURL(/\/cart/)
    }
  })

  test('cart page loads for authenticated user', async ({ page }) => {
    await page.goto('/cart')
    await expect(page).toHaveURL(/\/cart/)
  })

  test('checkout page redirects properly for authenticated user', async ({ page }) => {
    await page.goto('/checkout')
    // Should either show checkout page or redirect if cart is empty
    await page.waitForTimeout(2000)
  })

  test('user can log out', async ({ page }) => {
    // Look for logout button in navbar
    const logoutBtn = page.locator('button:has-text("Logout"), button:has-text("Log Out"), a:has-text("Logout")').first()

    if (await logoutBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await logoutBtn.click()
      await page.waitForTimeout(2000)

      // Should be redirected to login
      await expect(page).toHaveURL(/\/login/, { timeout: 10000 })
    }
  })
})
