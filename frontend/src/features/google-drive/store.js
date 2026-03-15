import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDriveStatus, getConnectUrl, disconnectDrive } from './api.js'

export const useDriveStore = defineStore('drive', () => {
  const configured = ref(false)
  const connected = ref(false)
  const email = ref('')
  const loading = ref(false)
  const driveSearchEnabled = ref(false)

  async function checkStatus() {
    try {
      const data = await getDriveStatus()
      configured.value = data.configured ?? false
      connected.value = data.connected
      email.value = data.email || ''
    } catch {
      configured.value = false
      connected.value = false
      email.value = ''
    }
  }

  async function connect() {
    if (!configured.value) return
    loading.value = true
    try {
      const data = await getConnectUrl()
      if (data.redirectUrl) {
        window.location.href = data.redirectUrl
      }
    } catch (e) {
      console.error('[DRIVE] Connect failed:', e.message)
    } finally {
      loading.value = false
    }
  }

  async function disconnect() {
    loading.value = true
    try {
      await disconnectDrive()
      connected.value = false
      email.value = ''
      driveSearchEnabled.value = false
    } catch (e) {
      console.error('[DRIVE] Disconnect failed:', e.message)
    } finally {
      loading.value = false
    }
  }

  function toggleDriveSearch() {
    if (!connected.value) return
    driveSearchEnabled.value = !driveSearchEnabled.value
  }

  return {
    configured,
    connected,
    email,
    loading,
    driveSearchEnabled,
    checkStatus,
    connect,
    disconnect,
    toggleDriveSearch
  }
})
