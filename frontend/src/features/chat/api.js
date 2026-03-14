import { apiFetch } from '../../shared/api/http.js'

export function fetchCompletions(conversationId) {
  return apiFetch(`/api/conversations/${conversationId}/completions`)
}

export function sendCompletion(conversationId, { prompt, modelId, clarificationContext }) {
  const body = { prompt, modelId }
  if (clarificationContext) body.clarificationContext = clarificationContext
  return apiFetch(`/api/conversations/${conversationId}/completions`, {
    method: 'POST',
    body: JSON.stringify(body)
  })
}
