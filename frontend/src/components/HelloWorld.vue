<template>
  <div class="chat-layout">
    <div class="chat-header">
      <div class="header-left">
        <span class="header-line">&mdash;</span>
        <span class="header-title">Session</span>
        <span class="header-sep">/</span>
        <span class="header-id">#{{ conversationId }}</span>
      </div>
      <span class="header-model">llama3.2:3b</span>
    </div>

    <div class="messages" ref="messagesEl">
      <div v-if="loading" class="state-msg">
        <span class="state-icon">...</span>
        <span>chargement</span>
      </div>
      <div v-else-if="error" class="state-msg state-error">
        <span class="state-icon">!</span>
        <span>{{ error }}</span>
      </div>
      <template v-else>
        <div v-if="interactions.length === 0" class="empty-chat">
          <div class="empty-terminal">
            <span class="term-line">$ thot --init</span>
            <span class="term-line dim">En attente d'une instruction...</span>
            <span class="term-cursor">_</span>
          </div>
        </div>
        <div v-for="(item, idx) in interactions" :key="item.id" class="interaction">
          <div class="msg msg-user">
            <div class="msg-meta">
              <span class="msg-tag">VOUS</span>
              <span class="msg-idx">#{{ idx + 1 }}</span>
            </div>
            <p class="msg-content">{{ item.prompt }}</p>
          </div>
          <div class="msg msg-llm">
            <div class="msg-meta">
              <span class="msg-tag llm-tag">THOT</span>
              <span class="msg-time">{{ formatDate(item.createdAt) }}</span>
            </div>
            <div class="msg-content md-content" v-html="renderMarkdown(item.response)"></div>
          </div>
        </div>
      </template>
    </div>

    <div class="input-area">
      <div class="input-console" :class="{ focused: inputFocused, disabled: sending }">
        <div class="console-top">
          <span class="console-prompt">&gt;_</span>
          <div v-if="sending" class="console-status">
            <span class="status-dot"></span>
            <span class="status-text">traitement</span>
          </div>
          <span v-else class="console-label">instruction</span>
        </div>
        <textarea
          ref="textareaEl"
          v-model="prompt"
          class="console-input"
          placeholder="Posez votre question..."
          :disabled="sending"
          rows="1"
          @keydown.enter.exact="sendPrompt"
          @input="autoResize"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
        ></textarea>
        <div class="console-bottom">
          <div class="console-keys">
            <kbd>&#9166;</kbd> <span>envoyer</span>
            <span class="key-sep">&middot;</span>
            <kbd>&#8679;&#9166;</kbd> <span>nouvelle ligne</span>
          </div>
          <button class="exec-btn" :disabled="!prompt.trim() || sending" @click="sendPrompt">
            EXEC
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
  background: var(--bg-main);
}

/* ── Header ── */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 2rem;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-family: 'IBM Plex Mono', monospace;
}

.header-line { color: var(--border); font-size: 0.8rem; }
.header-title { font-size: 0.7rem; font-weight: 600; color: var(--text-primary); text-transform: uppercase; letter-spacing: 0.1em; }
.header-sep { color: var(--border); font-size: 0.65rem; }
.header-id { font-size: 0.65rem; color: var(--text-muted); }
.header-model { font-family: 'IBM Plex Mono', monospace; font-size: 0.6rem; color: var(--copper); letter-spacing: 0.06em; opacity: 0.7; }

/* ── Messages ── */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 0;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

/* ── States ── */
.state-msg {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  color: var(--text-muted);
  letter-spacing: 0.06em;
  margin-top: 4rem;
}

.state-icon { font-weight: 700; font-size: 0.8rem; }
.state-error { color: var(--cto-terra); }
.state-error .state-icon { color: var(--cto-terra); }

/* ── Empty ── */
.empty-chat {
  display: flex;
  justify-content: center;
  margin-top: 6rem;
}

.empty-terminal {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.7rem;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.term-line.dim { opacity: 0.5; }
.term-cursor { animation: blink 1s step-end infinite; color: var(--copper); }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

/* ── Interaction ── */
.interaction {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-bottom: 1.5rem;
  margin-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-light);
}

.interaction:last-child { border-bottom: none; margin-bottom: 0; }

/* ── Message ── */
.msg {
  padding: 1rem 0;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 0.6rem;
}

.msg-tag {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.15em;
  color: var(--text-muted);
  background: var(--bg-card);
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
}

