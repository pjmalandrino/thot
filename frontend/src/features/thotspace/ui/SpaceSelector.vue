<template>
  <div class="space-selector" v-click-outside="close">
    <button class="space-trigger" @click="open = !open">
      <span class="trigger-label">Espace</span>
      <span class="trigger-name">{{ store.activeSpace?.name ?? '—' }}</span>
      <span class="trigger-chevron" :class="{ flipped: open }">&#9662;</span>
    </button>

    <transition name="dropdown">
      <div v-if="open" class="space-dropdown">
        <button
          v-for="space in store.spaces"
          :key="space.id"
          class="space-option"
          :class="{ active: store.selectedSpaceId === space.id }"
          @click="handleSelect(space.id)"
        >
          <span class="option-bar" v-if="store.selectedSpaceId === space.id"></span>
          <span class="option-name">{{ space.name }}</span>
        </button>
        <button class="space-manage" @click="goManage">
          <span class="manage-icon">&#9881;</span>
          <span>Gerer les espaces</span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThotspaceStore } from '../store.js'
import { useConversationStore } from '../../conversation/store.js'

const router = useRouter()
const store = useThotspaceStore()
const conversationStore = useConversationStore()
const open = ref(false)

// ── Directive v-click-outside ──
const vClickOutside = {
  mounted(el, binding) {
    el._clickOutside = (e) => {
      if (!el.contains(e.target)) binding.value()
    }
    document.addEventListener('click', el._clickOutside)
  },
  unmounted(el) {
    document.removeEventListener('click', el._clickOutside)
  }
}

function close() { open.value = false }

function handleSelect(id) {
  if (store.selectedSpaceId === id) {
    open.value = false
    return
  }
  store.selectSpace(id)
  conversationStore.load(id)
  conversationStore.select(null)
  open.value = false
}

function goManage() {
  open.value = false
  router.push('/thotspaces')
}
</script>

<style scoped>
.space-selector {
  position: relative;
  border-bottom: 1px solid var(--dark-border);
}

/* ── Trigger (collapsed state) ── */
.space-trigger {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.85rem 1.5rem;
  background: none;
  border: none;
  cursor: pointer;
  gap: 0.6rem;
  transition: background 0.2s ease;
}

.space-trigger:hover {
  background: var(--dark-mid);
}

.trigger-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.2em;
  color: var(--text-on-dark-muted);
  flex-shrink: 0;
}

.trigger-name {
  flex: 1;
  text-align: left;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--accent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trigger-chevron {
  font-size: 0.6rem;
  color: var(--text-on-dark-muted);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.trigger-chevron.flipped {
  transform: rotate(180deg);
}

/* ── Dropdown ── */
.space-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--dark);
  border-bottom: 1px solid var(--dark-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

.space-option {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.6rem 1.5rem;
  background: none;
  border: none;
  color: var(--text-on-dark-muted);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 400;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
  position: relative;
}

.space-option:hover {
  color: var(--text-on-dark);
  background: var(--dark-mid);
}

.space-option.active {
  color: var(--accent);
}

.option-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--accent);
}

.option-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.space-manage {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  padding: 0.55rem 1.5rem;
  background: none;
  border: none;
  border-top: 1px solid var(--dark-border);
  color: var(--text-on-dark-muted);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 400;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all 0.15s ease;
}

.space-manage:hover {
  color: var(--accent);
  background: var(--dark-mid);
}

.manage-icon {
  font-size: 0.7rem;
}

/* ── Transition ── */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
  transform-origin: top center;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: scaleY(0.95) translateY(-4px);
}
</style>
