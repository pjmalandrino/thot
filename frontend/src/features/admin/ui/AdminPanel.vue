<template>
  <div class="admin-layout">
    <div class="admin-header">
      <span class="admin-title">Configuration LLM</span>
      <span class="admin-sub">Gérez vos providers et modèles</span>
    </div>

    <div class="admin-content">
      <!-- ── Provider cards ── -->
      <div v-for="provider in providers" :key="provider.id" class="provider-card">
        <div class="provider-head">
          <div class="provider-meta">
            <span class="provider-name">{{ provider.name }}</span>
            <span class="tag tag-type">{{ provider.type }}</span>
            <span v-if="provider.baseUrl" class="provider-url">{{ provider.baseUrl }}</span>
            <span v-if="provider.apiKeyMasked" class="provider-key">API key ****</span>
          </div>
          <div class="provider-actions">
            <label class="toggle-wrap">
              <input
                type="checkbox"
                class="toggle-input"
                :checked="provider.enabled"
                @change="toggleProvider(provider)"
              />
              <span class="toggle-slider"></span>
              <span class="toggle-label">{{ provider.enabled ? 'Actif' : 'Inactif' }}</span>
            </label>
            <button class="btn-ghost" @click="startEditProvider(provider)">Modifier</button>
          </div>
        </div>

        <!-- Edit provider inline form -->
        <div v-if="editingProvider?.id === provider.id" class="inline-form">
          <div class="form-row">
            <input
              v-if="provider.type === 'OLLAMA'"
              v-model="editForm.baseUrl"
              class="form-input"
              placeholder="Base URL (ex: http://localhost:11434)"
            />
            <input
              v-else
              v-model="editForm.apiKey"
              class="form-input"
              placeholder="Nouvelle API key (laisser vide pour conserver)"
              type="password"
            />
            <button class="btn-primary" @click="saveProvider(provider)">Enregistrer</button>
            <button class="btn-ghost" @click="editingProvider = null">Annuler</button>
          </div>
        </div>

        <!-- Models list -->
        <div class="models-section">
          <div class="models-header">
            <span class="section-sub">Modèles</span>
            <button class="btn-add" @click="startAddModel(provider.id)">+ Ajouter</button>
          </div>

          <div class="models-list">
            <div v-for="model in getModels(provider.id)" :key="model.id" class="model-row">
              <span class="model-display">{{ model.displayLabel }}</span>
              <span class="model-tech">{{ model.modelName }}</span>
              <label class="toggle-wrap small">
                <input
                  type="checkbox"
                  class="toggle-input"
                  :checked="model.enabled"
                  @change="toggleModel(model)"
                />
                <span class="toggle-slider"></span>
              </label>
              <button class="btn-delete" @click="handleDeleteModel(model.id, provider.id)" title="Supprimer">&times;</button>
            </div>
            <div v-if="getModels(provider.id).length === 0" class="models-empty">
              Aucun modèle configuré
            </div>
          </div>

          <!-- Add model inline form -->
          <div v-if="addingModelForProvider === provider.id" class="inline-form">
            <div class="form-row">
              <input v-model="newModel.displayName" class="form-input" placeholder="Nom affiché (ex: llama3.3)" />
              <input v-model="newModel.modelName" class="form-input" placeholder="Nom technique (ex: llama3.3:8b)" />
              <button class="btn-primary" @click="handleAddModel(provider.id)">Ajouter</button>
              <button class="btn-ghost" @click="addingModelForProvider = null">Annuler</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ── Add provider section ── -->
      <div class="add-provider-section">
        <button class="btn-add-provider" @click="showAddProvider = !showAddProvider">
          {{ showAddProvider ? '— Annuler' : '+ Nouveau provider' }}
        </button>

        <div v-if="showAddProvider" class="inline-form new-provider-form">
          <div class="form-row">
            <input v-model="newProvider.name" class="form-input" placeholder="Nom (ex: Ollama Local)" />
            <select v-model="newProvider.type" class="form-select">
              <option value="">Type</option>
              <option value="OLLAMA">OLLAMA</option>
              <option value="MISTRAL">MISTRAL</option>
            </select>
          </div>
          <div class="form-row">
            <input
              v-if="newProvider.type === 'OLLAMA'"
              v-model="newProvider.baseUrl"
              class="form-input"
              placeholder="Base URL (ex: http://localhost:11434)"
            />
            <input
              v-else-if="newProvider.type === 'MISTRAL'"
              v-model="newProvider.apiKey"
              class="form-input"
              placeholder="API key Mistral"
              type="password"
            />
            <button class="btn-primary" @click="handleCreateProvider" :disabled="!newProvider.name || !newProvider.type">
              Créer
            </button>
          </div>
        </div>
      </div>

      <div v-if="error" class="admin-error">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as api from '../api.js'

