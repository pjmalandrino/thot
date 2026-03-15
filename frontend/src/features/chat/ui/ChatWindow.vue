<template>
  <div class="chat-layout">
    <div class="chat-header">
      <div class="header-left">
        <span v-if="activeSpaceName" class="header-space">{{ activeSpaceName }}</span>
        <span v-if="activeSpaceName" class="header-sep">/</span>
        <span class="header-label">Session</span>
        <span class="header-id">#{{ conversationId }}</span>
      </div>
      <ModelSelect :disabled="isBusy" />
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
        <div v-if="interactions.length === 0 && !clarification && !stream.streaming.value" class="empty-chat">
          <span class="empty-hero">?</span>
          <span class="empty-text">En attente d'une instruction</span>
        </div>
        <div v-for="(item, idx) in interactions" :key="item.id || idx" class="interaction">
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
              <span v-if="item.mode === 'think'" class="pill pill-think">Think</span>
              <span v-if="item.mode === 'research'" class="pill pill-research">Research</span>
              <span v-if="item.mode === 'lab'" class="pill pill-lab">Lab</span>
              <span v-if="item.sources && item.sources.length" class="pill pill-web">Web</span>
              <span v-if="item.autoWebSearchTriggered" class="pill pill-auto">Auto</span>
              <span class="msg-meta">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <!-- Historical pipeline steps (Research mode) -->
            <ThinkingIndicator
              v-if="item.steps && item.steps.length"
              :visible="true"
              :sse-steps="item.steps"
            />
            <!-- Historical thinking block (collapsed) -->
            <ThinkingBlock
              v-if="item.thinking"
              :content="item.thinking"
              :start-collapsed="true"
              :mode="item.mode || 'think'"
            />
            <div class="msg-text llm-text md-content" v-html="renderMarkdown(item.response, item.sources)"></div>
            <SourcesFooter :sources="item.sources" />
          </div>
        </div>

        <!-- Inline clarification -->
        <div v-if="clarification" class="interaction clarification-interaction">
          <div class="msg msg-user">
            <div class="msg-head">
              <span class="pill pill-user">Vous</span>
            </div>
            <p class="msg-text user-text">{{ clarification.prompt }}</p>
          </div>
          <div class="msg msg-clarification">
            <div class="msg-head">
              <span class="pill pill-llm">Thot</span>
              <span class="pill pill-clarification">Clarification</span>
            </div>
            <p class="msg-text clarification-text">{{ clarification.clarificationMessage }}</p>
            <div v-if="clarification.suggestions && clarification.suggestions.length" class="clarification-suggestions">
              <button
                v-for="(s, i) in clarification.suggestions"
                :key="i"
                class="suggestion-btn"
                @click="useSuggestion(s)"
              >{{ s }}</button>
            </div>
            <button class="skip-btn" @click="skipClarification">Envoyer quand meme</button>
          </div>
        </div>

        <!-- ═══ Live streaming interaction ═══ -->
        <div v-if="stream.streaming.value" class="interaction streaming-interaction">
          <div class="msg msg-user">
            <div class="msg-head">
              <span class="pill pill-user">Vous</span>
            </div>
            <p class="msg-text user-text">{{ streamingPrompt }}</p>
          </div>

          <div class="msg msg-llm">
            <div class="msg-head">
              <span class="pill pill-llm">Thot</span>
              <span v-if="selectedMode === 'think'" class="pill pill-think">Think</span>
              <span v-if="selectedMode === 'research'" class="pill pill-research">Research</span>
              <span v-if="selectedMode === 'lab'" class="pill pill-lab">Lab</span>
            </div>

            <!-- ═══ Lab mode: Perplexity-style accordion ═══ -->
            <template v-if="selectedMode === 'lab'">
              <LabAccordion
                :steps="stream.steps.value"
                :step-contents="stream.stepContents.value"
                :all-sources="stream.sources.value"
              />
            </template>

            <!-- ═══ Research mode: existing pipeline + thinking + answer ═══ -->
            <template v-else-if="selectedMode === 'research'">
              <ThinkingIndicator
                :visible="true"
                :sse-steps="stream.steps.value"
              />
              <ThinkingBlock
                v-if="stream.thinking.value"
                :content="stream.thinking.value"
                :is-streaming="true"
                :start-collapsed="true"
                mode="research"
              />
              <div
                v-if="stream.answer.value"
                class="msg-text llm-text md-content"
                v-html="renderMarkdown(stream.answer.value, stream.sources.value)"
              ></div>
              <SourcesFooter v-if="stream.sources.value.length" :sources="stream.sources.value" />
            </template>

            <!-- ═══ Think mode: thinking + answer ═══ -->
            <template v-else-if="selectedMode === 'think'">
              <ThinkingBlock
                v-if="stream.thinking.value"
                :content="stream.thinking.value"
                :is-streaming="true"
                mode="think"
              />
              <div
                v-if="stream.answer.value"
                class="msg-text llm-text md-content"
                v-html="renderMarkdown(stream.answer.value, stream.sources.value)"
              ></div>
            </template>
          </div>
        </div>
      </template>

      <!-- Standard mode: fake thinking indicator -->
      <ThinkingIndicator :visible="sending && selectedMode === 'standard'" />
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
            :disabled="isBusy"
            title="Retirer"
          >&times;</button>
        </div>
      </div>

      <div class="input-box" :class="{ focused: inputFocused, generating: isBusy }">
        <textarea
          ref="textareaEl"
          v-model="prompt"
          class="input-field"
          placeholder="Posez votre question..."
          :disabled="isBusy"
          rows="1"
          @keydown.enter.exact="sendPrompt"
          @input="autoResize"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
        ></textarea>
        <div class="input-bottom">
          <div class="input-left">
            <!-- Mode toggle -->
            <div class="mode-toggle">
              <button
                v-for="m in modes"
                :key="m.id"
                class="mode-btn"
                :class="{ active: selectedMode === m.id }"
                :data-mode="m.id"
                :disabled="isBusy"
                @click="selectedMode = m.id"
              >{{ m.label }}</button>
            </div>
            <div class="input-hints">
              <kbd>&#9166;</kbd> envoyer
              <span class="dot">&middot;</span>
              <kbd>&#8679;&#9166;</kbd> ligne
            </div>
          </div>
          <div class="input-actions">
            <DocumentAttachment
              :conversation-id="conversationId"
              :disabled="isBusy"
            />
            <button class="send-btn" :disabled="!prompt.trim() || isBusy" @click="sendPrompt">
              <span v-if="isBusy" class="send-loader"></span>
              <span v-else>Envoyer</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { formatDateTime } from '../../../shared/utils/date.js'
