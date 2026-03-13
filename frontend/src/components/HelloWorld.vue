<template>
  <div class="chat-layout">
    <div class="chat-header">
      <div class="header-left">
        <span class="header-label">Session</span>
        <span class="header-id">#{{ conversationId }}</span>
      </div>
      <span class="header-pill">llama3.2:3b</span>
    </div>

    <div class="messages" ref="messagesEl">
      <div v-if="loading" class="state-msg">
        <span class="loader"></span>
        <span>Chargement</span>
      </div>
      <div v-else-if="error" class="state-msg state-error">
        <span>{{ error }}</span>
      </div>
      <template v-else>
        <div v-if="interactions.length === 0" class="empty-chat">
          <span class="empty-hero">?</span>
          <span class="empty-text">En attente d'une instruction</span>
        </div>
        <div v-for="(item, idx) in interactions" :key="item.id" class="interaction">
          <div class="msg msg-user">
            <div class="msg-head">
              <span class="pill pill-user">Vous</span>
              <span class="msg-meta">#{{ idx + 1 }}</span>
            </div>
            <p class="msg-text user-text">{{ item.prompt }}</p>
          </div>
          <div class="msg msg-llm">
            <div class="msg-head">
              <span class="pill pill-llm">Thot</span>
              <span class="msg-meta">{{ formatDate(item.createdAt) }}</span>
            </div>
            <div class="msg-text llm-text md-content" v-html="renderMarkdown(item.response)"></div>
          </div>
        </div>
      </template>
    </div>

    <div class="input-area">
      <div class="input-box" :class="{ focused: inputFocused, generating: sending }">
        <textarea
          ref="textareaEl"
          v-model="prompt"
          class="input-field"
          placeholder="Posez votre question..."
          :disabled="sending"
          rows="1"
          @keydown.enter.exact="sendPrompt"
          @input="autoResize"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
        ></textarea>
        <div class="input-bottom">
          <div class="input-hints">
            <kbd>&#9166;</kbd> envoyer
            <span class="dot">&middot;</span>
            <kbd>&#8679;&#9166;</kbd> ligne
          </div>
          <button class="send-btn" :disabled="!prompt.trim() || sending" @click="sendPrompt">
            <span v-if="sending" class="send-loader"></span>
            <span v-else>Envoyer</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { getToken, refreshToken } from '../keycloak.js'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

const props = defineProps({ conversationId: { type: Number, required: true } })

const interactions = ref([])
const prompt = ref('')
const loading = ref(true)
const sending = ref(false)
const error = ref('')
const messagesEl = ref(null)
const textareaEl = ref(null)
const inputFocused = ref(false)

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

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
    interactions.value = await apiFetch(`/api/conversations/${props.conversationId}/completions`)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function autoResize() {
  const el = textareaEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function sendPrompt(e) {
  if (e) e.preventDefault()
  if (!prompt.value.trim() || sending.value) return
  sending.value = true
  error.value = ''
  try {
    const result = await apiFetch(`/api/conversations/${props.conversationId}/completions`, {
      method: 'POST',
      body: JSON.stringify({ prompt: prompt.value.trim() })
    })
    interactions.value.push(result)
    prompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'
    await nextTick()
    scrollToBottom()
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}

function scrollToBottom() {
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(fetchInteractions)
</script>

<style scoped>
.chat-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
}

/* ══════════════════════════════════════
   HEADER
   ══════════════════════════════════════ */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.1rem 3rem;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.header-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--text);
}

.header-id {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--text-light);
}

.header-pill {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  color: var(--text-mid);
  border: 1px solid var(--border);
  padding: 0.25rem 0.75rem;
  border-radius: 50px;
  letter-spacing: 0.02em;
}

/* ══════════════════════════════════════
   MESSAGES
   ══════════════════════════════════════ */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 2.5rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 0;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

.state-msg {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  justify-content: center;
  font-size: 0.8rem;
  color: var(--text-light);
  margin-top: 6rem;
}

