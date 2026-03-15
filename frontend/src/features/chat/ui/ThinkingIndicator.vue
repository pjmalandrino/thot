<template>
  <div class="thinking" v-if="visible">
    <div
      v-for="(step, idx) in displaySteps"
      :key="step.stepId"
      class="thinking-step"
      :class="{
        active: step.status === 'running',
        done: step.status === 'done',
        skipped: step.status === 'skipped',
        pending: step.status === 'pending'
      }"
      :style="{ animationDelay: (idx * 0.05) + 's' }"
    >
      <span class="thinking-check" v-if="step.status === 'done'">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="2.5 6 5 8.5 9.5 3.5" />
        </svg>
      </span>
      <span class="thinking-skip" v-else-if="step.status === 'skipped'">&mdash;</span>
      <span class="thinking-dot" v-else-if="step.status === 'running'"></span>
      <span class="thinking-blank" v-else></span>
      <span class="thinking-label">{{ step.label }}</span>
      <span v-if="step.detail && step.status === 'done'" class="thinking-detail">{{ step.detail }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // SSE-driven steps: [{stepId, status, label, detail}]
  // When provided, replaces the fake animation
  sseSteps: { type: Array, default: null }
})

// Fallback fake steps for Standard mode (no SSE)
const fakeSteps = [
  { stepId: 'vagueness', label: 'Analyse de votre question...', status: 'pending' },
  { stepId: 'rewriting', label: 'Reformulation pour plus de precision...', status: 'pending' },
  { stepId: 'websearch', label: 'Recherche de sources...', status: 'pending' },
  { stepId: 'relevance', label: 'Verification de la pertinence...', status: 'pending' },
  { stepId: 'budget', label: 'Optimisation du contexte...', status: 'pending' }
]

const currentFakeStep = ref(0)
let timer = null

const isSSEMode = computed(() => props.sseSteps !== null)

const displaySteps = computed(() => {
  if (isSSEMode.value) {
    return props.sseSteps
  }
  // Fake animation mode
  return fakeSteps.map((step, idx) => ({
    ...step,
    status: idx < currentFakeStep.value ? 'done' : idx === currentFakeStep.value ? 'running' : 'pending'
  }))
})

function startAnimation() {
  if (isSSEMode.value) return
  currentFakeStep.value = 0
  clearInterval(timer)
  timer = setInterval(() => {
    if (currentFakeStep.value < fakeSteps.length) {
      currentFakeStep.value++
    } else {
      clearInterval(timer)
    }
  }, 800)
}

function stopAnimation() {
  clearInterval(timer)
  timer = null
}

watch(() => props.visible, (val) => {
  if (val && !isSSEMode.value) startAnimation()
  else if (!val) stopAnimation()
})

onMounted(() => {
  if (props.visible && !isSSEMode.value) startAnimation()
})

onUnmounted(() => {
  stopAnimation()
})
</script>

<style scoped>
.thinking {
  padding: 0.85rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  background: rgba(61, 202, 173, 0.04);
  border-left: 3px solid var(--accent-pop);
  margin: 0.75rem 0;
  animation: fadeInUp 0.3s ease;
}

.thinking-step {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.02em;
  transition: opacity 0.3s ease, color 0.3s ease;
  animation: stepSlideIn 0.3s ease both;
}

.thinking-step.done {
  color: var(--accent-pop);
  opacity: 0.85;
}

.thinking-step.active {
  color: var(--accent);
}

.thinking-step.skipped {
  color: var(--text-light);
  opacity: 0.4;
  text-decoration: line-through;
}

.thinking-step.pending {
  color: var(--text-light);
  opacity: 0.4;
}

.thinking-check {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-pop);
  animation: checkIn 0.25s ease;
}

.thinking-skip {
  width: 14px;
  text-align: center;
  font-size: 0.65rem;
  color: var(--text-light);
}

.thinking-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  margin: 0 4px;
  animation: pulse 1.5s ease-in-out infinite;
}

.thinking-blank {
  width: 14px;
}

.thinking-label {
  line-height: 1.4;
}

.thinking-detail {
  margin-left: auto;
  font-size: 0.65rem;
  color: var(--text-light);
  opacity: 0.8;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes stepSlideIn {
  from { opacity: 0; transform: translateX(-6px); }
  to { opacity: 1; transform: translateX(0); }
}

@keyframes checkIn {
  from { opacity: 0; transform: scale(0.5); }
  to { opacity: 1; transform: scale(1); }
}
</style>
