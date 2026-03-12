<template>
  <div class="hello-world">
    <h2>Greetings</h2>

    <div class="input-section">
      <input
        v-model="newMessage"
        type="text"
        placeholder="Type a greeting..."
        @keyup.enter="sendGreeting"
      />
      <button @click="sendGreeting" :disabled="!newMessage.trim()">Send</button>
    </div>

    <div v-if="error" class="error">{{ error }}</div>

    <div v-if="loading" class="loading">Loading...</div>

    <ul v-else class="greeting-list">
      <li v-for="greeting in greetings" :key="greeting.id" class="greeting-item">
        <span class="message">{{ greeting.message }}</span>
        <span class="date">{{ formatDate(greeting.createdAt) }}</span>
      </li>
      <li v-if="greetings.length === 0" class="empty">No greetings yet. Send one!</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getToken, refreshToken } from '../keycloak.js'

const greetings = ref([])
const newMessage = ref('')
const loading = ref(true)
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
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`)
  }
  return response.json()
}

async function fetchGreetings() {
  try {
    error.value = ''
    greetings.value = await apiFetch('/api/greetings')
  } catch (e) {
    error.value = 'Failed to load greetings: ' + e.message
  } finally {
    loading.value = false
  }
}

async function sendGreeting() {
  if (!newMessage.value.trim()) return

  try {
    error.value = ''
    await apiFetch('/api/greetings', {
      method: 'POST',
      body: JSON.stringify({ message: newMessage.value.trim() })
    })
    newMessage.value = ''
    await fetchGreetings()
  } catch (e) {
    error.value = 'Failed to send greeting: ' + e.message
  }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString()
}

onMounted(fetchGreetings)
</script>

<style scoped>
.hello-world {
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

.greeting-list {
  list-style: none;
}

.greeting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #eee;
}

.greeting-item:last-child {
  border-bottom: none;
}

.message {
  font-size: 14px;
}

.date {
  font-size: 12px;
  color: #999;
}

.empty {
  color: #999;
  text-align: center;
  padding: 24px;
}
</style>
