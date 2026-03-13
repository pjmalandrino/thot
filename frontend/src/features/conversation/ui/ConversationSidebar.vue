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
          :class="{ active: store.selectedId === conv.id && route.name === 'chat', editing: editingId === conv.id }"
          @click="handleSelect(conv.id)"
        >
          <div class="conv-info">
            <template v-if="editingId === conv.id">
              <input
                v-focus
                class="conv-title-input"
                v-model="editingTitle"
                @keydown.enter.prevent="confirmRename(conv.id)"
                @keydown.escape.prevent="cancelRename"
                @click.stop
                maxlength="100"
              />
            </template>
            <template v-else>
              <span class="conv-title" @dblclick.stop="startRename(conv)">{{ conv.title }}</span>
            </template>
            <span class="conv-date">{{ formatDateShort(conv.createdAt) }}</span>
          </div>
          <div class="conv-actions" v-if="editingId !== conv.id">
            <button class="conv-rename" @click.stop="startRename(conv)" title="Renommer">✎</button>
            <button class="conv-delete" @click.stop="handleDelete(conv.id)" title="Supprimer">&times;</button>
          </div>
          <div class="conv-actions conv-actions--editing" v-else>
            <button class="conv-confirm" @click.stop="confirmRename(conv.id)" title="Valider">✓</button>
            <button class="conv-cancel" @click.stop="cancelRename" title="Annuler">✕</button>
          </div>
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
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getUsername, logout } from '../../../shared/auth/keycloak.js'
import { formatDateShort } from '../../../shared/utils/date.js'
import { useConversationStore } from '../store.js'

const router = useRouter()
const route = useRoute()
const store = useConversationStore()
const username = getUsername()

// ── Directive v-focus : focus + select à chaque mount de l'input ──
// Nécessaire car ref dans v-for retourne un tableau en Vue 3 (silent fail)
const vFocus = {
  mounted(el) {
    el.focus()
    el.select()
  }
}

// ── Rename state ───────────────────────────────────────────────
const editingId = ref(null)
const editingTitle = ref('')

function startRename(conv) {
  editingId.value = conv.id
  editingTitle.value = conv.title
}

async function confirmRename(id) {
  if (editingId.value !== id) return
  const title = editingTitle.value.trim()
  editingId.value = null
  if (title) await store.rename(id, title)
}

function cancelRename() {
  editingId.value = null
  editingTitle.value = ''
}

// ── Navigation ─────────────────────────────────────────────────
function handleLogout() { logout() }

async function handleCreate() {
  await store.create()
  router.push('/')
}

function handleSelect(id) {
  if (editingId.value) return  // ignore click while editing
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
.conv-item:hover .conv-actions { opacity: 1; }
.conv-item.editing { background: var(--dark-mid); }

.conv-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 0.2rem; }

.conv-title-input {
  width: 100%;
  background: var(--dark-border);
  border: 1px solid var(--accent);
  color: var(--text-on-dark);
  font-family: 'Inter', sans-serif;
  font-size: 0.82rem;
  font-weight: 400;
  padding: 0.15rem 0.4rem;
  outline: none;
  border-radius: 0;
}

.conv-actions {
  display: flex;
  align-items: center;
  gap: 0.1rem;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.conv-rename {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 0.85rem;
  line-height: 1;
  cursor: pointer;
  transition: color 0.2s ease;
  padding: 0.2rem 0.35rem;
}

.conv-rename:hover { color: var(--accent); }

.conv-delete {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0.2rem 0.4rem;
}

.conv-delete:hover { color: #E85D4A; }

.conv-actions--editing {
  opacity: 1 !important;
}

.conv-confirm {
  background: none;
  border: none;
  color: var(--accent);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
  transition: color 0.2s ease;
  padding: 0.2rem 0.35rem;
}

.conv-confirm:hover { color: #FFF; }

.conv-cancel {
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
  transition: color 0.2s ease;
  padding: 0.2rem 0.35rem;
}

.conv-cancel:hover { color: #E85D4A; }

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
