<template>
  <div class="chat-layout">
    <div class="chat-header">
      <div class="header-left">
        <span v-if="activeSpaceName" class="header-space">{{ activeSpaceName }}</span>
        <span v-if="activeSpaceName" class="header-sep">/</span>
        <span class="header-label">Session</span>
        <span class="header-id">#{{ conversationId }}</span>
      </div>
      <ModelSelect :disabled="sending" />
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
              <span class="msg-meta">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div class="msg-text llm-text md-content" v-html="renderMarkdown(item.response)"></div>
          </div>
        </div>
      </template>

      <!-- Searching / analyzing indicator -->
      <div v-if="searchStatus" class="search-status">
        <span class="search-dot"></span>
        <span>{{ searchStatus }}</span>
      </div>
    </div>

    <!-- Clarification card -->
    <div v-if="clarification" class="clarification-card">
      <div class="clarification-header">
        <span class="clarification-icon">&#9888;</span>
        <span class="clarification-title">Clarification suggérée</span>
        <span v-if="clarification.confidence" class="clarification-confidence">{{ Math.round(clarification.confidence * 100) }}%</span>
        <button class="clarification-dismiss" @click="dismissClarification" title="Fermer">&times;</button>
      </div>
      <p class="clarification-reason">{{ clarification.message }}</p>
      <div v-if="clarification.suggestions && clarification.suggestions.length" class="clarification-suggestions">
        <button
          v-for="(s, i) in clarification.suggestions"
          :key="i"
          class="suggestion-btn"
          @click="useSuggestion(s)"
        >{{ s }}</button>
      </div>
      <button class="skip-btn" @click="skipClarification">Répondre quand même</button>
    </div>

    <div class="input-area">
      <!-- Document chips -->
      <div v-if="documentStore.documents.length" class="doc-chips-bar">
        <div v-for="doc in documentStore.documents" :key="doc.id" class="doc-chip">
          <svg class="doc-chip-icon" viewBox="0 0 14 16" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">
            <path d="M8.5 1H3a1.5 1.5 0 00-1.5 1.5v11A1.5 1.5 0 003 15h8a1.5 1.5 0 001.5-1.5V5L8.5 1z"/>
            <path d="M8.5 1v4H12.5"/>
          </svg>
          <span class="doc-chip-name">{{ doc.filename }}</span>
          <span class="doc-chip-meta">{{ formatCharCount(doc.charCount) }}</span>
          <button
            class="doc-chip-remove"
            @click="documentStore.remove(conversationId, doc.id)"
            :disabled="sending"
            title="Retirer"
          >&times;</button>
        </div>
      </div>

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
              class="action-toggle"
              :class="{ active: contextEngineering }"
              @click="contextEngineering = !contextEngineering"
              type="button"
              title="Analyse contextuelle"
            >
              Contexte
            </button>
            <button
              class="action-toggle"
              :class="{ active: webSearch }"
              @click="webSearch = !webSearch"
              type="button"
              title="Recherche web"
            >
              Web
            </button>
            <DocumentAttachment
              :conversation-id="conversationId"
              :disabled="sending"
            />
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
import { ref, computed, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import { formatDateTime } from '../../../shared/utils/date.js'
import { useModelStore } from '../../llm-model/store.js'
import { useThotspaceStore } from '../../thotspace/store.js'
import { useDocumentStore } from '../../document/store.js'
import { fetchCompletions, sendCompletion } from '../api.js'
import { analyzeContext } from '../../context/api.js'
import ModelSelect from '../../llm-model/ui/ModelSelect.vue'
import DocumentAttachment from '../../document/ui/DocumentAttachment.vue'

marked.setOptions({ breaks: true, gfm: true })

const props = defineProps({ conversationId: { type: Number, required: true } })

const modelStore = useModelStore()
const thotspaceStore = useThotspaceStore()
const documentStore = useDocumentStore()
const activeSpaceName = computed(() => thotspaceStore.activeSpace?.name)

const interactions = ref([])
const prompt = ref('')
const loading = ref(true)
const sending = ref(false)
const error = ref('')
const messagesEl = ref(null)
const textareaEl = ref(null)
const inputFocused = ref(false)
const webSearch = ref(false)
const contextEngineering = ref(false)
const clarification = ref(null)
const pendingPrompt = ref('')
const searchStatus = ref('')

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

function formatCharCount(charCount) {
  if (charCount < 1000) return charCount + ' car.'
  return Math.round(charCount / 1000) + 'k car.'
}

async function loadInteractions() {
  try {
    error.value = ''
    interactions.value = await fetchCompletions(props.conversationId)
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

  const currentPrompt = prompt.value.trim()
  clarification.value = null

  // Step 1 : Analyse contextuelle (si activee)
  if (contextEngineering.value) {
    sending.value = true
    error.value = ''
    searchStatus.value = 'Analyse contextuelle...'
    await nextTick()
    scrollToBottom()

    try {
      const analysis = await analyzeContext(
        currentPrompt,
        ['vagueness']
      )

      if (analysis.status !== 'continue') {
        // Interruption : afficher la carte de clarification
        clarification.value = analysis
        pendingPrompt.value = currentPrompt
        searchStatus.value = ''
        sending.value = false
        return
      }
    } catch (err) {
      // Fail-open cote frontend : si l'analyse echoue, on continue
      console.warn('[CONTEXT] Analysis failed, continuing:', err.message)
    }

    searchStatus.value = ''
  }

  // Step 2 : Completion (inchange)
  await doCompletion(currentPrompt)
}

async function doCompletion(text) {
  sending.value = true
  error.value = ''

  try {
    if (webSearch.value) {
      searchStatus.value = 'Recherche web en cours...'
      await nextTick()
      scrollToBottom()
    }

    const result = await sendCompletion(props.conversationId, {
      prompt: text,
      webSearch: webSearch.value,
      modelId: modelStore.selectedModelId
    })

    searchStatus.value = ''
    interactions.value.push(result)
    prompt.value = ''
    pendingPrompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'
    await nextTick()
    scrollToBottom()
  } catch (err) {
    error.value = err.message
    searchStatus.value = ''
  } finally {
    sending.value = false
  }
}

function useSuggestion(suggestion) {
  const original = pendingPrompt.value
  clarification.value = null
  // Conserve la question d'origine + la precision choisie
  prompt.value = original + '\n\nPrécision : ' + suggestion
  pendingPrompt.value = ''
  if (textareaEl.value) {
    textareaEl.value.focus()
    nextTick(autoResize)
  }
}

function skipClarification() {
  const text = pendingPrompt.value
  clarification.value = null
  pendingPrompt.value = ''
  doCompletion(text)
}

function dismissClarification() {
  clarification.value = null
  pendingPrompt.value = ''
}

function scrollToBottom() {
  if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
}

onMounted(() => {
  loadInteractions()
  documentStore.load(props.conversationId)
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

.header-space {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--accent);
}

.header-sep {
  font-size: 0.75rem;
  color: var(--text-light);
  margin: 0 0.15rem;
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
   CLARIFICATION CARD
   ══════════════════════════════════════ */
.clarification-card {
  flex-shrink: 0;
  margin: 0 3rem;
  padding: 1.25rem 1.5rem;
  border: 1px solid var(--accent);
  background: rgba(212, 164, 56, 0.04);
  animation: fadeInUp 0.3s ease;
}

.clarification-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.clarification-icon {
  font-size: 0.9rem;
  color: var(--accent);
}

.clarification-title {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--accent);
  flex: 1;
}

.clarification-confidence {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
  background: var(--border);
  padding: 0.1rem 0.45rem;
  border-radius: 50px;
  margin-left: auto;
}

.clarification-dismiss {
  background: none;
  border: none;
  color: var(--text-light);
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0 0.25rem;
  line-height: 1;
  transition: color 0.2s ease;
}

.clarification-dismiss:hover { color: var(--text); }

.clarification-reason {
  font-size: 0.85rem;
  color: var(--text-mid);
  line-height: 1.6;
  margin-bottom: 1rem;
}

.clarification-suggestions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-bottom: 1rem;
}

.suggestion-btn {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.65rem 1rem;
  background: #FFF;
  border: 1px solid var(--border);
  color: var(--text);
  font-family: 'Inter', sans-serif;
  font-size: 0.82rem;
  line-height: 1.4;
  cursor: pointer;
  transition: all 0.2s ease;
}

.suggestion-btn:hover {
  border-color: var(--accent);
  background: rgba(212, 164, 56, 0.03);
  color: var(--accent);
}

.skip-btn {
  background: none;
  border: none;
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  letter-spacing: 0.04em;
  cursor: pointer;
  padding: 0.35rem 0;
  transition: color 0.2s ease;
}

.skip-btn:hover { color: var(--text); text-decoration: underline; }

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

/* ── Action Toggles ── */
.action-toggle {
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

.action-toggle:hover {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.action-toggle.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(212, 164, 56, 0.06);
}

.action-toggle:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

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

/* ══════════════════════════════════════
   DOCUMENT CHIPS
   ══════════════════════════════════════ */
.doc-chips-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
  animation: fadeInUp 0.3s ease;
}

.doc-chip {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.6rem;
  background: #FFF;
  border: 1px solid var(--border);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-mid);
  transition: all 0.2s ease;
}

.doc-chip-icon {
  width: 0.7rem;
  height: 0.7rem;
  flex-shrink: 0;
  color: var(--text-light);
}

.doc-chip-name {
  max-width: 160px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--text);
  font-weight: 500;
}

.doc-chip-meta {
  color: var(--text-light);
  font-size: 0.55rem;
}

.doc-chip-remove {
  background: none;
  border: none;
  color: var(--text-light);
  font-size: 0.85rem;
  line-height: 1;
  cursor: pointer;
  padding: 0 0.15rem;
  transition: color 0.2s ease;
}

.doc-chip-remove:hover { color: #E85D4A; }
.doc-chip-remove:disabled { opacity: 0.3; cursor: not-allowed; }
</style>
