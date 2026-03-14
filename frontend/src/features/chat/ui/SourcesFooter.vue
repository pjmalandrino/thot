<template>
  <div class="sources-footer" v-if="sources && sources.length">
    <div class="sources-divider"></div>
    <div class="sources-title">Sources</div>
    <div class="sources-list">
      <a
        v-for="src in sources"
        :key="src.citationId"
        :href="src.sourceUrl"
        target="_blank"
        rel="noopener"
        class="source-item"
      >
        <span class="source-id">{{ src.citationId }}</span>
        <span class="source-domain">{{ formatUrl(src.sourceUrl) }}</span>
        <span class="source-name">{{ src.sourceTitle || 'Sans titre' }}</span>
      </a>
    </div>
  </div>
</template>

<script setup>
defineProps({
  sources: { type: Array, default: () => [] }
})

function formatUrl(url) {
  if (!url) return ''
  try {
    return new URL(url).hostname.replace('www.', '')
  } catch {
    return url
  }
}
</script>

<style scoped>
.sources-footer {
  margin-top: 1.25rem;
}

.sources-divider {
  border-top: 1px solid var(--border);
  margin-bottom: 0.75rem;
}

.sources-title {
  font-size: 0.6rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: var(--text-light);
  margin-bottom: 0.5rem;
}

.sources-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.source-item {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  text-decoration: none;
  padding: 0.3rem 0;
  transition: color 0.2s ease;
}

.source-item:hover .source-name {
  color: var(--accent, #D4A438);
}

.source-id {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--accent, #D4A438);
  flex-shrink: 0;
}

.source-domain {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
  flex-shrink: 0;
}

.source-name {
  font-size: 0.72rem;
  color: var(--text-mid);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s ease;
}
</style>
