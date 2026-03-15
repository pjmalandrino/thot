import { marked } from 'marked'

// ── Table tool: JSON → HTML ─────────────────────────────────────────────────

/**
 * Renders a structured table spec into an HTML <table>.
 * Expected JSON: { "columns": ["Col1", "Col2"], "rows": [["a", "b"], ["c", "d"]] }
 */
function renderTableFromSpec(json) {
  const spec = JSON.parse(json)

  if (!spec.columns || !Array.isArray(spec.columns)) {
    throw new Error('Format invalide : "columns" manquant')
  }
  if (!spec.rows || !Array.isArray(spec.rows)) {
    throw new Error('Format invalide : "rows" manquant')
  }

  const escHtml = s => String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  const ths = spec.columns.map(c => `<th>${escHtml(c)}</th>`).join('')
  const trs = spec.rows.map(row => {
    const tds = spec.columns.map((_, i) => `<td>${escHtml(row[i])}</td>`).join('')
    return `<tr>${tds}</tr>`
  }).join('')

  return `<div class="table-wrapper"><table><thead><tr>${ths}</tr></thead><tbody>${trs}</tbody></table></div>`
}

// ── Chart tool: JSON → placeholder div (rendered async by useChartRenderer) ─

function renderChartPlaceholder(json) {
  const id = 'chart-' + Math.random().toString(36).substring(2, 10)
  const escaped = json
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return `<div class="chart-placeholder" data-chart-id="${id}" data-chart-spec="${escaped}"><div class="chart-loading">Chargement du graphique…</div></div>`
}

// ── Custom renderer: dispatch tools ─────────────────────────────────────────

const renderer = {
  code({ text, lang }) {
    // Tool: interactive chart (async, Plotly.js)
    if (lang === 'chart' || lang === 'plotly') {
      return renderChartPlaceholder(text)
    }

    // Tool: structured table (sync, direct HTML)
    if (lang === 'table') {
      try {
        return renderTableFromSpec(text)
      } catch (err) {
        return `<div class="table-error-msg">Erreur de tableau : ${err.message}</div>`
      }
    }

    // All other code blocks → default marked renderer
    return false
  }
}

marked.use({ breaks: true, gfm: true, renderer })

// ── Public API ──────────────────────────────────────────────────────────────

/**
 * Render markdown text to HTML with:
 *  - ```chart  → interactive Plotly chart placeholder (async)
 *  - ```table  → structured HTML table from JSON (sync)
 *  - [N]       → clickable citation badges
 *
 * @param {string} text - Markdown text from LLM response
 * @param {Array}  sources - Optional citation sources array
 * @returns {string} HTML string ready for v-html
 */
export function renderMarkdown(text, sources) {
  if (!text) return ''
  let html = marked.parse(text)

  if (sources && sources.length) {
    const sourceMap = {}
    sources.forEach(s => { sourceMap[s.citationId] = s })

    html = html.replace(/\[(\d+)\]/g, (match, num) => {
      const cid = `[${num}]`
      const src = sourceMap[cid]
      if (src) {
        const title = (src.sourceTitle || '').replace(/"/g, '&quot;')
        const snippet = (src.extractedText || '').replace(/"/g, '&quot;').substring(0, 120)
        return `<a href="${src.sourceUrl}" target="_blank" rel="noopener" class="citation-inline" title="${title}${snippet ? ' — ' + snippet : ''}">${cid}</a>`
      }
      return match
    })
  }

  return html
}

// Exported for testing
export { renderTableFromSpec }
