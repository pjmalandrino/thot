import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useDocumentStore } from './store.js'
import * as api from './api.js'

vi.mock('./api.js', () => ({
  fetchDocuments: vi.fn(),
  uploadDocument: vi.fn(),
  deleteDocument: vi.fn()
}))

describe('useDocumentStore', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useDocumentStore()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  // ── load ─────────────────────────────────────────────────────────────

  describe('load', () => {
    it('charge les documents d\'une conversation', async () => {
      const mockDocs = [
        { id: 1, filename: 'rapport.pdf' },
        { id: 2, filename: 'notes.txt' }
      ]
      api.fetchDocuments.mockResolvedValue(mockDocs)

      await store.load(10)

      expect(api.fetchDocuments).toHaveBeenCalledWith(10)
      expect(store.documents).toEqual(mockDocs)
    })

    it('gere les erreurs sans planter', async () => {
      api.fetchDocuments.mockRejectedValue(new Error('Network error'))
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

      await store.load(10)

      expect(spy).toHaveBeenCalledWith('Failed to load documents', expect.any(Error))
    })
  })

  // ── upload ───────────────────────────────────────────────────────────

  describe('upload', () => {
    it('upload un document et l\'ajoute a la liste', async () => {
      const mockDoc = { id: 1, filename: 'test.pdf' }
      api.uploadDocument.mockResolvedValue(mockDoc)
      const file = new File(['content'], 'test.pdf')

      const result = await store.upload(10, file)

      expect(result).toEqual(mockDoc)
      expect(store.documents).toContainEqual(mockDoc)
      expect(api.uploadDocument).toHaveBeenCalledWith(10, file)
    })

    it('met uploading a true pendant l\'upload', async () => {
      let resolvePromise
      api.uploadDocument.mockReturnValue(new Promise(resolve => {
        resolvePromise = resolve
      }))
      const file = new File(['content'], 'test.pdf')

      const uploadPromise = store.upload(10, file)
      expect(store.uploading).toBe(true)

      resolvePromise({ id: 1, filename: 'test.pdf' })
      await uploadPromise

      expect(store.uploading).toBe(false)
    })

    it('remet uploading a false en cas d\'erreur et propage l\'exception', async () => {
      api.uploadDocument.mockRejectedValue(new Error('Upload failed'))
      const file = new File(['content'], 'test.pdf')
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

      await expect(store.upload(10, file)).rejects.toThrow('Upload failed')
      expect(store.uploading).toBe(false)
    })
  })

  // ── remove ───────────────────────────────────────────────────────────

  describe('remove', () => {
    it('supprime le document de la liste', async () => {
      store.documents = [
        { id: 1, filename: 'a.pdf' },
        { id: 2, filename: 'b.pdf' }
      ]
      api.deleteDocument.mockResolvedValue(null)

      await store.remove(10, 1)

      expect(store.documents).toHaveLength(1)
      expect(store.documents[0].id).toBe(2)
      expect(api.deleteDocument).toHaveBeenCalledWith(10, 1)
    })

    it('gere les erreurs sans planter', async () => {
      store.documents = [{ id: 1, filename: 'a.pdf' }]
      api.deleteDocument.mockRejectedValue(new Error('Delete failed'))
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

      await store.remove(10, 1)

      // La liste ne devrait pas changer en cas d'erreur
      expect(store.documents).toHaveLength(1)
    })
  })

  // ── clear ────────────────────────────────────────────────────────────

  describe('clear', () => {
    it('vide la liste des documents', () => {
      store.documents = [
        { id: 1, filename: 'a.pdf' },
        { id: 2, filename: 'b.pdf' }
      ]

      store.clear()

      expect(store.documents).toEqual([])
    })
  })
})
