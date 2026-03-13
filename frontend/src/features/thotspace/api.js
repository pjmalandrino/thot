import { apiFetch } from '../../shared/api/http.js'

export function fetchThotspaces() {
  return apiFetch('/api/thotspaces')
}

export function createThotspace(payload) {
  return apiFetch('/api/thotspaces', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateThotspace(id, payload) {
  return apiFetch(`/api/thotspaces/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteThotspace(id) {
  return apiFetch(`/api/thotspaces/${id}`, { method: 'DELETE' })
}
