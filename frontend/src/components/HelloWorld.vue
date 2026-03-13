<template>
  <div class="chat-layout">
    <div class="chat-header">
      <div class="header-left">
        <span class="header-label">Session</span>
        <span class="header-id">#{{ conversationId }}</span>
      </div>
      <select v-model="selectedModelId" class="model-select" :disabled="sending">
        <option v-for="m in models" :key="m.id" :value="m.id">{{ m.displayLabel }}</option>
        <option v-if="models.length === 0" disabled value="">Chargement…</option>
      </select>
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

          <!-- Sources panel (Perplexity-style) -->
          <div v-if="item.sources && item.sources.length" class="sources-panel">
            <div class="sources-header">
              <span class="sources-icon">&#9906;</span>
              <span class="sources-label">Sources</span>
              <span class="sources-count">{{ item.sources.length }}</span>
            </div>
            <div class="sources-list">
              <a
                v-for="src in item.sources"
                :key="src.citationId"
                :href="src.sourceUrl"
                target="_blank"
                rel="noopener"
                class="source-card"
              >
                <span class="source-citation">{{ src.citationId }}</span>
                <span class="source-title">{{ src.sourceTitle || 'Sans titre' }}</span>
                <span class="source-url">{{ formatUrl(src.sourceUrl) }}</span>
              </a>
            </div>
          </div>

          <div class="msg msg-llm">
            <div class="msg-head">
              <span class="pill pill-llm">Thot</span>
              <span v-if="item.sources" class="pill pill-web">Web</span>
              <span class="msg-meta">{{ formatDate(item.createdAt) }}</span>
            </div>
            <div class="msg-text llm-text md-content" v-html="renderMarkdown(item.response)"></div>
          </div>
        </div>
      </template>

      <!-- Searching indicator -->
      <div v-if="searchStatus" class="search-status">
        <span class="search-dot"></span>
        <span>{{ searchStatus }}</span>
      </div>
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
          <div class="input-actions">
            <button
              class="web-toggle"
              :class="{ active: webSearch }"
              @click="webSearch = !webSearch"
              type="button"
              title="Recherche web"
            >
              <span class="web-icon">&#9906;</span>
              Web
            </button>
            <button class="send-btn" :disabled="!prompt.trim() || sending" @click="sendPrompt">
              <span v-if="sending" class="send-loader"></span>
              <span v-else>Envoyer</span>
            </button>
          </div>
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
const webSearch = ref(false)
const searchStatus = ref('')
const models = ref([])
const selectedModelId = ref(null)

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text)
}

function formatUrl(url) {
  if (!url) return ''
  try {
    const u = new URL(url)
    return u.hostname.replace('www.', '')
  } catch {
    return url
  }
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

async function fetchModels() {
  try {
    const data = await apiFetch('/api/llm/models')
    models.value = data
    if (data.length > 0) selectedModelId.value = data[0].id
  } catch (e) {
    console.error('Failed to load models', e)
  }
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
  searchStatus.value = webSearch.value ? 'Recherche web en cours...' : ''

  try {
    const body = {
      prompt: prompt.value.trim(),
      webSearch: webSearch.value,
      modelId: selectedModelId.value
    }

    if (webSearch.value) {
      searchStatus.value = 'Recherche web en cours...'
      await nextTick()
      scrollToBottom()
    }

    const result = await apiFetch(`/api/conversations/${props.conversationId}/completions`, {
      method: 'POST',
      body: JSON.stringify(body)
    })

    searchStatus.value = ''
    interactions.value.push(result)
    prompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'
    await nextTick()
    scrollToBottom()
  } catch (e) {
    error.value = e.message
    searchStatus.value = ''
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

onMounted(() => {
  fetchModels()
  fetchInteractions()
})
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

.model-select {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  color: var(--text-mid);
  border: 1px solid var(--border);
  padding: 0.25rem 0.75rem;
  border-radius: 50px;
  letter-spacing: 0.02em;
  background: transparent;
  outline: none;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  padding-right: 1.5rem;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%239C9688'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.5rem center;
  transition: border-color 0.2s ease;
}

.model-select:hover:not(:disabled) {
  border-color: var(--text-mid);
}

.model-select:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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
   SEARCH STATUS
   ══════════════════════════════════════ */
.search-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1rem 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--accent);
  letter-spacing: 0.04em;
  animation: fadeInUp 0.3s ease;
}

.search-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ══════════════════════════════════════
   INTERACTION
   ══════════════════════════════════════ */
.interaction {
  padding-bottom: 2.5rem;
  margin-bottom: 2.5rem;
  border-bottom: 1px solid var(--border);
  animation: fadeInUp 0.4s ease;
}

.interaction:last-child { border-bottom: none; margin-bottom: 0; }

.msg { padding: 1.25rem 0; }

.msg-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

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

.pill-web {
  background: none;
  border: 1px solid var(--accent);
  color: var(--accent);
  font-size: 0.55rem;
  padding: 0.2rem 0.6rem;
}

.msg-meta {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-light);
}

.msg-text {
  font-size: 0.95rem;
  line-height: 1.8;
  color: var(--text);
}

.user-text {
  white-space: pre-wrap;
  color: var(--text-mid);
}

/* ══════════════════════════════════════
   SOURCES PANEL (Perplexity-style)
   ══════════════════════════════════════ */
.sources-panel {
  padding: 1.25rem 0 0.5rem;
  animation: fadeInUp 0.3s ease;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.sources-icon {
  font-size: 0.85rem;
  color: var(--accent);
}

.sources-label {
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: var(--text-mid);
}

.sources-count {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
  background: var(--border);
  padding: 0.1rem 0.45rem;
  border-radius: 50px;
}

.sources-list {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.25rem;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

.source-card {
  flex-shrink: 0;
  width: 180px;
  padding: 0.75rem;
  border: 1px solid var(--border);
  background: #FFF;
  text-decoration: none;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  transition: all 0.2s ease;
}

.source-card:hover {
  border-color: var(--accent);
  background: rgba(212, 164, 56, 0.03);
}

.source-citation {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--accent);
}

.source-title {
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--text);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.source-url {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

.md-content :deep(a:hover) { color: var(--accent-light); }

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

.input-box.focused { border-color: var(--text); }
.input-box.generating { border-color: var(--accent); }

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

.input-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* ── Web Search Toggle ── */
.web-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.75rem;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all 0.2s ease;
}

.web-toggle:hover {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.web-toggle.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(212, 164, 56, 0.06);
}

.web-icon { font-size: 0.75rem; }

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
