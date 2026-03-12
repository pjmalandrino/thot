<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <img src="/thot-logo.png" alt="Thot" class="brand-logo" />
        <div class="brand-text">
          <span class="brand-name">THOT</span>
          <span class="brand-ver">v0.1 &mdash; interface ia</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button class="new-conv-btn" @click="createConversation">
          <span class="btn-icon">+</span>
          <span>Nouvelle session</span>
        </button>

        <div class="section-label">
          <span class="section-line"></span>
          <span class="section-text">Historique</span>
          <span class="section-line"></span>
        </div>

        <div class="conv-list">
          <div
            v-for="(conv, idx) in conversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: selectedId === conv.id }"
            @click="selectConversation(conv.id)"
          >
            <span class="conv-num">{{ String(conversations.length - idx).padStart(2, '0') }}</span>
            <div class="conv-info">
              <span class="conv-title">{{ conv.title }}</span>
              <span class="conv-date">{{ formatDate(conv.createdAt) }}</span>
            </div>
            <button class="conv-delete" @click.stop="deleteConversation(conv.id)" title="Supprimer">&times;</button>
          </div>
        </div>

        <div class="nav-spacer"></div>

        <div class="section-label">
          <span class="section-line"></span>
          <span class="section-text">Systeme</span>
          <span class="section-line"></span>
        </div>

        <button
          class="nav-item"
          :class="{ active: activeView === 'config' }"
          @click="activeView = 'config'; selectedId = null"
        >
          <span class="nav-icon">&gt;</span>
          <span>Configuration</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="footer-user">
          <span class="user-dot"></span>
          <span class="user-name">{{ username }}</span>
        </div>
        <button class="logout-btn" @click="handleLogout">deconnexion</button>
      </div>
    </aside>

    <main class="main">
      <HelloWorld
        v-if="selectedId"
        :conversation-id="selectedId"
        :key="selectedId"
      />
      <div v-else-if="activeView === 'config'" class="empty-view">
        <div class="empty-block">
          <span class="empty-tag">&gt; config</span>
          <p class="empty-desc">Module en cours de developpement.</p>
        </div>
      </div>
      <div v-else class="empty-view">
        <div class="empty-block">
          <span class="empty-tag">&gt; thot</span>
          <p class="empty-desc">Creez une session pour commencer.</p>
          <span class="empty-hint">Utilisez le bouton [+] dans la sidebar.</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUsername, getToken, refreshToken, logout } from './keycloak.js'
import HelloWorld from './components/HelloWorld.vue'

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
:root {
  --bg-main: #FAF8F4;
  --bg-sidebar: #F0EBE1;
  --bg-card: #E8E1D5;
  --bg-input: #FFFFFF;
  --border: #D0C7B8;
  --border-light: #E2DAD0;

  --copper: #A07850;
  --copper-light: #C09870;
  --copper-dark: #705030;
  --amb-gold: #B08828;
  --cto-terra: #A04840;
  --teal: #286858;

  --text-primary: #1A1410;
  --text-secondary: #4A4038;
  --text-muted: #908070;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: 'Inter', -apple-system, sans-serif;
  background: var(--bg-main);
  color: var(--text-secondary);
  line-height: 1.6;
  height: 100vh;
  overflow: hidden;
  -webkit-font-smoothing: antialiased;
}

.app-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  height: 100vh;
}

/* ═══════════════════════════════════════
   SIDEBAR
   ═══════════════════════════════════════ */

.sidebar {
  background: var(--bg-sidebar);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border);
}

.sidebar-brand {
  padding: 1.25rem 1rem;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.brand-logo {
  width: 32px;
  height: 32px;
  object-fit: contain;
  border-radius: 4px;
  filter: saturate(0.8);
}

.brand-text { display: flex; flex-direction: column; gap: 0.1rem; }

.brand-name {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.2em;
}

.brand-ver {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  color: var(--text-muted);
  letter-spacing: 0.06em;
}

/* ── Nav ── */
.sidebar-nav {
  flex: 1;
  padding: 0.75rem 0.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

.new-conv-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.55rem 0.75rem;
  background: var(--copper);
  color: #FFF;
  border: none;
  border-radius: 4px;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  font-weight: 500;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 0.25rem;
}

.new-conv-btn:hover { background: var(--copper-light); }
.btn-icon { font-weight: 700; font-size: 0.85rem; line-height: 1; }

/* ── Section Label ── */
.section-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.25rem 0.35rem;
}

.section-line { flex: 1; height: 1px; background: var(--border); }

.section-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  color: var(--text-muted);
  white-space: nowrap;
}

/* ── Conversation List ── */
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.45rem 0.6rem;
  background: none;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s;
}

.conv-item:hover { background: var(--bg-card); }
.conv-item:hover .conv-delete { opacity: 0.5; }

.conv-info { flex: 1; min-width: 0; }

.conv-delete {
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s, color 0.12s;
  padding: 0.15rem 0.3rem;
  border-radius: 3px;
  flex-shrink: 0;
}

.conv-delete:hover { opacity: 1 !important; color: var(--cto-terra); background: rgba(160, 72, 64, 0.08); }

.conv-item.active {
  background: var(--bg-main);
  box-shadow: inset 2px 0 0 var(--copper);
}

.conv-num {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.5rem;
  color: var(--text-muted);
  opacity: 0.5;
  min-width: 1.2rem;
}

.conv-title {
  font-size: 0.72rem;
  color: var(--text-secondary);
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item.active .conv-title { color: var(--text-primary); font-weight: 500; }

.conv-date {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.48rem;
  color: var(--text-muted);
}

.nav-spacer { flex: 1; }

/* ── Nav Items ── */
.nav-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.5rem 0.6rem;
  background: none;
  border: none;
  border-radius: 4px;
  color: var(--text-muted);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  letter-spacing: 0.04em;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s, color 0.12s;
}

.nav-item:hover { background: var(--bg-card); color: var(--text-secondary); }
.nav-item.active { background: var(--bg-main); color: var(--copper); box-shadow: inset 2px 0 0 var(--copper); }
.nav-icon { font-weight: 600; font-size: 0.7rem; }

/* ── Footer ── */
.sidebar-footer {
  padding: 0.8rem 1rem;
  border-top: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.user-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--teal);
}

.user-name {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.logout-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  letter-spacing: 0.06em;
  cursor: pointer;
  transition: color 0.15s;
  opacity: 0.6;
}

.logout-btn:hover { color: var(--cto-terra); opacity: 1; }

/* ═══════════════════════════════════════
   MAIN
   ═══════════════════════════════════════ */

.main {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.empty-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
}

.empty-tag {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.75rem;
  color: var(--copper);
  letter-spacing: 0.06em;
}

.empty-desc {
  font-size: 0.82rem;
  color: var(--text-muted);
  font-weight: 300;
}

.empty-hint {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-muted);
  opacity: 0.5;
  letter-spacing: 0.04em;
}
</style>
