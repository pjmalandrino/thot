<template>
  <span class="citation-badge" @mouseenter="showTooltip = true" @mouseleave="showTooltip = false">
    <a :href="source.sourceUrl" target="_blank" rel="noopener" class="citation-link">{{ source.citationId }}</a>
    <div v-if="showTooltip" class="citation-tooltip">
      <div class="tooltip-title">{{ source.sourceTitle || 'Sans titre' }}</div>
      <div v-if="source.extractedText" class="tooltip-text">{{ truncateText(source.extractedText) }}</div>
      <div class="tooltip-url">{{ formatUrl(source.sourceUrl) }}</div>
    </div>
  </span>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  source: { type: Object, required: true }
})

const showTooltip = ref(false)

function formatUrl(url) {
  if (!url) return ''
  try {
    return new URL(url).hostname.replace('www.', '')
  } catch {
    return url
  }
}

function truncateText(text) {
  if (!text) return ''
  return text.length > 150 ? text.substring(0, 150) + '...' : text
}
</script>

<style scoped>
.citation-badge {
  position: relative;
  display: inline;
}

.citation-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--accent, #D4A438);
  background: rgba(212, 164, 56, 0.08);
  padding: 0.1rem 0.35rem;
  text-decoration: none;
  vertical-align: super;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
}

.citation-link:hover {
  background: rgba(212, 164, 56, 0.18);
  color: var(--text, #1A1A1A);
}

.citation-tooltip {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  width: 280px;
  padding: 0.75rem;
  background: var(--dark, #1A1A18);
  color: #E0DCD4;
  border: 1px solid var(--dark-border, #2A2A28);
  z-index: 100;
  animation: fadeIn 0.15s ease;
}

.tooltip-title {
  font-size: 0.72rem;
  font-weight: 600;
  color: #FFF;
  margin-bottom: 0.35rem;
  line-height: 1.3;
}

.tooltip-text {
  font-size: 0.65rem;
  color: #B0ACA4;
  line-height: 1.5;
  margin-bottom: 0.35rem;
}

.tooltip-url {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--accent, #D4A438);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateX(-50%) translateY(4px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
</style>
