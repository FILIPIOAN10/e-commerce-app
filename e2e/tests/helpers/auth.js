export async function login(page, username, password) {
  await page.goto('/en/login')
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/\/en$/, { timeout: 10000 })
  await page.waitForSelector('[data-testid="user-avatar"]', { timeout: 10000 })
}