const providers = ref([])
const models = ref([])
const error = ref('')

const editingProvider = ref(null)
const editForm = ref({ baseUrl: '', apiKey: '' })

const addingModelForProvider = ref(null)
const newModel = ref({ displayName: '', modelName: '' })

const showAddProvider = ref(false)
const newProvider = ref({ name: '', type: '', baseUrl: '', apiKey: '' })

function getModels(providerId) {
  return models.value.filter(m => m._providerId === providerId)
}

async function loadData() {
  try {
    error.value = ''
    const provList = await api.fetchProviders()
    providers.value = provList

    const allModels = []
    for (const p of provList) {
      const pModels = await api.fetchProviderModels(p.id)
      pModels.forEach(m => { m._providerId = p.id })
      allModels.push(...pModels)
    }
    models.value = allModels
  } catch (e) {
    error.value = e.message
  }
}

async function toggleProvider(provider) {
  try {
    const updated = await api.updateProvider(provider.id, { enabled: !provider.enabled })
    const idx = providers.value.findIndex(p => p.id === provider.id)
    if (idx !== -1) providers.value[idx] = updated
  } catch (e) {
    error.value = e.message
  }
}

function startEditProvider(provider) {
  editingProvider.value = provider
  editForm.value = { baseUrl: provider.baseUrl || '', apiKey: '' }
}

async function saveProvider(provider) {
  try {
    const payload = {}
    if (provider.type === 'OLLAMA' && editForm.value.baseUrl) payload.baseUrl = editForm.value.baseUrl
    if (provider.type === 'MISTRAL' && editForm.value.apiKey) payload.apiKey = editForm.value.apiKey
    const updated = await api.updateProvider(provider.id, payload)
    const idx = providers.value.findIndex(p => p.id === provider.id)
    if (idx !== -1) providers.value[idx] = updated
    editingProvider.value = null
  } catch (e) {
    error.value = e.message
  }
}

async function toggleModel(model) {
  try {
    const updated = await api.updateModel(model.id, { enabled: !model.enabled })
    const idx = models.value.findIndex(m => m.id === model.id)
    if (idx !== -1) {
      updated._providerId = model._providerId
      models.value[idx] = updated
    }
  } catch (e) {
    error.value = e.message
  }
}

async function handleDeleteModel(modelId, providerId) {
  try {
    await api.deleteModel(modelId)
    models.value = models.value.filter(m => m.id !== modelId)
  } catch (e) {
    error.value = e.message
  }
}

function startAddModel(providerId) {
  addingModelForProvider.value = providerId
  newModel.value = { displayName: '', modelName: '' }
}

async function handleAddModel(providerId) {
  if (!newModel.value.displayName || !newModel.value.modelName) return
  try {
    const created = await api.createModel(providerId, newModel.value)
    created._providerId = providerId
    models.value.push(created)
    addingModelForProvider.value = null
    newModel.value = { displayName: '', modelName: '' }
  } catch (e) {
    error.value = e.message
  }
}

async function handleCreateProvider() {
  if (!newProvider.value.name || !newProvider.value.type) return
  try {
    const payload = {
      name: newProvider.value.name,
      type: newProvider.value.type,
      baseUrl: newProvider.value.baseUrl || null,
      apiKey: newProvider.value.apiKey || null,
      enabled: true
    }
    const created = await api.createProvider(payload)
    providers.value.push(created)
    showAddProvider.value = false
    newProvider.value = { name: '', type: '', baseUrl: '', apiKey: '' }
  } catch (e) {
    error.value = e.message
  }
}

onMounted(loadData)
</script>

<style scoped>
.admin-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  padding: 1.1rem 3rem;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.admin-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--text);
}

.admin-sub {
  font-size: 0.72rem;
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 2rem 3rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  scrollbar-width: thin;
  scrollbar-color: var(--border) transparent;
}

