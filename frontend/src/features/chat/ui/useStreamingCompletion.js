import { ref } from 'vue'
import { getToken, refreshToken } from '../../../shared/auth/keycloak.js'

/**
 * Composable for SSE streaming completions (Think & Research modes).
 * Consumes Server-Sent Events and provides reactive state for the UI.
 */
export function useStreamingCompletion() {
  const steps = ref([])
  const thinking = ref('')
  const answer = ref('')
  const sources = ref([])
  const streaming = ref(false)
  const streamError = ref('')
  const doneData = ref(null)

  async function streamCompletion(conversationId, { prompt, modelId, mode }) {
    // Reset state
    steps.value = []
    thinking.value = ''
    answer.value = ''
    sources.value = []
    streaming.value = true
    streamError.value = ''
    doneData.value = null

    try {
      await refreshToken()
      const token = getToken()
      const params = new URLSearchParams({ prompt, modelId, mode })

      const response = await fetch(
        `/api/conversations/${conversationId}/stream?${params}`,
        { headers: { 'Authorization': `Bearer ${token}` } }
      )

      if (!response.ok) {
        throw new Error(`Stream error: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() // Keep incomplete last line

        let eventName = null
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim()
          } else if (line.startsWith('data:') && eventName) {
            try {
              const data = JSON.parse(line.slice(5).trim())
              handleEvent(eventName, data)
            } catch {
              // Skip unparseable data
            }
            eventName = null
          }
        }
      }
    } catch (err) {
      streamError.value = err.message
    } finally {
      streaming.value = false
    }
  }

  function handleEvent(event, data) {
    switch (event) {
      case 'step': {
        const idx = steps.value.findIndex(s => s.stepId === data.stepId)
        if (idx >= 0) {
          steps.value[idx] = { ...steps.value[idx], ...data }
        } else {
          steps.value.push(data)
        }
        break
      }
      case 'thinking':
        thinking.value += data.content
        break
      case 'answer':
        answer.value += data.content
        break
      case 'sources':
        sources.value = data.sources || []
        break
      case 'done':
        doneData.value = data
        break
      case 'error':
        streamError.value = data.message || 'Erreur de streaming'
        break
      case 'clarification':
        // Handled by the parent component
        doneData.value = { clarification: true, ...data }
        break
    }
  }

  function reset() {
    steps.value = []
    thinking.value = ''
    answer.value = ''
    sources.value = []
    streaming.value = false
    streamError.value = ''
    doneData.value = null
  }

  return { steps, thinking, answer, sources, streaming, streamError, doneData, streamCompletion, reset }
}
