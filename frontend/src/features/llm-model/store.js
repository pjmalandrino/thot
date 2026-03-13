import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from './api.js'

export const useModelStore = defineStore('llm-model', () => {
  const models = ref([])
  const selectedModelId = ref(null)

  async function load() {
    try {
      const data = await api.fetchModels()
      models.value = data
      if (data.length > 0 && selectedModelId.value === null) {
        selectedModelId.value = data[0].id
      }
    } catch (e) {
      console.error('Failed to load models', e)
    }
  }

  return { models, selectedModelId, load }
})
