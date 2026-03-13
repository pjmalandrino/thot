import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from './api.js'

export const useConversationStore = defineStore('conversation', () => {
  const conversations = ref([])
  const selectedId = ref(null)

  async function load() {
    try {
      conversations.value = await api.fetchConversations()
    } catch (e) {
      console.error('Failed to load conversations', e)
    }
  }

  async function create() {
    try {
      const conv = await api.createConversation()
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

  return { conversations, selectedId, load, create, remove, select }
})
