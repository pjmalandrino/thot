import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useConversationStore } from './store.js'
import * as api from './api.js'

// Mock the API module
vi.mock('./api.js', () => ({
  fetchConversations: vi.fn(),
  fetchConversationsBySpace: vi.fn(),
  createConversation: vi.fn(),
  renameConversation: vi.fn(),
  deleteConversation: vi.fn()
}))

describe('useConversationStore', () => {
  let store

  beforeEach(() => {
    setActivePinia(createPinia())
    store = useConversationStore()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  // ── load ─────────────────────────────────────────────────────────────

  describe('load', () => {
    it('charge toutes les conversations sans filtre', async () => {
      const mockConvs = [
        { id: 1, title: 'Conv 1' },
        { id: 2, title: 'Conv 2' }
      ]
      api.fetchConversations.mockResolvedValue(mockConvs)

      await store.load()

      expect(api.fetchConversations).toHaveBeenCalled()
      expect(store.conversations).toEqual(mockConvs)
    })

    it('charge les conversations filtrees par thotspace', async () => {
      const mockConvs = [{ id: 1, title: 'Conv espace 5' }]
      api.fetchConversationsBySpace.mockResolvedValue(mockConvs)

      await store.load(5)

      expect(api.fetchConversationsBySpace).toHaveBeenCalledWith(5)
      expect(store.conversations).toEqual(mockConvs)
    })

    it('gere les erreurs sans planter', async () => {
      api.fetchConversations.mockRejectedValue(new Error('Network error'))
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

      await store.load()

      expect(spy).toHaveBeenCalledWith('Failed to load conversations', expect.any(Error))
    })
  })

  // ── create ───────────────────────────────────────────────────────────

  describe('create', () => {
    it('cree une conversation et la place en tete de liste', async () => {
      store.conversations = [{ id: 1, title: 'Existante' }]
      const newConv = { id: 2, title: 'Nouvelle conversation' }
      api.createConversation.mockResolvedValue(newConv)

      const result = await store.create(null)

      expect(result).toEqual(newConv)
      expect(store.conversations[0]).toEqual(newConv)
      expect(store.conversations).toHaveLength(2)
    })

    it('selectionne automatiquement la nouvelle conversation', async () => {
      const newConv = { id: 42, title: 'Nouvelle' }
      api.createConversation.mockResolvedValue(newConv)

      await store.create(null)

      expect(store.selectedId).toBe(42)
    })

    it('passe le thotspaceId a l\'API', async () => {
      api.createConversation.mockResolvedValue({ id: 1, title: 'test' })

      await store.create(7)

      expect(api.createConversation).toHaveBeenCalledWith(7)
    })
  })

  // ── remove ───────────────────────────────────────────────────────────

  describe('remove', () => {
    it('supprime la conversation de la liste', async () => {
      store.conversations = [
        { id: 1, title: 'A' },
        { id: 2, title: 'B' },
        { id: 3, title: 'C' }
      ]
      api.deleteConversation.mockResolvedValue(null)

      await store.remove(2)

      expect(store.conversations).toHaveLength(2)
      expect(store.conversations.find(c => c.id === 2)).toBeUndefined()
    })

    it('deselectionne si la conversation supprimee etait selectionnee', async () => {
      store.conversations = [{ id: 1, title: 'A' }]
      store.selectedId = 1
      api.deleteConversation.mockResolvedValue(null)

      await store.remove(1)

      expect(store.selectedId).toBeNull()
    })

    it('ne deselectionne pas si une autre conversation est selectionnee', async () => {
      store.conversations = [
        { id: 1, title: 'A' },
        { id: 2, title: 'B' }
      ]
      store.selectedId = 2
      api.deleteConversation.mockResolvedValue(null)

      await store.remove(1)

      expect(store.selectedId).toBe(2)
    })
  })

  // ── select ───────────────────────────────────────────────────────────

  describe('select', () => {
    it('met a jour selectedId', () => {
      store.select(42)
      expect(store.selectedId).toBe(42)
    })

    it('accepte null', () => {
      store.selectedId = 5
      store.select(null)
      expect(store.selectedId).toBeNull()
    })
  })

  // ── rename ───────────────────────────────────────────────────────────

  describe('rename', () => {
    it('fait un optimistic update puis confirme avec l\'API', async () => {
      store.conversations = [{ id: 1, title: 'Ancien titre' }]
      api.renameConversation.mockResolvedValue({ id: 1, title: 'Nouveau titre' })

      await store.rename(1, 'Nouveau titre')

      expect(store.conversations[0].title).toBe('Nouveau titre')
      expect(api.renameConversation).toHaveBeenCalledWith(1, 'Nouveau titre')
    })

    it('effectue un rollback en cas d\'erreur API', async () => {
      store.conversations = [{ id: 1, title: 'Titre original' }]
      api.renameConversation.mockRejectedValue(new Error('500'))
      const spy = vi.spyOn(console, 'error').mockImplementation(() => {})

      await store.rename(1, 'Titre qui va echouer')

      expect(store.conversations[0].title).toBe('Titre original')
    })

    it('ne plante pas si l\'id n\'existe pas dans la liste', async () => {
      store.conversations = [{ id: 1, title: 'A' }]
      api.renameConversation.mockResolvedValue({ id: 999, title: 'X' })

      // Should not throw
      await store.rename(999, 'X')

      expect(store.conversations[0].title).toBe('A')
    })
  })
})
