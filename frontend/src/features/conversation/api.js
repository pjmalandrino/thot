import { apiFetch } from '../../shared/api/http.js'

export function fetchConversations() {
  return apiFetch('/api/conversations')
}

export function createConversation() {
  return apiFetch('/api/conversations', { method: 'POST' })
}

export function renameConversation(id, title) {
  return apiFetch(`/api/conversations/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ title })
  })
}

export function deleteConversation(id) {
  return apiFetch(`/api/conversations/${id}`, { method: 'DELETE' })
}
