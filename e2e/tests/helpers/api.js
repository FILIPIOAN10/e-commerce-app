const API_BASE = process.env.API_BASE_URL || 'http://localhost:8080/api'

export async function loginAsAdmin() {
  const res = await fetch(`${API_BASE}/auth/signin`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'adminPass' }),
  })

  if (!res.ok) {
    throw new Error(`Admin login failed: ${res.status}`)
  }

  const data = await res.json()
  return data.jwtToken
}

export async function createCategory(token, name) {
  const res = await fetch(`${API_BASE}/admin/categories`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
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
  const res = await fetch(`${API_BASE}/admin/categories/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ categoryName: name }),
  })

  if (!res.ok) {
    throw new Error(`Update category failed: ${res.status}`)
  }
}

export async function deleteCategory(token, id) {
  const res = await fetch(`${API_BASE}/admin/categories/${id}`, {
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
  const res = await fetch(`${API_BASE}/admin/categories/${categoryId}/product`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(product),
  })

  if (!res.ok) {
    throw new Error(`Create product failed: ${res.status}`)
  }

  return res.json()
}
