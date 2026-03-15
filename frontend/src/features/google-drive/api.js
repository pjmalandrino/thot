import { apiFetch } from '../../shared/api/http.js'

export const getDriveStatus = () => apiFetch('/api/drive/status')

export const getConnectUrl = () => apiFetch('/api/drive/connect')

export const disconnectDrive = () => apiFetch('/api/drive/disconnect', { method: 'POST' })
