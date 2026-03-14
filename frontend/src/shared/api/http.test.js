import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { apiFetch } from './http.js'

// Le mock de keycloak est deja configure dans setup.js
// On mock fetch globalement
const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

describe('apiFetch', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('injecte le token Bearer dans les headers', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: 'test' })
    })

    await apiFetch('/api/test')

    expect(mockFetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
      headers: expect.objectContaining({
        'Authorization': 'Bearer fake-jwt-token'
      })
    }))
  })

  it('ajoute Content-Type application/json par defaut', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({})
    })

    await apiFetch('/api/test')

    expect(mockFetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
      headers: expect.objectContaining({
        'Content-Type': 'application/json'
      })
    }))
  })

  it('n\'ajoute pas Content-Type quand skipContentType est true', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({})
    })

    await apiFetch('/api/upload', { skipContentType: true })

    const callHeaders = mockFetch.mock.calls[0][1].headers
    expect(callHeaders['Content-Type']).toBeUndefined()
  })

  it('retourne null pour les reponses 204', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 204
    })

    const result = await apiFetch('/api/delete', { method: 'DELETE' })

    expect(result).toBeNull()
  })

  it('parse le JSON des reponses 200', async () => {
    const responseData = { id: 1, title: 'Test' }
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(responseData)
    })

    const result = await apiFetch('/api/test')

    expect(result).toEqual(responseData)
  })

  it('lance une erreur pour les reponses non-ok', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500
    })

    await expect(apiFetch('/api/test')).rejects.toThrow('API error: 500')
  })

  it('appelle refreshToken avant chaque requete', async () => {
    const { refreshToken } = await import('../auth/keycloak.js')
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({})
    })

    await apiFetch('/api/test')

    expect(refreshToken).toHaveBeenCalledOnce()
  })

  it('lance une erreur si le reseau echoue', async () => {
    mockFetch.mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(apiFetch('/api/test')).rejects.toThrow('Failed to fetch')
  })

  it('transmet les options (method, body, etc.)', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({})
    })

    await apiFetch('/api/test', {
      method: 'POST',
      body: JSON.stringify({ key: 'value' })
    })

    expect(mockFetch).toHaveBeenCalledWith('/api/test', expect.objectContaining({
      method: 'POST',
      body: '{"key":"value"}'
    }))
  })
})
