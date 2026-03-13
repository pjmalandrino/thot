<template>
  <ChatWindow
    v-if="store.selectedId"
    :conversation-id="store.selectedId"
    :key="store.selectedId"
  />
  <div v-else class="empty-view">
    <span class="empty-hero">THOT</span>
    <p class="empty-sub">Creez une session pour commencer</p>
  </div>
</template>

<script setup>
import { useConversationStore } from '../features/conversation/store.js'
import { ChatWindow } from '../features/chat/index.js'

const store = useConversationStore()
</script>

<style scoped>
.empty-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 1.5rem;
}

.empty-hero {
  font-size: 5rem;
  font-weight: 200;
  letter-spacing: 0.3em;
  color: var(--text);
  opacity: 0.06;
  line-height: 1;
}

.empty-sub {
  font-size: 0.9rem;
  color: var(--text-light);
  font-weight: 400;
}
</style>
