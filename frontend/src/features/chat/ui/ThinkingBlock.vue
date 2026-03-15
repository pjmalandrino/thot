<template>
  <div class="thinking-block" :class="[
    { collapsed: isCollapsed, streaming: isStreaming },
    'mode-' + mode
  ]">
    <button class="thinking-header" @click="toggleCollapse">
      <svg class="thinking-icon" :class="{ rotated: !isCollapsed }" width="8" height="8" viewBox="0 0 8 8" fill="currentColor">
        <polygon points="1,0 7,4 1,8" />
      </svg>
      <span class="thinking-title">{{ mode === 'research' ? 'Analyse' : 'Raisonnement' }}</span>
      <span v-if="isStreaming" class="thinking-badge streaming-badge">en cours</span>
      <span v-else class="thinking-badge done-badge">{{ formatTokens(tokenCount) }}</span>
    </button>
    <div v-show="!isCollapsed" class="thinking-content">
      <div class="thinking-text" v-html="renderedContent"></div>
      <span v-if="isStreaming" class="thinking-cursor">&#9608;</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { renderMarkdown } from './markdown.js'

const props = defineProps({
  content: { type: String, default: '' },
  isStreaming: { type: Boolean, default: false },
  startCollapsed: { type: Boolean, default: false },
  mode: { type: String, default: 'think' }
})

const isCollapsed = ref(props.startCollapsed)

// Auto-expand when streaming starts
watch(() => props.isStreaming, (val) => {
  if (val) isCollapsed.value = false
})

const renderedContent = computed(() => renderMarkdown(props.content, []))

const tokenCount = computed(() => {
  if (!props.content) return 0
  return Math.ceil(props.content.length / 4)
})

function formatTokens(count) {
  if (count >= 1000) return (count / 1000).toFixed(1) + 'k tokens'
  return count + ' tokens'
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style scoped>
.thinking-block {
  margin: 0.75rem 0;
  border: 1px solid var(--border);
  background: rgba(0, 0, 0, 0.025);
  animation: fadeInUp 0.3s ease;
}

/* Think mode: gold accent */
.thinking-block.mode-think.streaming {
  border-color: var(--accent);
  border-left: 3px solid var(--accent);
}

/* Research mode: teal accent (matches ThinkingIndicator) */
.thinking-block.mode-research.streaming {
  border-color: var(--accent-pop);
  border-left: 3px solid var(--accent-pop);
}

/* Non-streaming (historical) — subtle left accent */
.thinking-block:not(.streaming).mode-think {
  border-left: 3px solid rgba(139, 92, 246, 0.3);
}

.thinking-block:not(.streaming).mode-research {
  border-left: 3px solid rgba(61, 202, 173, 0.3);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.6rem 1rem;
  background: none;
  border: none;
  cursor: pointer;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--text-mid);
  transition: color 0.2s ease;
}

.thinking-header:hover {
  color: var(--text);
}

.thinking-icon {
  width: 8px;
  height: 8px;
  flex-shrink: 0;
  transition: transform 0.2s ease;
  opacity: 0.6;
}

.thinking-icon.rotated {
  transform: rotate(90deg);
}

.thinking-title {
  font-weight: 600;
}

.mode-research .thinking-title {
  color: var(--accent-pop);
}

.mode-think .thinking-title {
  color: #8B5CF6;
}

.thinking-badge {
  margin-left: auto;
  font-size: 0.55rem;
  padding: 0.1rem 0.5rem;
  border-radius: 50px;
}

.mode-think .streaming-badge {
  background: rgba(139, 92, 246, 0.12);
  color: #8B5CF6;
  animation: pulse 1.5s ease-in-out infinite;
}

.mode-research .streaming-badge {
  background: rgba(61, 202, 173, 0.12);
  color: var(--accent-pop);
  animation: pulse 1.5s ease-in-out infinite;
}

.done-badge {
  background: rgba(0, 0, 0, 0.05);
  color: var(--text-light);
}

.thinking-content {
  padding: 0 1rem 1rem;
  max-height: 400px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
  border-top: 1px solid var(--border);
}

.thinking-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.78rem;
  line-height: 1.7;
  color: var(--text-mid);
  white-space: pre-wrap;
  padding-top: 0.75rem;
}

.thinking-text :deep(p) { margin-bottom: 0.5rem; }
.thinking-text :deep(p:last-child) { margin-bottom: 0; }

.thinking-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: var(--accent);
  font-size: 0.8rem;
}

.mode-research .thinking-cursor {
  color: var(--accent-pop);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
