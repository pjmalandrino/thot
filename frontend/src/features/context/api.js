import { apiFetch } from '../../shared/api/http.js'

/**
 * Analyse contextuelle d'un prompt avant envoi au LLM.
 * Le modele est configure cote backend (ContextProperties) — pas de modelId ici.
 * @param {string} prompt - Le message de l'utilisateur
 * @param {string[]} steps - Les steps a activer (ex: ['vagueness'])
 * @returns {Promise<{status: string, confidence?: number, message?: string, suggestions?: string[]}>}
 */
export function analyzeContext(prompt, steps) {
  return apiFetch('/api/context/analyze', {
    method: 'POST',
    body: JSON.stringify({ prompt, steps })
  })
}
