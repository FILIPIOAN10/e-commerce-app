import { test, expect } from '@playwright/test'

test.describe('Registration flow', () => {
  test('register page loads with all required fields', async ({ page }) => {
    await page.goto('/Register')

    await expect(page.locator('h1')).toContainText('Register')
    await expect(page.locator('#username')).toBeVisible()
    await expect(page.locator('#email')).toBeVisible()
    await expect(page.locator('#password')).toBeVisible()
    await expect(page.locator('#passwordHint')).toBeVisible()
    await expect(page.locator('select')).toBeVisible()
  })

  test('register page has role selector with User and Seller options', async ({ page }) => {
    await page.goto('/Register')

    const roleSelect = page.locator('select')
    await expect(roleSelect).toBeVisible()

    // <option> elements inside <select> are not "visible" in CSS sense,
    // so we check their values via evaluate
    const optionValues = await roleSelect.evaluate((sel) =>
      Array.from(sel.options).map((opt) => opt.value)
    )
    expect(optionValues).toContain('ROLE_USER')
    expect(optionValues).toContain('ROLE_SELLER')
  })

  test('form validation prevents empty submission', async ({ page }) => {
    await page.goto('/Register')

    await page.click('button[type="submit"]')

    // Should stay on register page
    await expect(page).toHaveURL(/\/Register/)
  })

  test('successful registration redirects to login', async ({ page }) => {
    const uniqueUser = `testuser_${Date.now()}`
    await page.goto('/Register')

    await page.fill('#username', uniqueUser)
    await page.fill('#email', `${uniqueUser}@test.com`)
    await page.fill('#password', 'testpass123')
    await page.fill('#passwordHint', 'my hint')
    await page.selectOption('select', 'ROLE_USER')
    await page.click('button[type="submit"]')

    // Should redirect to login page after successful registration
    await expect(page).toHaveURL(/\/login/, { timeout: 15000 })
  })
})
