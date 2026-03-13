import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { router } from './router/index.js'
import App from './App.vue'
import { initKeycloak } from '../shared/auth/keycloak.js'

initKeycloak().then((authenticated) => {
  if (authenticated) {
    const app = createApp(App)
    app.use(createPinia())
    app.use(router)
    app.mount('#app')
  }
}).catch((error) => {
  console.error('Keycloak init failed:', error)
})
