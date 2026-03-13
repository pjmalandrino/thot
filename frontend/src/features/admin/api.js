import { apiFetch } from '../../shared/api/http.js'

// ── Providers ──────────────────────────────────────────────────────

export function fetchProviders() {
  return apiFetch('/api/admin/providers')
}

export function createProvider(payload) {
  return apiFetch('/api/admin/providers', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateProvider(id, payload) {
  return apiFetch(`/api/admin/providers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteProvider(id) {
  return apiFetch(`/api/admin/providers/${id}`, { method: 'DELETE' })
}

// ── Models ─────────────────────────────────────────────────────────

export function fetchProviderModels(providerId) {
  return apiFetch(`/api/admin/providers/${providerId}/models`)
}

export function createModel(providerId, payload) {
  return apiFetch(`/api/admin/providers/${providerId}/models`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateModel(id, payload) {
  return apiFetch(`/api/admin/models/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteModel(id) {
  return apiFetch(`/api/admin/models/${id}`, { method: 'DELETE' })
}
