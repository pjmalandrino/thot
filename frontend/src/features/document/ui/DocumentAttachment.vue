<template>
  <button
    class="action-toggle"
    :class="{ active: documentStore.documents.length > 0 }"
    @click="triggerUpload"
    :disabled="disabled || documentStore.uploading"
    type="button"
    title="Joindre un document"
  >
    <span v-if="documentStore.uploading" class="upload-spinner"></span>
    <span v-else>Doc</span>
  </button>

  <input
    ref="fileInput"
    type="file"
    accept=".pdf,.docx,.pptx,.xlsx,.txt,.md,.html,.png,.jpg,.jpeg,.tiff"
    style="display: none"
    @change="onFileSelected"
  />
</template>

<script setup>
import { ref } from 'vue'
import { useDocumentStore } from '../store.js'

const props = defineProps({
  conversationId: { type: Number, required: true },
  disabled: { type: Boolean, default: false }
})

const documentStore = useDocumentStore()
const fileInput = ref(null)

function triggerUpload() {
  fileInput.value?.click()
}

async function onFileSelected(e) {
  const file = e.target.files[0]
  if (!file) return
  try {
    await documentStore.upload(props.conversationId, file)
  } catch {
    // Error already logged in store
  }
  fileInput.value.value = ''
}
</script>

<style scoped>
.action-toggle {
  padding: 0.4rem 0.75rem;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-toggle:hover {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.action-toggle.active {
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(212, 164, 56, 0.06);
}

.action-toggle:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.upload-spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  display: inline-block;
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
