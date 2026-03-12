import { createApp } from 'vue'
import App from './App.vue'
import { initKeycloak } from './keycloak.js'

initKeycloak().then((authenticated) => {
  if (authenticated) {
    createApp(App).mount('#app')
  }
}).catch((error) => {
  console.error('Keycloak init failed:', error)
})
