<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <img src="/thot-logo.png" alt="Thot" class="brand-logo" />
      <div class="brand-text">
        <span class="brand-name">THOT</span>
        <span class="brand-ver">v0.1</span>
      </div>
    </div>

    <nav class="sidebar-nav">
      <button class="new-conv-btn" @click="handleCreate">
        <span>+ Nouvelle session</span>
      </button>

      <div class="section-label">Historique</div>

      <div class="conv-list">
        <div
          v-for="conv in store.conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: store.selectedId === conv.id && route.name === 'chat' }"
          @click="handleSelect(conv.id)"
        >
          <div class="conv-info">
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-date">{{ formatDateShort(conv.createdAt) }}</span>
          </div>
          <button class="conv-delete" @click.stop="handleDelete(conv.id)" title="Supprimer">&times;</button>
        </div>
      </div>

      <div class="nav-spacer"></div>

      <div class="section-label">Systeme</div>

      <button
        class="nav-item"
        :class="{ active: route.name === 'admin' }"
        @click="router.push('/admin')"
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
</template>

<script setup>
import { useRouter, useRoute } from 'vue-router'
import { getUsername, logout } from '../../../shared/auth/keycloak.js'
import { formatDateShort } from '../../../shared/utils/date.js'
import { useConversationStore } from '../store.js'

const router = useRouter()
const route = useRoute()
const store = useConversationStore()
const username = getUsername()

function handleLogout() { logout() }

async function handleCreate() {
  await store.create()
  router.push('/')
}

function handleSelect(id) {
  store.select(id)
  router.push('/')
}

async function handleDelete(id) {
  await store.remove(id)
}
</script>

<style scoped>
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
  position: relative;
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
</style>
