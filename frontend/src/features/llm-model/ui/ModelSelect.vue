<template>
  <select v-model="store.selectedModelId" class="model-select" :disabled="disabled">
    <option v-for="m in store.models" :key="m.id" :value="m.id">{{ m.displayLabel }}</option>
    <option v-if="store.models.length === 0" disabled value="">Chargement…</option>
  </select>
</template>

<script setup>
import { useModelStore } from '../store.js'

defineProps({
  disabled: { type: Boolean, default: false }
})

const store = useModelStore()
</script>

<style scoped>
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
</style>
