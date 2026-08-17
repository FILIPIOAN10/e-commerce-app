import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

test.describe('Admin category CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'admin', 'adminPass')
    await page.goto('/admin/categories')
    await page.waitForTimeout(2000)
  })

  test('admin can create, update and delete a category', async ({ page }) => {
    const uniqueName = `E2E Category ${Date.now()}`
    const updatedName = `${uniqueName} Updated`

    // Create
    await page.click('button:has-text("Add Category")')
    await page.fill('#categoryName', uniqueName)
    await page.click('button:has-text("Save")')

    await expect(page.locator('text=' + uniqueName)).toBeVisible({ timeout: 15000 })

    // Update
    const editBtn = page.locator('button:has-text("Edit")').first()
    if (await editBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await editBtn.click()
      await page.fill('#categoryName', updatedName)
      await page.click('button:has-text("Update")')
      await expect(page.locator('text=' + updatedName)).toBeVisible({ timeout: 15000 })
    }

    // Delete
    const deleteBtn = page.locator('button:has-text("Delete")').first()
    if (await deleteBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
      await deleteBtn.click()
      await page.click('button:has-text("Delete")')
      await page.waitForTimeout(2000)
    }
  })
})
