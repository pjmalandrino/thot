import { ref } from 'vue'
import { getToken, refreshToken } from '../../../shared/auth/keycloak.js'

/**
 * Composable for SSE streaming completions (Think, Research & Lab modes).
 * Consumes Server-Sent Events and provides reactive state for the UI.
 *
 * In addition to flat state (thinking, answer, sources), tracks per-step
 * content in stepContents for Lab mode's accordion rendering.
 */
export function useStreamingCompletion() {
  const steps = ref([])
  const thinking = ref('')
  const answer = ref('')
  const sources = ref([])
  const streaming = ref(false)
  const streamError = ref('')
  const doneData = ref(null)

  // Per-step content tracking (used by Lab accordion)
  const activeStepId = ref(null)
  const stepContents = ref({})
  // Structure: { [stepId]: { label, status, detail, thinking, answer, sources[] } }
  let previousSourcesCount = 0

  async function streamCompletion(conversationId, { prompt, modelId, mode }) {
    // Reset state
    steps.value = []
    thinking.value = ''
    answer.value = ''
    sources.value = []
    streaming.value = true
    streamError.value = ''
    doneData.value = null
    activeStepId.value = null
    stepContents.value = {}
    previousSourcesCount = 0

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
      let eventName = null // Persists across chunks so split event:/data: lines are handled

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() // Keep incomplete last line

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
      // Safety net: if stream ended without a 'done' event, synthesize one
      // so the watcher in ChatWindow can push the interaction to history
      if (!doneData.value && !streamError.value && answer.value) {
        doneData.value = {
          response: answer.value,
          thinking: thinking.value || null,
          sources: [...sources.value],
          autoWebSearchTriggered: false
        }
      }
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

        // Track per-step content
        if (data.status === 'running') {
          activeStepId.value = data.stepId
          if (!stepContents.value[data.stepId]) {
            stepContents.value = {
              ...stepContents.value,
              [data.stepId]: {
                label: data.label || '',
                status: 'running',
                detail: data.detail || '',
                thinking: '',
                answer: '',
                sources: []
              }
            }
          }
        } else if (data.status === 'done' && stepContents.value[data.stepId]) {
          stepContents.value = {
            ...stepContents.value,
            [data.stepId]: {
              ...stepContents.value[data.stepId],
              status: 'done',
              detail: data.detail || stepContents.value[data.stepId].detail
            }
          }
        }
        break
      }

      case 'thinking':
        thinking.value += data.content
        // Associate with active step
        if (activeStepId.value && stepContents.value[activeStepId.value]) {
          stepContents.value = {
            ...stepContents.value,
            [activeStepId.value]: {
              ...stepContents.value[activeStepId.value],
              thinking: stepContents.value[activeStepId.value].thinking + data.content
            }
          }
        }
        break

      case 'answer':
        answer.value += data.content
        // Associate with active step
        if (activeStepId.value && stepContents.value[activeStepId.value]) {
          stepContents.value = {
            ...stepContents.value,
            [activeStepId.value]: {
              ...stepContents.value[activeStepId.value],
              answer: stepContents.value[activeStepId.value].answer + data.content
            }
          }
        }
        break

      case 'sources': {
        const allSources = data.sources || []
        sources.value = allSources
        // Track delta for active step (only if growing = new sources found)
        if (activeStepId.value && stepContents.value[activeStepId.value]
            && allSources.length > previousSourcesCount) {
          const newSources = allSources.slice(previousSourcesCount)
          stepContents.value = {
            ...stepContents.value,
            [activeStepId.value]: {
              ...stepContents.value[activeStepId.value],
              sources: [...stepContents.value[activeStepId.value].sources, ...newSources]
            }
          }
          previousSourcesCount = allSources.length
        }
        break
      }

      case 'done':
        // Defensive: ensure all steps are marked as done when stream completes
        steps.value = steps.value.map(s =>
          s.status === 'running' ? { ...s, status: 'done' } : s
        )
        // Also mark step contents as done
        const updated = { ...stepContents.value }
        for (const key of Object.keys(updated)) {
          if (updated[key].status === 'running') {
            updated[key] = { ...updated[key], status: 'done' }
          }
        }
        stepContents.value = updated
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
    activeStepId.value = null
    stepContents.value = {}
    previousSourcesCount = 0
  }

  return {
    steps, thinking, answer, sources, streaming, streamError, doneData,
    activeStepId, stepContents,
    streamCompletion, reset
  }
}
