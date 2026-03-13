import { apiFetch } from '../../shared/api/http.js'

export function fetchConversations() {
  return apiFetch('/api/conversations')
}

export function createConversation() {
  return apiFetch('/api/conversations', { method: 'POST' })
}

export function deleteConversation(id) {
  return apiFetch(`/api/conversations/${id}`, { method: 'DELETE' })
}
