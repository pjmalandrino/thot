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
        class="source-card"
      >
        <span class="source-id">{{ src.citationId }}</span>
        <div class="source-body">
          <div class="source-top">
            <img
              class="source-favicon"
              :src="faviconUrl(src.sourceUrl)"
              :alt="formatUrl(src.sourceUrl)"
              loading="lazy"
              @error="$event.target.style.display='none'"
            />
            <span class="source-domain">{{ formatUrl(src.sourceUrl) }}</span>
          </div>
          <span class="source-name">{{ src.sourceTitle || 'Sans titre' }}</span>
        </div>
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

function faviconUrl(url) {
  if (!url) return ''
  try {
    const domain = new URL(url).hostname
    return `https://www.google.com/s2/favicons?domain=${domain}&sz=16`
  } catch {
    return ''
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 0.35rem;
}

.source-card {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  text-decoration: none;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border);
  background: #FFF;
  transition: all 0.2s ease;
}

.source-card:hover {
  border-color: var(--accent);
  background: rgba(212, 164, 56, 0.03);
}

.source-id {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.65rem;
  font-weight: 600;
  color: var(--accent);
  flex-shrink: 0;
  padding: 0.1rem 0.35rem;
  background: rgba(212, 164, 56, 0.08);
  line-height: 1;
}

.source-body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.source-top {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.source-favicon {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
  opacity: 0.7;
}

.source-domain {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 0.55rem;
  color: var(--text-light);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.source-name {
  font-size: 0.72rem;
  color: var(--text-mid);
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s ease;
}

.source-card:hover .source-name {
  color: var(--text);
}
</style>
