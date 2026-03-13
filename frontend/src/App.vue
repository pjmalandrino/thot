<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img src="/thot-logo.png" alt="Thot" class="brand-logo" />
        <div class="brand-text">
          <span class="brand-name">THOT</span>
          <span class="brand-ver">v0.1</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button class="new-conv-btn" @click="createConversation">
          <span>+ Nouvelle session</span>
        </button>

        <div class="section-label">Historique</div>

        <div class="conv-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: selectedId === conv.id }"
            @click="selectConversation(conv.id)"
          >
            <div class="conv-info">
              <span class="conv-title">{{ conv.title }}</span>
              <span class="conv-date">{{ formatDate(conv.createdAt) }}</span>
            </div>
            <button class="conv-delete" @click.stop="deleteConversation(conv.id)" title="Supprimer">&times;</button>
          </div>
        </div>

        <div class="nav-spacer"></div>

        <div class="section-label">Systeme</div>

        <button
          class="nav-item"
          :class="{ active: activeView === 'config' }"
          @click="activeView = 'config'; selectedId = null"
        >
          Configuration
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="footer-user">
          <span class="user-dot"></span>
          <span class="user-name">{{ username }}</span>
        </div>
        <button class="logout-btn" @click="handleLogout">Deconnexion</button>
      </div>
    </aside>

    <main class="main">
      <HelloWorld
        v-if="selectedId"
        :conversation-id="selectedId"
        :key="selectedId"
      />
      <AdminView v-else-if="activeView === 'config'" />
      <div v-else class="empty-view">
        <span class="empty-hero">THOT</span>
        <p class="empty-sub">Creez une session pour commencer</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUsername, getToken, refreshToken, logout } from './keycloak.js'
import HelloWorld from './components/HelloWorld.vue'
import AdminView from './components/AdminView.vue'

const username = getUsername()
const activeView = ref('chat')
const conversations = ref([])
const selectedId = ref(null)

function handleLogout() { logout() }

async function apiFetch(url, options = {}) {
  await refreshToken()
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`,
      ...options.headers
    }
  })
  if (!response.ok) throw new Error(`API error: ${response.status}`)
  if (response.status === 204) return null
  return response.json()
}

async function fetchConversations() {
  try {
    conversations.value = await apiFetch('/api/conversations')
  } catch (e) {
    console.error('Failed to load conversations', e)
  }
}

async function createConversation() {
  try {
    const conv = await apiFetch('/api/conversations', { method: 'POST' })
    conversations.value.unshift(conv)
    selectedId.value = conv.id
    activeView.value = 'chat'
  } catch (e) {
    console.error('Failed to create conversation', e)
  }
}

function selectConversation(id) {
  selectedId.value = id
  activeView.value = 'chat'
}

async function deleteConversation(id) {
  try {
    await apiFetch(`/api/conversations/${id}`, { method: 'DELETE' })
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (selectedId.value === id) {
      selectedId.value = null
    }
  } catch (e) {
    console.error('Failed to delete conversation', e)
  }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })
}

onMounted(fetchConversations)
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

/* ══════════════════════════════════════════
   SIDEBAR
   ══════════════════════════════════════════ */

.sidebar {
  background: var(--dark);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--dark-border);
}

.sidebar-brand {
  padding: 1.5rem 1.5rem;
  border-bottom: 1px solid var(--dark-border);
  display: flex;
  align-items: center;
  gap: 1rem;
}

.brand-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
  border-radius: 4px;
}

.brand-text { display: flex; align-items: baseline; gap: 0.5rem; }

.brand-name {
  font-family: 'Inter', sans-serif;
  font-size: 0.85rem;
  font-weight: 700;
  color: #FFF;
  letter-spacing: 0.2em;
}

.brand-ver {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  color: var(--text-on-dark-muted);
}

/* ── Nav ── */
.sidebar-nav {
  flex: 1;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--dark-light) transparent;
}

.new-conv-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0.75rem 1rem;
  background: transparent;
  color: var(--accent);
  border: 1px solid var(--accent);
  border-radius: 0;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 1rem;
}

.new-conv-btn:hover {
  background: var(--accent);
  color: var(--dark);
}

/* ── Section Label ── */
.section-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  color: var(--text-on-dark-muted);
  padding: 1rem 0.5rem 0.5rem;
}

/* ── Conversations ── */
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.75rem;
  background: none;
  border: none;
  border-bottom: 1px solid var(--dark-border);
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease;
}

.conv-item:first-child { border-top: 1px solid var(--dark-border); }

.conv-item:hover { background: var(--dark-mid); }
.conv-item:hover .conv-delete { opacity: 0.5; }

.conv-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 0.2rem; }

.conv-delete {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: all 0.2s ease;
  padding: 0.2rem 0.4rem;
  flex-shrink: 0;
}

.conv-delete:hover { opacity: 1 !important; color: #E85D4A; }

.conv-item.active {
  background: var(--dark-mid);
}

.conv-item.active::before {
  content: '';
  display: block;
  width: 3px;
  height: 100%;
  background: var(--accent);
  position: absolute;
  left: 0;
}

.conv-item {
  position: relative;
}

.conv-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent);
}

.conv-title {
  font-size: 0.82rem;
  color: var(--text-on-dark);
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  transition: color 0.2s ease;
}

.conv-item.active .conv-title { color: #FFF; }

.conv-date {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.58rem;
  color: var(--text-on-dark-muted);
  letter-spacing: 0.02em;
}

.nav-spacer { flex: 1; }

/* ── Nav Items ── */
.nav-item {
  display: flex;
  align-items: center;
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

.nav-item:hover { color: var(--text-on-dark); background: var(--dark-mid); }
.nav-item.active { color: var(--accent); }

/* ── Footer ── */
.sidebar-footer {
  padding: 1.25rem 1.5rem;
  border-top: 1px solid var(--dark-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-pop);
}

.user-name {
  font-size: 0.75rem;
  color: var(--text-on-dark-muted);
}

.logout-btn {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 0.7rem;
  cursor: pointer;
  transition: color 0.2s ease;
}

.logout-btn:hover { color: #E85D4A; }

/* ══════════════════════════════════════════
   MAIN
   ══════════════════════════════════════════ */

.main {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg);
}

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
