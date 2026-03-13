import { getToken, refreshToken } from '../auth/keycloak.js'

export async function apiFetch(url, options = {}) {
  await refreshToken()

  const headers = {
    'Authorization': `Bearer ${getToken()}`,
    ...options.headers
  }

  // Ne pas forcer Content-Type pour FormData (le navigateur gere le boundary multipart)
  if (!options.skipContentType) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(url, {
    ...options,
    headers
  })
  if (!response.ok) throw new Error(`API error: ${response.status}`)
  if (response.status === 204) return null
  return response.json()
}