import { renderMarkdown } from './markdown.js'
import { useChartRenderer } from './useChartRenderer.js'
import { useStreamingCompletion } from './useStreamingCompletion.js'
import { useModelStore } from '../../llm-model/store.js'
import { useThotspaceStore } from '../../thotspace/store.js'
import { useDocumentStore } from '../../document/store.js'
import { fetchCompletions, sendCompletion } from '../api.js'
import ModelSelect from '../../llm-model/ui/ModelSelect.vue'
import DocumentAttachment from '../../document/ui/DocumentAttachment.vue'
import ThinkingIndicator from './ThinkingIndicator.vue'
import ThinkingBlock from './ThinkingBlock.vue'
import SourcesFooter from './SourcesFooter.vue'
import LabAccordion from './LabAccordion.vue'

const props = defineProps({ conversationId: { type: Number, required: true } })

const modelStore = useModelStore()
const thotspaceStore = useThotspaceStore()
const documentStore = useDocumentStore()
const stream = useStreamingCompletion()
const activeSpaceName = computed(() => thotspaceStore.activeSpace?.name)

const interactions = ref([])
const prompt = ref('')
const loading = ref(true)
const sending = ref(false)
const error = ref('')
const messagesEl = ref(null)
const textareaEl = ref(null)
const inputFocused = ref(false)
const clarification = ref(null)
const pendingPrompt = ref('')
const selectedMode = ref('standard')
const streamingPrompt = ref('')

const modes = [
  { id: 'standard', label: 'Standard' },
  { id: 'think', label: 'Think' },
  { id: 'research', label: 'Research' },
  { id: 'lab', label: 'Lab' }
]

const isBusy = computed(() => sending.value || stream.streaming.value)

// Chart rendering: scans messages for chart placeholders and mounts Plotly.js
useChartRenderer(messagesEl, interactions)

// Auto-scroll during streaming
watch(() => stream.answer.value, () => {
  nextTick(() => scrollToBottom())
})
watch(() => stream.thinking.value, () => {
  nextTick(() => scrollToBottom())
})
watch(() => stream.steps.value.length, () => {
  nextTick(() => scrollToBottom())
})

