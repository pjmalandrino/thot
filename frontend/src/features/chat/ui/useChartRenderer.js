import { onMounted, onUnmounted, nextTick, watch } from 'vue'

// ── Lazy-loaded Plotly singleton ────────────────────────────────────────────

let plotlyPromise = null

function loadPlotly() {
  if (!plotlyPromise) {
    plotlyPromise = import('plotly.js-dist-min')
  }
  return plotlyPromise
}

// ── HTML entity decoding ────────────────────────────────────────────────────

function unescapeHtml(str) {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
}

// ── Spec normalization ──────────────────────────────────────────────────────

/**
 * Normalize a chart spec to full Plotly { data, layout } format.
 * Accepts both:
 *   - Full Plotly:  { data: [...], layout: {...} }
 *   - Simplified:   { type: "bar", data: { x, y, ... }, layout: {...} }
 */
function normalizeSpec(spec) {
  if (spec.data && Array.isArray(spec.data)) {
    return { data: spec.data, layout: spec.layout || {} }
  }

  if (spec.type && spec.data) {
    const trace = {
      type: spec.type,
      ...spec.data
    }
    return { data: [trace], layout: spec.layout || {} }
  }

  throw new Error('Format invalide : attendu { data, layout } ou { type, data, layout }')
}

// ── THOT design defaults ────────────────────────────────────────────────────

const THOT_LAYOUT = {
  font: { family: 'Inter, sans-serif', color: '#141210' },
  paper_bgcolor: 'transparent',
  plot_bgcolor: 'transparent',
  margin: { t: 40, r: 20, b: 40, l: 50 }
}

const THOT_CONFIG = {
  responsive: true,
  displaylogo: false,
  modeBarButtonsToRemove: ['sendDataToCloud', 'lasso2d', 'select2d']
}

// ── Composable ──────────────────────────────────────────────────────────────

/**
 * Scans a container ref for chart placeholders injected by markdown.js
 * and renders them with Plotly.js (lazy-loaded on first encounter).
 *
 * @param {Ref<HTMLElement>} containerRef - ref to the messages container
 * @param {Ref<Array>}       interactions - reactive interactions list
 */
export function useChartRenderer(containerRef, interactions) {
  let observer = null

  async function renderCharts() {
    const container = containerRef.value
    if (!container) return

    const placeholders = container.querySelectorAll('.chart-placeholder:not(.chart-rendered)')
    if (placeholders.length === 0) return

    const Plotly = await loadPlotly()

    for (const el of placeholders) {
      const specAttr = el.getAttribute('data-chart-spec')
      if (!specAttr) continue

      try {
        const rawJson = unescapeHtml(specAttr)
        const spec = JSON.parse(rawJson)
        const { data, layout } = normalizeSpec(spec)

        // Merge THOT defaults with spec layout (spec wins on conflicts)
        const mergedLayout = { ...THOT_LAYOUT, ...layout }

        // Clear loading indicator and render
        el.innerHTML = ''
        el.classList.add('chart-rendered')
        await Plotly.default.newPlot(el, data, mergedLayout, THOT_CONFIG)
      } catch (err) {
        el.classList.add('chart-rendered', 'chart-error')
        el.innerHTML = `<div class="chart-error-msg">Erreur de graphique : ${err.message}</div>`
        console.warn('[THOT] Chart render error:', err)
      }
    }
  }

  onMounted(() => {
    // MutationObserver detects when v-html injects new content
    if (containerRef.value) {
      observer = new MutationObserver(() => {
        nextTick(renderCharts)
      })
      observer.observe(containerRef.value, { childList: true, subtree: true })
    }
  })

  // Also re-scan when interactions change (new messages arrive)
  watch(interactions, () => {
    nextTick(renderCharts)
  }, { deep: true })

  onUnmounted(() => {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  })

  return { renderCharts }
}
