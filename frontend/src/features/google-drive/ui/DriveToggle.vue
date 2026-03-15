<template>
  <button
    v-if="driveStore.configured"
    class="drive-toggle"
    :class="{
      active: driveStore.driveSearchEnabled,
      disabled: !driveStore.connected
    }"
    :disabled="!driveStore.connected || disabled"
    :title="tooltipText"
    @click="driveStore.toggleDriveSearch()"
  >
    <svg class="drive-toggle-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 2L2 19.5h20L12 2z" />
      <path d="M8 19.5L15.5 7" />
      <path d="M16 19.5L8.5 7" />
    </svg>
    <span v-if="driveStore.driveSearchEnabled" class="drive-toggle-label">Drive</span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { useDriveStore } from '../store.js'

defineProps({
  disabled: { type: Boolean, default: false }
})

const driveStore = useDriveStore()

const tooltipText = computed(() => {
  if (!driveStore.connected) return 'Google Drive non connecte'
  return driveStore.driveSearchEnabled ? 'Desactiver la recherche Drive' : 'Activer la recherche Drive'
})
</script>

<style scoped>
.drive-toggle {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.35rem 0.55rem;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.drive-toggle:hover:not(:disabled) {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.drive-toggle.active {
  border-color: var(--accent-pop);
  color: var(--accent-pop);
  background: rgba(61, 202, 173, 0.04);
}

.drive-toggle.active:hover {
  background: rgba(61, 202, 173, 0.08);
}

.drive-toggle.disabled,
.drive-toggle:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}

.drive-toggle-icon {
  width: 0.75rem;
  height: 0.75rem;
  flex-shrink: 0;
}

.drive-toggle-label {
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
</style>
