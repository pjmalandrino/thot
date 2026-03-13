import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'chat',
    component: () => import('../../pages/ChatPage.vue')
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../../pages/AdminPage.vue')
  }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})
