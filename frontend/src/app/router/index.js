import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'chat',
    component: () => import('../../pages/ChatPage.vue')
  },
  {
    path: '/thotspaces',
    name: 'thotspaces',
    component: () => import('../../pages/ThotspacePage.vue')
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
