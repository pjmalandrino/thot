import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from './api.js'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref([])
  const uploading = ref(false)

  async function load(conversationId) {
    try {
      documents.value = await api.fetchDocuments(conversationId)
    } catch (e) {
      console.error('Failed to load documents', e)
    }
  }

  async function upload(conversationId, file) {
    uploading.value = true
    try {
      const doc = await api.uploadDocument(conversationId, file)
      documents.value.push(doc)
      return doc
    } catch (e) {
      console.error('Failed to upload document', e)
      throw e
    } finally {
      uploading.value = false
    }
  }

  async function remove(conversationId, documentId) {
    try {
      await api.deleteDocument(conversationId, documentId)
      documents.value = documents.value.filter(d => d.id !== documentId)
    } catch (e) {
      console.error('Failed to delete document', e)
    }
  }

  function clear() {
    documents.value = []
  }

  return { documents, uploading, load, upload, remove, clear }
})