.llm-tag {
  color: var(--copper);
  background: rgba(184, 135, 90, 0.08);
}

.msg-idx {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  color: var(--text-muted);
  opacity: 0.5;
}

.msg-time {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  color: var(--text-muted);
  opacity: 0.5;
}

.msg-content {
  font-size: 0.88rem;
  color: var(--text-secondary);
  line-height: 1.7;
  font-weight: 300;
}

.msg-user .msg-content {
  white-space: pre-wrap;
  padding-left: 0.75rem;
  border-left: 2px solid var(--border-light);
}

.msg-llm .msg-content {
  padding-left: 0.75rem;
  border-left: 2px solid var(--copper);
}

/* ── Markdown ── */
.md-content :deep(p) { margin-bottom: 0.75rem; }
.md-content :deep(p:last-child) { margin-bottom: 0; }
.md-content :deep(strong) { font-weight: 600; color: var(--text-primary); }
.md-content :deep(em) { font-style: italic; }
.md-content :deep(code) {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.8rem;
  background: var(--bg-card);
  padding: 0.1rem 0.35rem;
  border-radius: 3px;
  color: var(--copper-dark);
}
.md-content :deep(pre) {
  background: #1E1610;
  color: #E8E0D4;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0.75rem 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.78rem;
  line-height: 1.5;
}
.md-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}
.md-content :deep(ul), .md-content :deep(ol) {
  padding-left: 1.25rem;
  margin-bottom: 0.75rem;
}
.md-content :deep(li) { margin-bottom: 0.25rem; }
.md-content :deep(h1), .md-content :deep(h2), .md-content :deep(h3) {
  font-weight: 600;
  color: var(--text-primary);
  margin: 1rem 0 0.5rem 0;
}
.md-content :deep(h1) { font-size: 1.1rem; }
.md-content :deep(h2) { font-size: 1rem; }
.md-content :deep(h3) { font-size: 0.92rem; }
.md-content :deep(blockquote) {
  border-left: 2px solid var(--amb-gold);
  padding-left: 0.75rem;
  color: var(--text-muted);
  margin: 0.5rem 0;
}

/* ── Input Area ── */
.input-area {
  flex-shrink: 0;
  padding: 0 2rem 1.5rem;
}

.input-console {
  background: var(--bg-input);
  border: 1px solid var(--border);
  border-radius: 6px;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.03);
}

.input-console.focused {
  border-color: var(--copper);
  box-shadow: 0 2px 12px rgba(160, 120, 80, 0.1);
}

.input-console.disabled {
  opacity: 0.7;
}

/* ── Console Top Bar ── */
.console-top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.85rem 0;
}

.console-prompt {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--copper);
}

.console-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.52rem;
  color: var(--text-muted);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  opacity: 0.5;
}

.console-status {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.status-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--copper);
  animation: statusPulse 1s ease-in-out infinite;
}

@keyframes statusPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); }
}

.status-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--copper);
  letter-spacing: 0.08em;
  animation: statusFade 1.5s ease-in-out infinite;
}

@keyframes statusFade {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* ── Textarea ── */
.console-input {
  display: block;
  width: 100%;
  padding: 0.5rem 0.85rem 0.4rem;
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-family: 'Inter', sans-serif;
  font-size: 0.88rem;
  font-weight: 400;
  line-height: 1.55;
  outline: none;
  resize: none;
  min-height: 2.2rem;
  max-height: 160px;
}

.console-input::placeholder {
  color: var(--text-muted);
  font-weight: 300;
}

/* ── Console Bottom Bar ── */
.console-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.3rem 0.5rem 0.45rem 0.85rem;
  border-top: 1px dashed var(--border-light);
}

.console-keys {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  color: var(--text-muted);
  letter-spacing: 0.02em;
  opacity: 0.5;
}

.console-keys kbd {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  background: var(--bg-card);
  padding: 0.08rem 0.3rem;
  border-radius: 2px;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  font-weight: 500;
}

.key-sep {
  margin: 0 0.15rem;
  opacity: 0.3;
}

.exec-btn {
  padding: 0.3rem 1rem;
  background: transparent;
  border: 1px solid var(--copper);
  border-radius: 3px;
  color: var(--copper);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.15em;
  cursor: pointer;
  transition: all 0.15s;
}

.exec-btn:hover:not(:disabled) {
  background: var(--copper);
  color: #FFF;
}

.exec-btn:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}
</style>
