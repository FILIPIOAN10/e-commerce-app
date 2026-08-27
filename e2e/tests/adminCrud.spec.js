import { test, expect } from '@playwright/test'
import { login } from './helpers/auth'

async function waitForCategory(page, name) {
  // New categories can land on the last page due to pagination.
  for (let attempt = 0; attempt < 10; attempt++) {
    if (await page.locator('text=' + name).first().isVisible({ timeout: 1000 }).catch(() => false)) {
      return
    }
    const nextBtn = page.getByRole('button', { name: 'Go to next page' })
    if (await nextBtn.isDisabled().catch(() => true)) {
      break
    }
    await nextBtn.click()
    await page.waitForTimeout(500)
  }
}

test.describe('Admin category CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await login(page, 'admin', 'adminPass')
    await page.goto('/en/admin/categories')
    await page.waitForTimeout(2000)
  })

  test('admin can create, update and delete a category', async ({ page }) => {
    const uniqueName = `E2E Category ${Date.now()}`
    const updatedName = `${uniqueName} Updated`

    // Create
    await page.click('button:has-text("Add Category")')
    await page.waitForSelector('#categoryName')
    await page.fill('#categoryName', uniqueName)
    await page.click('button:has-text("Save")')

    // Wait for the create request and the subsequent table refresh to complete
    await page.waitForResponse(
      response => response.url().includes('/en/admin/categories') && response.request().method() === 'POST',
      { timeout: 10000 }
    ).catch(() => null)
    await page.waitForTimeout(500)

    // New categories land on the last page due to pagination
    await waitForCategory(page, uniqueName)
    await expect(page.locator('text=' + uniqueName).first()).toBeVisible({ timeout: 5000 })

    // Update the correct row
    const row = page.locator('[role="row"]:has-text("' + uniqueName + '")')
    const editBtn = row.locator('button:has-text("Edit")')
    await expect(editBtn).toBeVisible({ timeout: 5000 })
    await editBtn.click()
    await page.fill('#categoryName', updatedName)
    await page.click('button:has-text("Update")')

    // Reset to first page after update (the action always refreshes page 0)
    await page.goto('/en/admin/categories')
    await page.waitForTimeout(1000)
    await waitForCategory(page, updatedName)
    await expect(page.locator('text=' + updatedName).first()).toBeVisible({ timeout: 5000 })

    // Delete the correct row
    const updatedRow = page.locator('[role="row"]:has-text("' + updatedName + '")')
    const deleteBtn = updatedRow.locator('button:has-text("Delete")')
    await expect(deleteBtn).toBeVisible({ timeout: 5000 })
    await deleteBtn.click()
    await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click()
    await page.waitForTimeout(1000)

    // Reset to first page after delete
    await page.goto('/en/admin/categories')
    await page.waitForTimeout(1000)
    await waitForCategory(page, updatedName)
    await expect(page.locator('text=' + updatedName).first()).toHaveCount(0, { timeout: 5000 })
  })
})