/* ── Provider card ── */
.provider-card {
  border: 1px solid var(--border);
  background: #FFF;
}

.provider-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border);
}

.provider-meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.provider-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text);
}

.tag {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  padding: 0.2rem 0.55rem;
  border-radius: 50px;
}

.tag-type {
  background: var(--dark);
  color: var(--bg);
}

.provider-url, .provider-key {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-light);
}

.provider-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

/* ── Toggle ── */
.toggle-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}

.toggle-input { display: none; }

.toggle-slider {
  position: relative;
  width: 32px;
  height: 18px;
  background: var(--border);
  border-radius: 50px;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.toggle-slider::after {
  content: '';
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #FFF;
  top: 3px;
  left: 3px;
  transition: transform 0.2s ease;
}

.toggle-input:checked + .toggle-slider {
  background: var(--accent);
}

.toggle-input:checked + .toggle-slider::after {
  transform: translateX(14px);
}

.toggle-wrap.small .toggle-slider {
  width: 26px;
  height: 14px;
}

.toggle-wrap.small .toggle-slider::after {
  width: 8px;
  height: 8px;
  top: 3px;
  left: 3px;
}

.toggle-wrap.small .toggle-input:checked + .toggle-slider::after {
  transform: translateX(12px);
}

.toggle-label {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  color: var(--text-light);
}

/* ── Buttons ── */
.btn-ghost {
  background: none;
  border: 1px solid var(--border);
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  padding: 0.3rem 0.75rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-ghost:hover {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.btn-primary {
  background: var(--dark);
  border: none;
  color: #FFF;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  padding: 0.4rem 1rem;
  cursor: pointer;
  transition: background 0.2s ease;
}

.btn-primary:hover:not(:disabled) { background: var(--accent); color: var(--dark); }
.btn-primary:disabled { opacity: 0.3; cursor: not-allowed; }

.btn-delete {
  background: none;
  border: none;
  color: var(--text-light);
  font-size: 1rem;
  cursor: pointer;
  padding: 0 0.25rem;
  transition: color 0.2s ease;
}
.btn-delete:hover { color: #E85D4A; }

.btn-add {
  background: none;
  border: none;
  color: var(--accent);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.2s ease;
}
.btn-add:hover { opacity: 0.7; }

/* ── Models section ── */
.models-section {
  padding: 1rem 1.5rem 1.25rem;
}

.models-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}

.section-sub {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.18em;
  color: var(--text-light);
}

.models-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.model-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--border);
  background: var(--bg);
}

.model-display {
  font-size: 0.82rem;
  color: var(--text);
  font-weight: 500;
  min-width: 150px;
}

.model-tech {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.62rem;
  color: var(--text-light);
  flex: 1;
}

.models-empty {
  font-size: 0.75rem;
  color: var(--text-light);
  padding: 0.5rem 0;
  font-style: italic;
}

/* ── Inline forms ── */
.inline-form {
  padding: 0.75rem 1.5rem;
  border-top: 1px solid var(--border);
  background: var(--bg);
}

.form-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.form-input {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--text);
  border: 1px solid var(--border);
  padding: 0.4rem 0.75rem;
  background: #FFF;
  outline: none;
  flex: 1;
  min-width: 160px;
  transition: border-color 0.2s ease;
}

.form-input:focus { border-color: var(--text); }
.form-input::placeholder { color: var(--text-light); }

.form-select {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: var(--text);
  border: 1px solid var(--border);
  padding: 0.4rem 0.75rem;
  background: #FFF;
  outline: none;
  cursor: pointer;
}

/* ── Add provider ── */
.add-provider-section {
  padding: 0.5rem 0;
}

.btn-add-provider {
  background: none;
  border: 1px dashed var(--border);
  color: var(--text-light);
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  padding: 0.75rem 1.5rem;
  cursor: pointer;
  width: 100%;
  text-align: left;
  transition: all 0.2s ease;
}

.btn-add-provider:hover {
  border-color: var(--text-mid);
  color: var(--text-mid);
}

.new-provider-form {
  border: 1px dashed var(--border);
  border-top: none;
  padding: 1rem 1.5rem;
  background: #FFF;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* ── Error ── */
.admin-error {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.72rem;
  color: #D44A3A;
  border: 1px solid #D44A3A;
  padding: 0.75rem 1rem;
  background: rgba(212, 74, 58, 0.04);
}
</style>
