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

// ── Deep merge utility ─────────────────────────────────────────────────────

/**
 * Deep merge two objects. `target` provides defaults, `source` overrides.
 * Arrays and non-plain-objects are replaced, not merged.
 */
function deepMerge(target, source) {
  const result = { ...target }
  for (const key of Object.keys(source)) {
    if (
      source[key] && typeof source[key] === 'object' && !Array.isArray(source[key]) &&
      target[key] && typeof target[key] === 'object' && !Array.isArray(target[key])
    ) {
      result[key] = deepMerge(target[key], source[key])
    } else {
      result[key] = source[key]
    }
  }
  return result
}

// ── Spec normalization ──────────────────────────────────────────────────────

/**
 * Normalize a chart spec to full Plotly { data, layout } format.
 * Accepts both:
 *   - Full Plotly:  { data: [...], layout: {...} }
 *   - Simplified:   { type: "bar", data: { x, y, ... }, layout: {...} }
 */
function normalizeSpec(spec) {
  let data, layout

  if (spec.data && Array.isArray(spec.data)) {
    data = spec.data
    layout = spec.layout || {}
  } else if (spec.type && spec.data) {
    data = [{ type: spec.type, ...spec.data }]
    layout = spec.layout || {}
  } else {
    throw new Error('Format invalide : attendu { data, layout } ou { type, data, layout }')
  }

  // ── Clean up generic trace names ──────────────────────────────────────
  const genericNamePattern = /^trace\s*\d+$/i
  let hasExplicitNames = false

  for (const trace of data) {
    if (trace.name && !genericNamePattern.test(trace.name)) {
      hasExplicitNames = true
    }
    // Remove generic "trace 0", "trace 1" names
    if (trace.name && genericNamePattern.test(trace.name)) {
      delete trace.name
    }
  }

  // If single trace with no meaningful name → hide legend
  if (data.length === 1 && !hasExplicitNames && layout.showlegend === undefined) {
    layout.showlegend = false
  }

  // ── Apply THOT colors to traces without explicit colors ───────────────
  for (let i = 0; i < data.length; i++) {
    const trace = data[i]
    const color = THOT_PALETTE[i % THOT_PALETTE.length]

    if (trace.type === 'pie') {
      // Pie charts use marker.colors (array), only set if not provided
      if (!trace.marker?.colors) {
        trace.marker = { ...trace.marker, colors: THOT_PALETTE.slice(0, (trace.labels || trace.values || []).length || THOT_PALETTE.length) }
      }
    } else if (trace.type === 'heatmap') {
      // Heatmaps use colorscale — apply THOT-inspired scale if not set
      if (!trace.colorscale) {
        trace.colorscale = [
          [0, '#F5F0E8'],
          [0.5, '#D4A438'],
          [1, '#141210']
        ]
      }
    } else {
      // Bar, scatter, line, histogram — apply marker/line color if not set
      if (!trace.marker?.color && !trace.line?.color) {
        if (trace.type === 'scatter' || trace.type === 'line' ||
            (trace.mode && trace.mode.includes('lines'))) {
          trace.line = { ...trace.line, color, width: 2 }
          trace.marker = { ...trace.marker, color, size: 6 }
        } else {
          trace.marker = { ...trace.marker, color }
        }
      }
    }
  }

  return { data, layout }
}

// ── THOT design system ──────────────────────────────────────────────────────

const THOT_PALETTE = [
  '#D4A438', // amber (brand)
  '#3DCAAD', // teal (accent pop)
  '#8B5CF6', // violet
  '#E85D4A', // coral
  '#6366F1', // indigo
  '#10B981', // emerald
  '#F59E0B', // warm amber
  '#64748B', // slate
]

const THOT_LAYOUT = {
  font: { family: 'Inter, sans-serif', size: 12, color: '#4A4740' },
  paper_bgcolor: 'transparent',
  plot_bgcolor: 'transparent',
  margin: { t: 48, r: 24, b: 56, l: 56 },
  title: {
    font: { family: 'Inter, sans-serif', size: 14, color: '#141210' },
    x: 0,
    xanchor: 'left',
    pad: { l: 8 },
  },
  xaxis: {
    gridcolor: 'rgba(0,0,0,0.05)',
    linecolor: 'rgba(0,0,0,0.08)',
    tickfont: { family: "'IBM Plex Mono', monospace", size: 10, color: '#8C877D' },
    title: { font: { family: 'Inter, sans-serif', size: 11, color: '#8C877D' } },
    zeroline: false,
    showgrid: true,
  },
  yaxis: {
    gridcolor: 'rgba(0,0,0,0.05)',
    linecolor: 'rgba(0,0,0,0.08)',
    tickfont: { family: "'IBM Plex Mono', monospace", size: 10, color: '#8C877D' },
    title: { font: { family: 'Inter, sans-serif', size: 11, color: '#8C877D' } },
    zeroline: false,
    showgrid: true,
  },
  legend: {
    font: { family: "'IBM Plex Mono', monospace", size: 10, color: '#4A4740' },
    bgcolor: 'transparent',
    orientation: 'h',
    y: -0.18,
    x: 0.5,
    xanchor: 'center',
  },
  colorway: THOT_PALETTE,
  hoverlabel: {
    bgcolor: '#141210',
    font: { family: 'Inter, sans-serif', size: 12, color: '#FFFFFF' },
    bordercolor: 'transparent',
  },
  bargap: 0.3,
  bargroupgap: 0.08,
}

const THOT_CONFIG = {
  responsive: true,
  displaylogo: false,
  displayModeBar: false,
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

        // Deep merge THOT defaults with spec layout (spec wins on conflicts)
        const mergedLayout = deepMerge(THOT_LAYOUT, layout)

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
