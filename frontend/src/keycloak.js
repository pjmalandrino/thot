import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: 'http://localhost:8080',
  realm: 'chat-app',
  clientId: 'chat-frontend'
})

export async function initKeycloak() {
  const authenticated = await keycloak.init({
    onLoad: 'login-required',
    checkLoginIframe: false
  })
  return authenticated
}

export function getToken() {
  return keycloak.token
}

export function getUsername() {
  return keycloak.tokenParsed?.preferred_username || 'unknown'
}

export function logout() {
  keycloak.logout({ redirectUri: 'http://localhost:3000' })
}

export async function refreshToken() {
  try {
    await keycloak.updateToken(30)
  } catch {
    keycloak.login()
  }
}

export default keycloak
