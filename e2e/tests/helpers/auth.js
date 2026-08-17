export async function login(page, username, password) {
  await page.goto('/login')
  await page.fill('#username', username)
  await page.fill('#password', password)
  await page.click('button[type="submit"]')
  await page.waitForURL(/\/$/, { timeout: 10000 })
}
