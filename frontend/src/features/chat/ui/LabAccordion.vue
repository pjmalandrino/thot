<template>
  <div class="lab-accordion">
    <div
      v-for="step in steps"
      :key="step.stepId"
      class="lab-step"
      :class="{
        'is-active': step.status === 'running',
        'is-done': step.status === 'done',
        'is-pending': !step.status || step.status === 'pending'
      }"
    >
      <!-- Step header (always visible) -->
      <button class="lab-step-header" @click="toggle(step.stepId)">
        <!-- Status icon -->
        <span class="step-icon" v-if="step.status === 'done'">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="3 7 6 10 11 4" />
          </svg>
        </span>
        <span class="step-icon step-icon-pulse" v-else-if="step.status === 'running'"></span>
        <span class="step-icon step-icon-blank" v-else></span>

        <!-- Label -->
        <span class="step-label">{{ stepLabel(step) }}</span>

        <!-- Detail badge -->
        <span v-if="stepDetail(step)" class="step-detail">{{ stepDetail(step) }}</span>

        <!-- Chevron -->
        <svg
          class="step-chevron"
          :class="{ open: isExpanded(step.stepId) }"
          width="8" height="8" viewBox="0 0 8 8" fill="currentColor"
        >
          <polygon points="1,0 7,4 1,8" />
        </svg>
      </button>

      <!-- Step body (collapsible) — always show for running steps -->
      <div v-if="isExpanded(step.stepId)" class="lab-step-body">

        <!-- Source chips (for research sub-queries) -->
        <div v-if="content(step.stepId).sources.length" class="step-sources">
          <a
            v-for="src in content(step.stepId).sources"
            :key="src.citationId"
            :href="src.sourceUrl"
            target="_blank"
            rel="noopener"
            class="source-chip"
          >
            <img :src="faviconUrl(src.sourceUrl)" class="source-chip-icon" alt="" />
            <span class="source-chip-id">{{ src.citationId }}</span>
            <span class="source-chip-title">{{ src.sourceTitle }}</span>
          </a>
        </div>

        <!-- Answer content (for writing sections) -->
        <div
          v-if="content(step.stepId).answer"
          class="step-answer md-content"
          v-html="renderMarkdown(content(step.stepId).answer, allSources)"
        ></div>

        <!-- Loading indicator when running but no content yet -->
        <div v-if="step.status === 'running' && !hasContent(step.stepId)" class="step-loading">
          <span class="step-loading-dot"></span>
          <span class="step-loading-text">En cours...</span>
        </div>

        <!-- Streaming cursor -->
        <span v-if="step.status === 'running' && hasContent(step.stepId)" class="step-cursor">&#9608;</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { renderMarkdown } from './markdown.js'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  stepContents: { type: Object, default: () => ({}) },
  allSources: { type: Array, default: () => [] }
})

const expandedSteps = ref(new Set())

// Auto-manage accordion: when a step becomes "running", expand it and collapse others
watch(
  () => props.steps.map(s => s.stepId + ':' + s.status).join(','),
  () => {
    const running = props.steps.find(s => s.status === 'running')
    if (running) {
      expandedSteps.value = new Set([running.stepId])
    }
  }
)

function toggle(stepId) {
  const next = new Set(expandedSteps.value)
  if (next.has(stepId)) {
    next.delete(stepId)
  } else {
    next.add(stepId)
  }
  expandedSteps.value = next
}

function isExpanded(stepId) {
  return expandedSteps.value.has(stepId)
}

function content(stepId) {
  return props.stepContents[stepId] || { thinking: '', answer: '', sources: [], label: '', detail: '' }
}

function hasContent(stepId) {
  const c = content(stepId)
  return c.sources.length > 0 || c.answer || c.thinking
}

function stepLabel(step) {
  const c = content(step.stepId)
  return c.label || step.label || step.stepId
}

function stepDetail(step) {
  return step.detail || content(step.stepId).detail || ''
}

function faviconUrl(url) {
  try {
    const hostname = new URL(url).hostname
    return `https://www.google.com/s2/favicons?domain=${hostname}&sz=16`
  } catch {
    return ''
  }
}
</script>

<style scoped>
.lab-accordion {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0.75rem 0;
}

.lab-step {
  border: 1px solid var(--border);
  background: var(--bg);
  transition: all 0.2s ease;
}

.lab-step + .lab-step {
  border-top: none;
}

