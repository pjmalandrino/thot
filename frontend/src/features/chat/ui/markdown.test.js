import { describe, it, expect } from 'vitest'
import { renderMarkdown, renderTableFromSpec } from './markdown.js'

describe('renderMarkdown', () => {
  it('rend le markdown basique en HTML', () => {
    const html = renderMarkdown('**bold** text')
    expect(html).toContain('<strong>bold</strong>')
  })

  it('retourne une chaine vide pour un texte null ou vide', () => {
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })

  // ── Tool: table ──────────────────────────────────────────────────────────

  it('rend un bloc table JSON en HTML <table>', () => {
    const md = '```table\n{"columns":["Nom","Age"],"rows":[["Alice","30"],["Bob","25"]]}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('<table>')
    expect(html).toContain('<thead>')
    expect(html).toContain('<th>Nom</th>')
    expect(html).toContain('<th>Age</th>')
    expect(html).toContain('<td>Alice</td>')
    expect(html).toContain('<td>30</td>')
    expect(html).toContain('<td>Bob</td>')
    expect(html).not.toContain('<code')
  })

  it('affiche une erreur pour un JSON table invalide', () => {
    const md = '```table\n{invalid json}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('table-error-msg')
    expect(html).toContain('Erreur')
  })

  it('affiche une erreur si columns manquant dans le JSON table', () => {
    const md = '```table\n{"rows":[["a"]]}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('table-error-msg')
    expect(html).toContain('columns')
  })

  it('echappe le HTML dans les valeurs de la table', () => {
    const md = '```table\n{"columns":["Test"],"rows":[["<script>alert(1)</script>"]]}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('<script>')
  })

  // ── GFM tables still work (legacy) ───────────────────────────────────────

  it('rend une table GFM Markdown en HTML (retrocompatibilite)', () => {
    const md = '| Col1 | Col2 |\n|------|------|\n| A    | B    |'
    const html = renderMarkdown(md)
    expect(html).toContain('<table>')
    expect(html).toContain('<th>')
    expect(html).toContain('<td>')
  })

  // ── Tool: chart ──────────────────────────────────────────────────────────

  it('remplace les blocs code chart par un placeholder', () => {
    const md = '```chart\n{"type":"bar","data":{"x":["A"],"y":[1]}}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('class="chart-placeholder"')
    expect(html).toContain('data-chart-spec')
    expect(html).toContain('data-chart-id')
    expect(html).not.toContain('<code')
  })

  it('accepte plotly comme alias de langage', () => {
    const md = '```plotly\n{"data":[{"type":"scatter","x":[1],"y":[2]}]}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('class="chart-placeholder"')
  })

  it('affiche un indicateur de chargement dans le placeholder', () => {
    const md = '```chart\n{"data":[]}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('chart-loading')
    expect(html).toContain('Chargement')
  })

  it('echappe correctement les specs JSON dans les attributs HTML', () => {
    const md = '```chart\n{"layout":{"title":"Sales & Revenue <2024>"}}\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('&amp;')
    expect(html).toContain('&lt;')
    expect(html).toContain('&gt;')
    const specMatch = html.match(/data-chart-spec="([^"]*)"/)
    expect(specMatch).not.toBeNull()
    const specValue = specMatch[1]
    expect(specValue).not.toContain('<')
    expect(specValue).not.toContain('>')
  })

  // ── Normal code blocks ─────────────────────────────────────────────────────

  it('laisse les blocs code normaux intacts', () => {
    const md = '```javascript\nconsole.log("hello")\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('<code')
    expect(html).not.toContain('chart-placeholder')
    expect(html).not.toContain('<table>')
  })

  it('laisse les blocs code sans langage intacts', () => {
    const md = '```\nplain code\n```'
    const html = renderMarkdown(md)
    expect(html).toContain('<code')
    expect(html).not.toContain('chart-placeholder')
  })

  // ── Citations ──────────────────────────────────────────────────────────────

  it('remplace les citations [N] avec des badges cliquables', () => {
    const sources = [
      { citationId: '[1]', sourceUrl: 'https://example.com', sourceTitle: 'Test', extractedText: 'Some text' }
    ]
    const html = renderMarkdown('Selon [1] la reponse.', sources)
    expect(html).toContain('citation-inline')
    expect(html).toContain('https://example.com')
    expect(html).toContain('[1]')
  })

  it('ne touche pas aux citations sans source correspondante', () => {
    const sources = [
      { citationId: '[1]', sourceUrl: 'https://example.com', sourceTitle: 'Test' }
    ]
    const html = renderMarkdown('Voir [1] et [2].', sources)
    expect(html).toContain('citation-inline')
    expect(html).toContain('[2]')
    expect(html).not.toContain('class="citation-inline" title="">[2]')
  })

  it('fonctionne sans sources', () => {
    const html = renderMarkdown('Hello [1] world')
    expect(html).toContain('[1]')
    expect(html).not.toContain('citation-inline')
  })
})

// ── renderTableFromSpec (unit) ──────────────────────────────────────────────

describe('renderTableFromSpec', () => {
  it('genere un tableau HTML valide', () => {
    const html = renderTableFromSpec('{"columns":["A","B"],"rows":[["1","2"]]}')
    expect(html).toContain('<table>')
    expect(html).toContain('<thead>')
    expect(html).toContain('<tbody>')
    expect(html).toContain('<th>A</th>')
    expect(html).toContain('<td>1</td>')
  })

  it('gere les cellules null/undefined', () => {
    const html = renderTableFromSpec('{"columns":["X"],"rows":[[null]]}')
    expect(html).toContain('<td></td>')
  })

  it('leve une erreur si columns est absent', () => {
    expect(() => renderTableFromSpec('{"rows":[]}')).toThrow('columns')
  })

  it('leve une erreur si rows est absent', () => {
    expect(() => renderTableFromSpec('{"columns":["A"]}')).toThrow('rows')
  })
})
