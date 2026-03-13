import { apiFetch } from '../../shared/api/http.js'

export function fetchModels() {
  return apiFetch('/api/llm/models')
}
