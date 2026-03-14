import { vi } from 'vitest'

// ── Mock global : Keycloak ──────────────────────────────────────────────
// Tous les tests importent indirectement keycloak via http.js / api.js.
// On mock le module entier pour eviter toute tentative de connexion reelle.
vi.mock('../shared/auth/keycloak.js', () => ({
  default: {},
  initKeycloak: vi.fn().mockResolvedValue(true),
  getToken: vi.fn().mockReturnValue('fake-jwt-token'),
  getUsername: vi.fn().mockReturnValue('testuser'),
  logout: vi.fn(),
  refreshToken: vi.fn().mockResolvedValue(undefined)
}))
