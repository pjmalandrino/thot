<template>
  <div id="app">
    <header>
      <h1>Chat Interface</h1>
      <div class="user-info">
        <span>Logged in as <strong>{{ username }}</strong></span>
        <button @click="handleLogout">Logout</button>
      </div>
    </header>
    <main>
      <HelloWorld />
    </main>
  </div>
</template>

<script setup>
import { getUsername, logout } from './keycloak.js'
import HelloWorld from './components/HelloWorld.vue'

const username = getUsername()

function handleLogout() {
  logout()
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f5f5f5;
  color: #333;
}

#app {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #ddd;
  margin-bottom: 24px;
}

header h1 {
  font-size: 1.5rem;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info button {
  padding: 6px 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: white;
  cursor: pointer;
}

.user-info button:hover {
  background: #eee;
}
</style>
