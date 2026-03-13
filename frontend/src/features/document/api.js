import { apiFetch } from '../../shared/api/http.js'

export function uploadDocument(conversationId, file) {
  const formData = new FormData()
  formData.append('file', file)

  return apiFetch(`/api/conversations/${conversationId}/documents`, {
    method: 'POST',
    body: formData,
    skipContentType: true
  })
}

export function fetchDocuments(conversationId) {
  return apiFetch(`/api/conversations/${conversationId}/documents`)
}

export function deleteDocument(conversationId, documentId) {
  return apiFetch(`/api/conversations/${conversationId}/documents/${documentId}`, {
    method: 'DELETE'
  })
}