// Handle stream completion
watch(() => stream.doneData.value, async (data) => {
  if (!data) return

  if (data.clarification) {
    // Stream returned a clarification
    clarification.value = {
      prompt: streamingPrompt.value,
      clarificationMessage: data.message,
      suggestions: data.suggestions
    }
    pendingPrompt.value = streamingPrompt.value
  } else {
    // Push completed interaction to history
    const effectiveSources = (data.sources && data.sources.length)
      ? data.sources
      : (stream.sources.value.length ? [...stream.sources.value] : [])
    interactions.value.push({
      prompt: streamingPrompt.value,
      response: data.response || stream.answer.value,
      thinking: data.thinking || stream.thinking.value || null,
      sources: effectiveSources,
      mode: selectedMode.value,
      steps: (selectedMode.value === 'research' || selectedMode.value === 'lab')
        ? stream.steps.value.map(s => s.status === 'running' ? { ...s, status: 'done' } : s)
        : null,
      autoWebSearchTriggered: data.autoWebSearchTriggered || false,
      createdAt: new Date().toISOString()
    })
    prompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'
  }

  streamingPrompt.value = ''
  stream.reset()
  await nextTick()
  scrollToBottom()
})

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
  if (!prompt.value.trim() || isBusy.value) return

  const currentPrompt = prompt.value.trim()
  clarification.value = null
  error.value = ''

  if (selectedMode.value === 'standard') {
    // Standard mode: existing synchronous flow
    sending.value = true
    await nextTick()
    scrollToBottom()

    try {
      const result = await sendCompletion(props.conversationId, {
        prompt: currentPrompt,
        modelId: modelStore.selectedModelId
      })

      if (result.status === 'clarification_needed') {
        clarification.value = result
        pendingPrompt.value = currentPrompt
      } else {
        interactions.value.push(result)
        prompt.value = ''
        pendingPrompt.value = ''
        if (textareaEl.value) textareaEl.value.style.height = 'auto'
      }

      await nextTick()
      scrollToBottom()
    } catch (err) {
      error.value = err.message
    } finally {
      sending.value = false
    }
  } else {
    // Think or Research mode: SSE streaming
    streamingPrompt.value = currentPrompt
    prompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'

    await nextTick()
    scrollToBottom()

    stream.streamCompletion(props.conversationId, {
      prompt: currentPrompt,
      modelId: modelStore.selectedModelId,
      mode: selectedMode.value
    })
  }
}

async function useSuggestion(suggestion) {
  const original = pendingPrompt.value
  clarification.value = null
  pendingPrompt.value = ''
  sending.value = true
  error.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const result = await sendCompletion(props.conversationId, {
      prompt: original,
      modelId: modelStore.selectedModelId,
      clarificationContext: suggestion
    })

    interactions.value.push(result)
    prompt.value = ''
    if (textareaEl.value) textareaEl.value.style.height = 'auto'

    await nextTick()
    scrollToBottom()
  } catch (err) {
    error.value = err.message
  } finally {
    sending.value = false
  }
}

