<template>
  <div class="manager-layout">
    <div class="manager-header">
      <span class="manager-title">Thotspaces</span>
      <span class="manager-sub">Configurez vos espaces pour specialiser THOT</span>
    </div>

    <div class="manager-content">
      <!-- ── Space cards ── -->
      <div v-for="space in store.spaces" :key="space.id" class="space-card">
        <div class="space-head">
          <div class="space-meta">
            <span class="space-name">{{ space.name }}</span>
            <span v-if="space.default" class="tag tag-default">Defaut</span>
            <span v-if="space.description" class="space-desc">{{ space.description }}</span>
          </div>
          <div class="space-actions">
            <button class="btn-ghost" @click="startEdit(space)">Modifier</button>
            <button
              v-if="!space.default"
              class="btn-delete"
              @click="handleDelete(space.id)"
              title="Supprimer"
            >&times;</button>
          </div>
        </div>

        <!-- Edit inline form -->
        <div v-if="editingId === space.id" class="inline-form">
          <div class="form-row">
            <input
              v-model="editForm.name"
              class="form-input"
              placeholder="Nom de l'espace"
              maxlength="100"
            />
          </div>
          <div class="form-row">
            <input
              v-model="editForm.description"
              class="form-input"
              placeholder="Description (optionnel)"
              maxlength="500"
            />
          </div>
          <div class="form-row">
            <textarea
              v-model="editForm.systemPrompt"
              class="form-textarea"
              placeholder="System prompt — Instructions qui seront injectees dans chaque conversation de cet espace..."
              rows="6"
            ></textarea>
          </div>
          <div class="form-row form-row--actions">
            <button class="btn-primary" @click="saveEdit(space.id)">Enregistrer</button>
            <button class="btn-ghost" @click="editingId = null">Annuler</button>
          </div>
        </div>

        <!-- Show system prompt preview if not editing -->
        <div v-else-if="space.systemPrompt" class="prompt-preview">
          <span class="prompt-label">System prompt</span>
          <pre class="prompt-text">{{ space.systemPrompt }}</pre>
        </div>
      </div>

      <!-- ── Add new space ── -->
      <div class="add-section">
        <button class="btn-add" @click="showAdd = !showAdd">
          {{ showAdd ? '— Annuler' : '+ Nouvel espace' }}
        </button>

        <div v-if="showAdd" class="inline-form new-form">
          <div class="form-row">
            <input
              v-model="newSpace.name"
              class="form-input"
              placeholder="Nom de l'espace"
              maxlength="100"
            />
          </div>
          <div class="form-row">
            <input
              v-model="newSpace.description"
              class="form-input"
              placeholder="Description (optionnel)"
              maxlength="500"
            />
          </div>
          <div class="form-row">
            <textarea
              v-model="newSpace.systemPrompt"
              class="form-textarea"
              placeholder="System prompt — Instructions qui seront injectees dans chaque conversation de cet espace..."
              rows="6"
            ></textarea>
          </div>
          <div class="form-row form-row--actions">
            <button class="btn-primary" @click="handleCreate" :disabled="!newSpace.name">Creer</button>
          </div>
        </div>
      </div>

      <div v-if="error" class="manager-error">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useThotspaceStore } from '../store.js'

const store = useThotspaceStore()
const error = ref('')

const editingId = ref(null)
const editForm = ref({ name: '', description: '', systemPrompt: '' })

const showAdd = ref(false)
const newSpace = ref({ name: '', description: '', systemPrompt: '' })

function startEdit(space) {
  editingId.value = space.id
  editForm.value = {
    name: space.name || '',
    description: space.description || '',
    systemPrompt: space.systemPrompt || ''
  }
}

async function saveEdit(id) {
  try {
    error.value = ''
    await store.update(id, editForm.value)
    editingId.value = null
  } catch (e) {
    error.value = e.message
  }
}

async function handleCreate() {
  try {
    error.value = ''
    await store.create(newSpace.value)
    newSpace.value = { name: '', description: '', systemPrompt: '' }
    showAdd.value = false
  } catch (e) {
    error.value = e.message
  }
}

async function handleDelete(id) {
  try {
    error.value = ''
    await store.remove(id)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<style scoped>
.manager-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow-y: auto;
}

.manager-header {
  padding: 2rem 3rem 1.5rem;
  border-bottom: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.manager-title {
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.manager-sub {
  font-size: 0.78rem;
  color: var(--text-light);
}

.manager-content {
  padding: 2rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* ── Space card ── */
.space-card {
  border: 1px solid var(--border);
  padding: 1.25rem 1.5rem;
}

.space-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.space-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.space-name {
  font-weight: 600;
  font-size: 0.88rem;
}

.tag-default {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 0.2rem 0.6rem;
  background: var(--accent);
  color: var(--dark);
}

.space-desc {
  font-size: 0.78rem;
  color: var(--text-light);
}

.space-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* ── Prompt preview ── */
.prompt-preview {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border);
}

.prompt-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--text-light);
  display: block;
  margin-bottom: 0.4rem;
}

.prompt-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--text-mid);
  background: var(--bg-light);
  padding: 0.75rem;
  border: 1px solid var(--border);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 150px;
  overflow-y: auto;
}

/* ── Forms ── */
.inline-form {
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-row {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
}

.form-row--actions {
  padding-top: 0.25rem;
}

.form-input {
  flex: 1;
  padding: 0.55rem 0.75rem;
  font-family: 'Inter', sans-serif;
  font-size: 0.82rem;
  border: 1px solid var(--border);
  background: var(--bg-light);
  color: var(--text);
  outline: none;
  transition: border-color 0.2s ease;
}

.form-input:focus { border-color: var(--text); }

.form-textarea {
  width: 100%;
  padding: 0.75rem;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.75rem;
  line-height: 1.6;
  border: 1px solid var(--border);
  background: var(--bg-light);
  color: var(--text);
  outline: none;
  resize: vertical;
  min-height: 120px;
  transition: border-color 0.2s ease;
}

.form-textarea:focus { border-color: var(--text); }

/* ── Buttons ── */
.btn-primary {
  padding: 0.55rem 1.25rem;
  background: var(--dark);
  color: #FFF;
  border: none;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  font-weight: 500;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.btn-primary:hover { opacity: 0.85; }
.btn-primary:disabled { opacity: 0.4; cursor: not-allowed; }

.btn-ghost {
  padding: 0.55rem 1rem;
  background: none;
  border: 1px solid var(--border);
  color: var(--text-mid);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover { border-color: var(--text); color: var(--text); }

.btn-delete {
  background: none;
  border: none;
  color: var(--text-light);
  font-size: 1.2rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.2rem 0.4rem;
  transition: color 0.2s ease;
}

.btn-delete:hover { color: #E85D4A; }

/* ── Add section ── */
.add-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.btn-add {
  padding: 0.75rem;
  background: none;
  border: 1px dashed var(--border);
  color: var(--text-mid);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-add:hover { border-color: var(--accent); color: var(--accent); }

.new-form {
  border: 1px solid var(--border);
  padding: 1.25rem;
  margin-top: 0;
  border-top: 1px solid var(--border);
}

.manager-error {
  font-size: 0.78rem;
  color: #E85D4A;
  padding: 0.5rem 0;
}
</style>
