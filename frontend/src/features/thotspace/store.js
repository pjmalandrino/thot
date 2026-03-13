import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from './api.js'

export const useThotspaceStore = defineStore('thotspace', () => {
  const spaces = ref([])
  const selectedSpaceId = ref(null)

  const activeSpace = computed(() =>
    spaces.value.find(s => s.id === selectedSpaceId.value) || null
  )

  async function load() {
    try {
      spaces.value = await api.fetchThotspaces()
      if (selectedSpaceId.value === null && spaces.value.length > 0) {
        const def = spaces.value.find(s => s.default)
        selectedSpaceId.value = def ? def.id : spaces.value[0].id
      }
    } catch (e) {
      console.error('Failed to load thotspaces', e)
    }
  }

  function selectSpace(id) {
    selectedSpaceId.value = id
  }

  async function create(payload) {
    try {
      const space = await api.createThotspace(payload)
      spaces.value.push(space)
      selectedSpaceId.value = space.id
      return space
    } catch (e) {
      console.error('Failed to create thotspace', e)
    }
  }

  async function update(id, payload) {
    try {
      const updated = await api.updateThotspace(id, payload)
      const idx = spaces.value.findIndex(s => s.id === id)
      if (idx !== -1) spaces.value[idx] = updated
      return updated
    } catch (e) {
      console.error('Failed to update thotspace', e)
    }
  }

  async function remove(id) {
    try {
      await api.deleteThotspace(id)
      spaces.value = spaces.value.filter(s => s.id !== id)
      if (selectedSpaceId.value === id) {
        const def = spaces.value.find(s => s.default)
        selectedSpaceId.value = def ? def.id : null
      }
    } catch (e) {
      console.error('Failed to delete thotspace', e)
    }
  }

  return { spaces, selectedSpaceId, activeSpace, load, selectSpace, create, update, remove }
})
