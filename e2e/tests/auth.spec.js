import { test, expect } from '@playwright/test'

test.describe('Authentication flow', () => {
  test('login with seed admin credentials succeeds', async ({ page }) => {
    await page.goto('/login')

    await page.fill('#username', 'admin')
    await page.fill('#password', 'adminPass')
    await page.click('button[type="submit"]')

    // Should redirect to home page
    await expect(page).toHaveURL(/\/$|\/$/)

    // Navbar should show admin user info, not "Login"
    await expect(page.locator('text=Login')).not.toBeVisible({ timeout: 10000 })
  })

  test('login with wrong password shows error toast', async ({ page }) => {
    await page.goto('/login')

    await page.fill('#username', 'admin')
    await page.fill('#password', 'wrongpassword')
    await page.click('button[type="submit"]')

    // Should stay on login page
    await expect(page).toHaveURL(/\/login/)

    // Error toast should appear
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 5000 })
  })

  test('login with non-existent user shows error toast', async ({ page }) => {
    await page.goto('/login')

    await page.fill('#username', 'nonexistentuser')
    await page.fill('#password', 'somepassword')
    await page.click('button[type="submit"]')

    await expect(page).toHaveURL(/\/login/)
    await expect(page.locator('[role="status"]')).toBeVisible({ timeout: 5000 })
  })

  test('login page has link to register', async ({ page }) => {
    await page.goto('/login')

    await expect(page.locator('a:has-text("SignUp")')).toBeVisible()
  })

  test('login page has link to forgot password', async ({ page }) => {
    await page.goto('/login')

    await expect(page.locator('a:has-text("Forgot Password")')).toBeVisible()
  })
})