function skipClarification() {
  const text = pendingPrompt.value
  clarification.value = null
  pendingPrompt.value = ''
  prompt.value = text
  nextTick(() => sendPrompt())
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

.streaming-interaction {
  border-left: 3px solid var(--accent);
  padding-left: 1.25rem;
  background: rgba(212, 164, 56, 0.02);
}

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

.pill-auto {
  background: none;
  border: 1px solid var(--accent-pop, #3DCAAD);
  color: var(--accent-pop, #3DCAAD);
  font-size: 0.55rem;
  padding: 0.2rem 0.6rem;
}

.pill-think {
  background: none;
  border: 1px solid #8B5CF6;
  color: #8B5CF6;
  font-size: 0.55rem;
  padding: 0.2rem 0.6rem;
}

.pill-research {
  background: none;
  border: 1px solid var(--accent-pop, #3DCAAD);
  color: var(--accent-pop, #3DCAAD);
  font-size: 0.55rem;
  padding: 0.2rem 0.6rem;
}

.pill-lab {
  background: none;
  border: 1px solid #F59E0B;
  color: #F59E0B;
  font-size: 0.55rem;
  padding: 0.2rem 0.6rem;
}

.pill-clarification {
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
   CITATION BADGES (inline in markdown)
   ══════════════════════════════════════ */
.md-content :deep(.citation-inline) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--accent);
  background: rgba(212, 164, 56, 0.08);
  padding: 0.05rem 0.3rem;
  text-decoration: none;
  vertical-align: super;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}

.md-content :deep(.citation-inline:hover) {
  background: rgba(212, 164, 56, 0.18);
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

.md-content :deep(a:not(.citation-inline)) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color 0.2s ease;
}

.md-content :deep(a:not(.citation-inline):hover) { color: var(--accent-light); }

.md-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1.5rem 0;
}

/* ══════════════════════════════════════
   TABLE
   ══════════════════════════════════════ */
.md-content :deep(table) {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin: 1.5rem 0;
  font-size: 0.82rem;
  line-height: 1.5;
  display: block;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border: 1px solid var(--border);
}

.md-content :deep(thead) {
  background: var(--dark);
}

.md-content :deep(th) {
  padding: 0.7rem 1.1rem;
  text-align: left;
  font-family: 'IBM Plex Mono', monospace;
  font-weight: 500;
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--accent);
  border-bottom: 2px solid var(--accent);
  white-space: nowrap;
  position: sticky;
  top: 0;
}

.md-content :deep(th:not(:last-child)) {
  border-right: 1px solid var(--dark-border);
}

.md-content :deep(td) {
  padding: 0.6rem 1.1rem;
  border-bottom: 1px solid var(--border);
  color: var(--text);
  font-variant-numeric: tabular-nums;
  vertical-align: top;
}

.md-content :deep(td:not(:last-child)) {
  border-right: 1px solid rgba(0, 0, 0, 0.04);
}

.md-content :deep(tbody tr:nth-child(even)) {
  background: rgba(0, 0, 0, 0.018);
}

.md-content :deep(tbody tr:hover) {
  background: rgba(212, 164, 56, 0.06);
}

.md-content :deep(tbody tr:first-child td) {
  padding-top: 0.75rem;
}

.md-content :deep(tbody td:first-child) {
  font-family: 'IBM Plex Mono', monospace;
  font-weight: 500;
  font-size: 0.78rem;
  color: var(--text-mid);
}

.md-content :deep(.table-error-msg) {
  padding: 0.75rem 1rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: #D44A3A;
  border-left: 3px solid #D44A3A;
  margin: 1rem 0;
}

/* ══════════════════════════════════════
   CHART
   ══════════════════════════════════════ */
.md-content :deep(.chart-placeholder) {
  margin: 1.25rem 0;
  min-height: 300px;
  border: 1px solid var(--border);
  background: #FFF;
}

.md-content :deep(.chart-loading) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 300px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--text-light);
  letter-spacing: 0.04em;
}

.md-content :deep(.chart-error-msg) {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: #D44A3A;
  padding: 1rem;
  text-align: center;
}

/* ══════════════════════════════════════
   CLARIFICATION (inline in chat)
   ══════════════════════════════════════ */
.clarification-interaction {
  border-left: 3px solid var(--accent);
  padding-left: 1.25rem;
}

.clarification-text {
  color: var(--text-mid);
  line-height: 1.6;
}

.clarification-suggestions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-top: 1rem;
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
  padding: 0.5rem 0 0;
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

.input-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

/* ══════════════════════════════════════
   MODE TOGGLE
   ══════════════════════════════════════ */
.mode-toggle {
  display: flex;
  gap: 0;
  border: 1px solid var(--border);
}

.mode-btn {
  padding: 0.3rem 0.75rem;
  background: none;
  border: none;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-light);
  cursor: pointer;
  transition: all 0.25s ease;
}

.mode-btn:not(:last-child) {
  border-right: 1px solid var(--border);
}

.mode-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

/* Standard mode active */
.mode-btn.active {
  background: var(--dark);
  color: var(--bg);
}

/* Think mode active — violet */
.mode-btn.active[data-mode="think"] {
  background: #8B5CF6;
  color: #FFF;
}

/* Research mode active — teal */
.mode-btn.active[data-mode="research"] {
  background: var(--accent-pop);
  color: var(--dark);
  font-weight: 600;
}

/* Lab mode active — amber */
.mode-btn.active[data-mode="lab"] {
  background: #F59E0B;
  color: #FFF;
  font-weight: 600;
}

.mode-btn:hover:not(.active):not(:disabled) {
  color: var(--text);
  background: rgba(0, 0, 0, 0.03);
}

.mode-btn[data-mode="think"]:hover:not(.active):not(:disabled) {
  color: #8B5CF6;
}

.mode-btn[data-mode="research"]:hover:not(.active):not(:disabled) {
  color: var(--accent-pop);
}

.mode-btn[data-mode="lab"]:hover:not(.active):not(:disabled) {
  color: #F59E0B;
}

.mode-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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
  opacity: 0.35;
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
