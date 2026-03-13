import { apiFetch } from '../../shared/api/http.js'

export function fetchCompletions(conversationId) {
  return apiFetch(`/api/conversations/${conversationId}/completions`)
}

export function sendCompletion(conversationId, { prompt, webSearch, modelId }) {
  return apiFetch(`/api/conversations/${conversationId}/completions`, {
    method: 'POST',
    body: JSON.stringify({ prompt, webSearch, modelId })
  })
}