.lab-step:first-child {
  border-radius: 6px 6px 0 0;
}

.lab-step:last-child {
  border-radius: 0 0 6px 6px;
}

.lab-step:only-child {
  border-radius: 6px;
}

.lab-step.is-active {
  border-color: #F59E0B;
  border-left: 3px solid #F59E0B;
  background: rgba(245, 158, 11, 0.03);
  z-index: 1;
}

.lab-step.is-active + .lab-step {
  border-top: 1px solid var(--border);
}

.lab-step.is-done {
  opacity: 0.95;
}

.lab-step.is-pending {
  opacity: 0.45;
}

/* ── Header ────────────────────────────────────── */

.lab-step-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.55rem 0.75rem;
  background: none;
  border: none;
  cursor: pointer;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: var(--text-mid);
  transition: color 0.15s, background 0.15s;
  text-align: left;
  overflow: visible;
}

.lab-step-header:hover {
  color: var(--text);
  background: rgba(0, 0, 0, 0.02);
}

/* Status icons */
.step-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.is-done .step-icon {
  color: #22C55E;
}

.step-icon-pulse {
  position: relative;
}

.step-icon-pulse::after {
  content: '';
  display: block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #F59E0B;
  animation: labPulse 1.5s ease-in-out infinite;
}

.step-icon-blank {
  position: relative;
}

.step-icon-blank::after {
  content: '';
  display: block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--border);
}

.step-label {
  flex: 1;
  min-width: 0;
  line-height: 1.4;
  word-break: break-word;
}

.is-active .step-label {
  color: var(--text);
  font-weight: 500;
}

.step-detail {
  margin-left: auto;
  font-size: 0.6rem;
  color: var(--text-light);
  white-space: nowrap;
}

.step-chevron {
  flex-shrink: 0;
  opacity: 0.4;
  transition: transform 0.2s ease, opacity 0.2s;
  width: 8px;
  height: 8px;
}

.step-chevron.open {
  transform: rotate(90deg);
  opacity: 0.7;
}

/* ── Body ──────────────────────────────────────── */

.lab-step-body {
  padding: 0 0.75rem 0.75rem 2.4rem;
  animation: slideDown 0.2s ease;
}

/* Source chips */
.step-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-bottom: 0.5rem;
}

.source-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.5rem;
  background: rgba(0, 0, 0, 0.035);
  border: 1px solid var(--border);
  border-radius: 4px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-mid);
  text-decoration: none;
  transition: all 0.15s;
  max-width: 280px;
  overflow: hidden;
}

.source-chip:hover {
  border-color: #F59E0B;
  color: #F59E0B;
  background: rgba(245, 158, 11, 0.05);
}

.source-chip-icon {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  flex-shrink: 0;
}

.source-chip-id {
  color: #F59E0B;
  font-weight: 600;
  flex-shrink: 0;
}

.source-chip-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Section answer */
.step-answer {
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--text);
}

.step-answer :deep(p) { margin-bottom: 0.6rem; }
.step-answer :deep(p:last-child) { margin-bottom: 0; }
.step-answer :deep(ul), .step-answer :deep(ol) { margin: 0.4rem 0; padding-left: 1.5rem; }
.step-answer :deep(li) { margin-bottom: 0.25rem; }
.step-answer :deep(h2) { font-size: 1rem; margin: 0.8rem 0 0.4rem; display: none; }
.step-answer :deep(h3) { font-size: 0.92rem; margin: 0.6rem 0 0.3rem; }
.step-answer :deep(strong) { font-weight: 600; }
.step-answer :deep(code) {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.82rem;
  background: rgba(0, 0, 0, 0.05);
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
}

/* Hide the ## title inside answer since the accordion header already shows it */
.step-answer :deep(h2:first-child) { display: none; }

/* Loading indicator */
.step-loading {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.step-loading-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #F59E0B;
  animation: labPulse 1.5s ease-in-out infinite;
}

.step-loading-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  color: var(--text-light);
  letter-spacing: 0.02em;
}

/* Streaming cursor */
.step-cursor {
  display: inline-block;
  color: #F59E0B;
  font-size: 0.85rem;
  animation: blink 1s step-end infinite;
}

/* ── Animations ────────────────────────────────── */

@keyframes labPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@keyframes slideDown {
  from { opacity: 0; max-height: 0; }
  to { opacity: 1; max-height: 2000px; }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
