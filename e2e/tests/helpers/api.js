const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api'

// Simple in-memory cookie jar so that the XSRF-TOKEN (and any session cookies)
// flow between requests the same way a browser would.
const cookies = {}

function parseSetCookie(raw) {
  const firstPart = (raw || '').split(';')[0].trim()
  const eq = firstPart.indexOf('=')
  if (eq === -1) return null
  const name = firstPart.slice(0, eq).trim()
  const value = firstPart.slice(eq + 1).trim()
  const lower = raw.toLowerCase()
  if (!value || lower.includes('max-age=0')) {
    delete cookies[name]
    return null
  }
  cookies[name] = value
  return { name, value }
}

function updateCookies(setCookieHeaders) {
  if (!setCookieHeaders) return
  const entries = Array.isArray(setCookieHeaders) ? setCookieHeaders : [setCookieHeaders]
  for (const raw of entries) {
    if (raw) parseSetCookie(raw)
  }
}

function cookieHeader() {
  return Object.entries(cookies)
    .map(([name, value]) => `${name}=${value}`)
    .join('; ')
}

function csrfHeader() {
  const xsrf = cookies['XSRF-TOKEN']
  if (!xsrf) return {}
  return {
    'X-CSRF-TOKEN': xsrf,
    'X-XSRF-TOKEN': xsrf,
  }
}

async function ensureCsrfToken() {
  if (cookies['XSRF-TOKEN']) return
  const res = await fetch(`${API_BASE}/public/products`)
  const setCookie = res.headers.getSetCookie ? res.headers.getSetCookie() : res.headers.get('set-cookie')
  updateCookies(setCookie)
}

async function apiRequest(path, options = {}) {
  await ensureCsrfToken()

  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
    ...csrfHeader(),
  }

  const cookie = cookieHeader()
  if (cookie) {
    headers['Cookie'] = cookie
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  const setCookie = res.headers.getSetCookie ? res.headers.getSetCookie() : res.headers.get('set-cookie')
  updateCookies(setCookie)

  return res
}

export async function loginAsAdmin() {
  const res = await apiRequest('/auth/signin', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin', password: 'adminPass' }),
  })

  if (!res.ok) {
    throw new Error(`Admin login failed: ${res.status}`)
  }

  const data = await res.json()
  return data.jwtToken
}

export async function createCategory(token, name) {
  const res = await apiRequest('/admin/categories', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ categoryName: name }),
  })

  if (!res.ok) {
    throw new Error(`Create category failed: ${res.status}`)
  }

  return res.json()
}

export async function updateCategory(token, id, name) {
  const res = await apiRequest(`/admin/categories/${id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ categoryName: name }),
  })

  if (!res.ok) {
    throw new Error(`Update category failed: ${res.status}`)
  }
}

export async function deleteCategory(token, id) {
  const res = await apiRequest(`/admin/categories/${id}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  if (!res.ok) {
    throw new Error(`Delete category failed: ${res.status}`)
  }
}

export async function createProduct(token, categoryId, product) {
  const res = await apiRequest(`/admin/categories/${categoryId}/product`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(product),
  })

  if (!res.ok) {
    throw new Error(`Create product failed: ${res.status}`)
  }

  return res.json()
}
