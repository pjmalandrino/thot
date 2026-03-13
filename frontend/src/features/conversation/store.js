import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from './api.js'

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref([])
  const selectedId = ref(null)

  async function load(thotspaceId) {
    try {
      conversations.value = thotspaceId
        ? await api.fetchConversationsBySpace(thotspaceId)
        : await api.fetchConversations()
    } catch (e) {
      console.error('Failed to load conversations', e)
    }
  }

  async function create(thotspaceId) {
    try {
      const conv = await api.createConversation(thotspaceId)
      conversations.value.unshift(conv)
      selectedId.value = conv.id
      return conv
    } catch (e) {
      console.error('Failed to create conversation', e)
    }
  }

  async function remove(id) {
    try {
      await api.deleteConversation(id)
      conversations.value = conversations.value.filter(c => c.id !== id)
      if (selectedId.value === id) {
        selectedId.value = null
      }
    } catch (e) {
      console.error('Failed to delete conversation', e)
    }
  }

  function select(id) {
    selectedId.value = id
  }

  async function rename(id, title) {
    const idx = conversations.value.findIndex(c => c.id === id)
    const previous = idx !== -1 ? conversations.value[idx].title : null
    // Optimistic update : mise a jour immediate sans attendre l'API
    if (idx !== -1) conversations.value[idx] = { ...conversations.value[idx], title }
    try {
      const updated = await api.renameConversation(id, title)
      if (idx !== -1) conversations.value[idx] = { ...conversations.value[idx], title: updated.title }
    } catch (e) {
      console.error('Failed to rename conversation', e)
      // Revert en cas d'erreur API
      if (idx !== -1 && previous !== null) conversations.value[idx] = { ...conversations.value[idx], title: previous }
    }
  }

  return { conversations, selectedId, load, create, remove, select, rename }
})
