<template>
  <div class="completions">
    <h2>Ask the LLM</h2>

    <div class="input-section">
      <input
        v-model="prompt"
        type="text"
        placeholder="Type your prompt..."
        @keyup.enter="sendPrompt"
      />
      <button @click="sendPrompt" :disabled="!prompt.trim() || sending">
        {{ sending ? 'Sending...' : 'Send' }}
      </button>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div v-if="loading" class="loading">Loading...</div>

    <ul v-else class="interaction-list">
      <li v-for="item in interactions" :key="item.id" class="interaction-item">
        <div class="prompt-line">
          <span class="label you">You</span>
          <span class="prompt-text">{{ item.prompt }}</span>
        </div>
        <div class="response-line">
          <span class="label llm">LLM</span>
          <span class="response-text">{{ item.response }}</span>
        </div>
        <span class="date">{{ formatDate(item.createdAt) }}</span>
      </li>
      <li v-if="interactions.length === 0" class="empty">No interactions yet. Ask something!</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getToken, refreshToken } from '../keycloak.js'

const interactions = ref([])
const prompt = ref('')
const loading = ref(true)
const sending = ref(false)
const error = ref('')

async function apiFetch(url, options = {}) {
  await refreshToken()
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`,
      ...options.headers
    }
  })
  if (!response.ok) throw new Error(`API error: ${response.status}`)
  return response.json()
}

async function fetchInteractions() {
  try {
    error.value = ''
    interactions.value = await apiFetch('/api/completions')
  } catch (e) {
    error.value = 'Failed to load interactions: ' + e.message
  } finally {
    loading.value = false
  }
}

async function sendPrompt() {
  if (!prompt.value.trim() || sending.value) return
  sending.value = true
  error.value = ''
  try {
    const result = await apiFetch('/api/completions', {
      method: 'POST',
      body: JSON.stringify({ prompt: prompt.value.trim() })
    })
    interactions.value.unshift(result)
    prompt.value = ''
  } catch (e) {
    error.value = 'Failed to send prompt: ' + e.message
  } finally {
    sending.value = false
  }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString()
}

onMounted(fetchInteractions)
</script>

<style scoped>
.completions {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

h2 {
  margin-bottom: 16px;
}

.input-section {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.input-section input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 14px;
}

.input-section button {
  padding: 10px 24px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  white-space: nowrap;
}

.input-section button:hover:not(:disabled) {
  background: #357abd;
}

.input-section button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error {
  color: #d32f2f;
  padding: 8px;
  margin-bottom: 12px;
  background: #fdecea;
  border-radius: 4px;
}

.loading {
  color: #666;
  padding: 16px;
  text-align: center;
}

.interaction-list {
  list-style: none;
}

.interaction-item {
  padding: 14px;
  border-bottom: 1px solid #eee;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.interaction-item:last-child {
  border-bottom: none;
}

.prompt-line,
.response-line {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  margin-top: 1px;
}

.label.you { background: #4a90d9; }
.label.llm { background: #6c757d; }

.prompt-text {
  font-size: 14px;
  color: #333;
}

.response-text {
  font-size: 14px;
  color: #555;
  white-space: pre-wrap;
}

.date {
  font-size: 11px;
  color: #999;
  align-self: flex-end;
}

.empty {
  color: #999;
  text-align: center;
  padding: 24px;
}
</style>
