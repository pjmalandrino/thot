<template>
  <div class="drive-section">
    <!-- Not configured: show disabled button with tooltip -->
    <button
      v-if="!driveStore.configured"
      class="nav-item drive-btn"
      disabled
      title="Google Drive non configure sur ce serveur"
    >
      <svg class="drive-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2L2 19.5h20L12 2z" />
        <path d="M8 19.5L15.5 7" />
        <path d="M16 19.5L8.5 7" />
      </svg>
      <span>Google Drive</span>
    </button>

    <!-- Configured but not connected: clickable -->
    <button
      v-else-if="!driveStore.connected"
      class="nav-item drive-btn"
      :disabled="driveStore.loading"
      @click="driveStore.connect()"
    >
      <svg class="drive-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2L2 19.5h20L12 2z" />
        <path d="M8 19.5L15.5 7" />
        <path d="M16 19.5L8.5 7" />
      </svg>
      <span>Google Drive</span>
    </button>

    <!-- Connected: show status with disconnect on hover -->
    <div v-else class="nav-item drive-connected" @mouseenter="hovered = true" @mouseleave="hovered = false">
      <div class="drive-connected-info">
        <span class="drive-dot"></span>
        <span class="drive-label">Drive</span>
        <span class="drive-email">{{ truncatedEmail }}</span>
      </div>
      <button
        v-if="hovered"
        class="drive-disconnect"
        :disabled="driveStore.loading"
        @click.stop="driveStore.disconnect()"
        title="Deconnecter"
      >&times;</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useDriveStore } from '../store.js'

const driveStore = useDriveStore()
const hovered = ref(false)

const truncatedEmail = computed(() => {
  const e = driveStore.email
  if (!e) return ''
  return e.length > 18 ? e.substring(0, 18) + '...' : e
})
</script>

<style scoped>
.drive-section {
  margin-top: 0;
}

.drive-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.7rem 0.75rem;
  background: none;
  border: none;
  border-bottom: 1px solid var(--dark-border);
  border-top: 1px solid var(--dark-border);
  color: var(--text-on-dark-muted);
  font-size: 0.82rem;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.drive-btn:hover:not(:disabled) {
  color: var(--text-on-dark);
  background: var(--dark-mid);
}

.drive-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.drive-icon {
  width: 0.85rem;
  height: 0.85rem;
  flex-shrink: 0;
}

.drive-connected {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.7rem 0.75rem;
  background: none;
  border: none;
  border-bottom: 1px solid var(--dark-border);
  border-top: 1px solid var(--dark-border);
  color: var(--text-on-dark-muted);
  font-size: 0.82rem;
  cursor: default;
  transition: all 0.2s ease;
}

.drive-connected:hover {
  background: var(--dark-mid);
}

.drive-connected-info {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.drive-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-pop);
  flex-shrink: 0;
}

.drive-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--text-on-dark);
}

.drive-email {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  color: var(--text-on-dark-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.drive-disconnect {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  transition: color 0.2s ease;
  flex-shrink: 0;
}

.drive-disconnect:hover {
  color: #E85D4A;
}

.drive-disconnect:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
</style>
