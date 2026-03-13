<template>
  <div class="app-layout">
    <ConversationSidebar />
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import { ConversationSidebar, useConversationStore } from '../features/conversation/index.js'
import { useModelStore } from '../features/llm-model/store.js'

const conversationStore = useConversationStore()
const modelStore = useModelStore()

onMounted(() => {
  conversationStore.load()
  modelStore.load()
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@200;300;400;500;600;700;800&family=IBM+Plex+Mono:wght@300;400;500;600&display=swap');

:root {
  --bg: #F5F2ED;
  --bg-light: #FAFAF7;
  --dark: #141210;
  --dark-mid: #1E1C18;
  --dark-light: #2A2824;
  --dark-border: #333028;

  --accent: #D4A438;
  --accent-light: #E8BE58;
  --accent-pop: #3DCAAD;

  --text: #141210;
  --text-mid: #5C584E;
  --text-light: #9C9688;
  --text-on-dark: #D4D0C8;
  --text-on-dark-muted: #7C7870;

  --border: #D8D4CC;
  --radius: 0;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: 'Inter', -apple-system, sans-serif;
  background: var(--bg);
  color: var(--text);
  line-height: 1.5;
  height: 100vh;
  overflow: hidden;
  -webkit-font-smoothing: antialiased;
}

.app-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: 100vh;
}

.main {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}
</style>
