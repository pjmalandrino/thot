<template>
  <div class="thinking" v-if="visible">
    <div
      v-for="(step, idx) in steps"
      :key="step.id"
      class="thinking-step"
      :class="{ active: idx === currentStep, done: idx < currentStep, pending: idx > currentStep }"
    >
      <span class="thinking-check" v-if="idx < currentStep">&#10003;</span>
      <span class="thinking-dot" v-else-if="idx === currentStep"></span>
      <span class="thinking-blank" v-else></span>
      <span class="thinking-label">{{ step.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const steps = [
  { id: 'vagueness', label: 'Analyse de votre question...' },
  { id: 'rewriting', label: 'Reformulation pour plus de precision...' },
  { id: 'websearch', label: 'Recherche de sources...' },
  { id: 'relevance', label: 'Verification de la pertinence...' },
  { id: 'budget', label: 'Optimisation du contexte...' }
]

const currentStep = ref(0)
let timer = null

function startAnimation() {
  currentStep.value = 0
  clearInterval(timer)
  timer = setInterval(() => {
    if (currentStep.value < steps.length) {
      currentStep.value++
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
  if (val) startAnimation()
  else stopAnimation()
})

onMounted(() => {
  if (props.visible) startAnimation()
})

onUnmounted(() => {
  stopAnimation()
})
</script>

<style scoped>
.thinking {
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  animation: fadeInUp 0.3s ease;
}

.thinking-step {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  letter-spacing: 0.02em;
  transition: opacity 0.3s ease;
}

.thinking-step.done {
  color: var(--accent-pop, #3DCAAD);
  opacity: 0.7;
}

.thinking-step.active {
  color: var(--accent, #D4A438);
}

.thinking-step.pending {
  color: var(--text-light);
  opacity: 0.3;
}

.thinking-check {
  width: 14px;
  text-align: center;
  font-size: 0.65rem;
  color: var(--accent-pop, #3DCAAD);
}

.thinking-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent, #D4A438);
  margin: 0 4px;
  animation: pulse 1s ease-in-out infinite;
}

.thinking-blank {
  width: 14px;
}

.thinking-label {
  line-height: 1.4;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
