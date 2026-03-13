<template>
  <div class="space-selector">
    <div class="space-label">Espace</div>
    <div class="space-list">
      <button
        v-for="space in store.spaces"
        :key="space.id"
        class="space-pill"
        :class="{ active: store.selectedSpaceId === space.id }"
        @click="handleSelect(space.id)"
      >
        {{ space.name }}
      </button>
      <button class="space-add" @click="$router.push('/thotspaces')" title="Gerer les espaces">+</button>
    </div>
  </div>
</template>

<script setup>
import { useThotspaceStore } from '../store.js'
import { useConversationStore } from '../../conversation/store.js'

const store = useThotspaceStore()
const conversationStore = useConversationStore()

function handleSelect(id) {
  if (store.selectedSpaceId === id) return
  store.selectSpace(id)
  conversationStore.load(id)
  conversationStore.select(null)
}
</script>

<style scoped>
.space-selector {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--dark-border);
}

.space-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  color: var(--text-on-dark-muted);
  padding-bottom: 0.5rem;
}

.space-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.space-pill {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.5rem 0.75rem;
  background: none;
  border: none;
  border-bottom: 1px solid var(--dark-border);
  color: var(--text-on-dark-muted);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 400;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  position: relative;
}

.space-pill:first-child { border-top: 1px solid var(--dark-border); }

.space-pill:hover {
  color: var(--text-on-dark);
  background: var(--dark-mid);
}

.space-pill.active {
  color: var(--accent);
  background: var(--dark-mid);
}

.space-pill.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent);
}

.space-add {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0.45rem;
  background: none;
  border: 1px dashed var(--dark-border);
  color: var(--text-on-dark-muted);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-top: 0.35rem;
}

.space-add:hover {
  color: var(--accent);
  border-color: var(--accent);
}
</style>