.loader {
  width: 14px; height: 14px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.state-error { color: #D44A3A; }

/* ── Empty ── */
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  margin-top: 10rem;
}

.empty-hero {
  font-size: 6rem;
  font-weight: 200;
  color: var(--text);
  opacity: 0.05;
  line-height: 1;
}

.empty-text {
  font-size: 0.85rem;
  color: var(--text-light);
}

/* ══════════════════════════════════════
   INTERACTION
   ══════════════════════════════════════ */
.interaction {
  padding-bottom: 2.5rem;
  margin-bottom: 2.5rem;
  border-bottom: 1px solid var(--border);
}

.interaction:last-child { border-bottom: none; margin-bottom: 0; }

.msg {
  padding: 1.25rem 0;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

/* ── Pills ── */
.pill {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  padding: 0.3rem 0.85rem;
  border-radius: 50px;
}

.pill-user {
  background: var(--border);
  color: var(--text-mid);
}

.pill-llm {
  background: var(--dark);
  color: var(--bg);
}

.msg-meta {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-light);
}

/* ── Text ── */
.msg-text {
  font-size: 0.95rem;
  line-height: 1.8;
  color: var(--text);
}

.user-text {
  white-space: pre-wrap;
  color: var(--text-mid);
}

.llm-text {
  color: var(--text);
}

/* ══════════════════════════════════════
   MARKDOWN
   ══════════════════════════════════════ */
.md-content :deep(p) { margin-bottom: 1rem; }
.md-content :deep(p:last-child) { margin-bottom: 0; }
.md-content :deep(strong) { font-weight: 600; }
.md-content :deep(em) { font-style: italic; }

.md-content :deep(code) {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.85rem;
  background: rgba(0,0,0,0.05);
  padding: 0.15rem 0.45rem;
  border-radius: 3px;
}

.md-content :deep(pre) {
  background: var(--dark);
  color: #E0DCD4;
  padding: 1.5rem;
  border-radius: 0;
  overflow-x: auto;
  margin: 1.25rem 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.82rem;
  line-height: 1.7;
  border: 1px solid var(--dark-border);
}

.md-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.md-content :deep(ul), .md-content :deep(ol) {
  padding-left: 1.5rem;
  margin-bottom: 1rem;
}

.md-content :deep(li) { margin-bottom: 0.35rem; }

.md-content :deep(h1), .md-content :deep(h2), .md-content :deep(h3) {
  font-weight: 600;
  color: var(--text);
  margin: 1.5rem 0 0.5rem 0;
  letter-spacing: -0.01em;
}

.md-content :deep(h1) { font-size: 1.3rem; }
.md-content :deep(h2) { font-size: 1.15rem; }
.md-content :deep(h3) { font-size: 1rem; }

.md-content :deep(blockquote) {
  border-left: 3px solid var(--accent);
  padding-left: 1.25rem;
  color: var(--text-mid);
  margin: 1rem 0;
}

.md-content :deep(a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color 0.2s ease;
}

.md-content :deep(a:hover) {
  color: var(--accent-light);
}

.md-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.5rem 0;
}

/* ══════════════════════════════════════
   INPUT
   ══════════════════════════════════════ */
.input-area {
  flex-shrink: 0;
  padding: 0 3rem 2rem;
}

.input-box {
  border: 1px solid var(--border);
  border-radius: 0;
  transition: all 0.3s ease;
  background: #FFF;
}

.input-box.focused {
  border-color: var(--text);
}

.input-box.generating {
  border-color: var(--accent);
}

.input-field {
  display: block;
  width: 100%;
  padding: 1rem 1.25rem 0.75rem;
  background: transparent;
  border: none;
  color: var(--text);
  font-family: 'Inter', sans-serif;
  font-size: 0.95rem;
  font-weight: 400;
  line-height: 1.6;
  outline: none;
  resize: none;
  min-height: 2.8rem;
  max-height: 160px;
}

.input-field::placeholder {
  color: var(--text-light);
  font-weight: 300;
}

.input-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.65rem 0.65rem 1.25rem;
  border-top: 1px solid var(--border);
}

.input-hints {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
}

.input-hints kbd {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  background: var(--bg);
  padding: 0.1rem 0.4rem;
  border: 1px solid var(--border);
  color: var(--text-mid);
}

.dot { margin: 0 0.15rem; opacity: 0.3; }

.send-btn {
  padding: 0.5rem 1.5rem;
  background: var(--dark);
  border: none;
  color: #FFF;
  font-size: 0.72rem;
  font-weight: 500;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: all 0.3s ease;
  min-width: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn:hover:not(:disabled) {
  background: var(--accent);
  color: var(--dark);
}

.send-btn:disabled {
  opacity: 0.15;
  cursor: not-allowed;
}

.send-loader {
  width: 12px; height: 12px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #FFF;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
</style>
