import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import cpp from 'highlight.js/lib/languages/cpp'
import c from 'highlight.js/lib/languages/c'
import java from 'highlight.js/lib/languages/java'
import css from 'highlight.js/lib/languages/css'
import xml from 'highlight.js/lib/languages/xml'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('c', c)
hljs.registerLanguage('java', java)
hljs.registerLanguage('css', css)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('markdown', markdown)

export interface MarkdownOptions {
  prefix: string
  copyButton?: boolean
  tables?: boolean
  blockquote?: boolean
  strikethrough?: boolean
  hr?: boolean
  paragraphs?: boolean
}

const COPY_BTN_SVG = `<svg class="copy-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><svg class="copied-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="display:none"><polyline points="20 6 9 17 4 12"></polyline></svg><span class="copy-text">复制</span>`

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function highlightCode(code: string, language?: string): string {
  if (!language || !hljs.getLanguage(language)) return hljs.highlightAuto(code).value
  try { return hljs.highlight(code, { language }).value }
  catch { return hljs.highlightAuto(code).value }
}

function buildCodeBlockHtml(code: string, language: string, highlighted: string, opts: MarkdownOptions): string {
  const escapedCode = escapeHtml(code).replace(/`/g, '&#96;')
  if (opts.copyButton) {
    return `<div class="code-block-wrapper" data-code="${escapedCode}"><div class="code-block-header"><span class="code-block-lang">${language}</span><button class="code-copy-btn" title="复制代码">${COPY_BTN_SVG}</button></div><pre class="code-block-pre"><code class="hljs">${highlighted}</code></pre></div>`
  }
  return `<div class="${opts.prefix}code-block"><div class="${opts.prefix}code-header"><span class="${opts.prefix}code-lang">${language || 'code'}</span></div><pre class="${opts.prefix}code-pre"><code>${highlighted}</code></pre></div>`
}

function renderCodeBlocks(s: string, opts: MarkdownOptions): string {
  const codeBlockRegex = /```(\w*)\s*\n([\s\S]*?)```/g
  const incompleteRegex = /```(\w*)\s*\n([\s\S]*)$/
  let match, result = '', lastIndex = 0

  while ((match = codeBlockRegex.exec(s)) !== null) {
    result += escapeHtml(s.substring(lastIndex, match.index))
    const lang = match[1] || ''
    const code = match[2].replace(/^[\n]|[\n]$/g, '')
    result += buildCodeBlockHtml(code, lang, highlightCode(code, lang), opts)
    lastIndex = match.index + match[0].length
  }

  const remaining = s.substring(lastIndex)
  const incompleteMatch = incompleteRegex.exec(remaining)
  if (incompleteMatch) {
    result += escapeHtml(remaining.substring(0, incompleteMatch.index))
    const lang = incompleteMatch[1] || ''
    result += buildCodeBlockHtml(incompleteMatch[2], lang, highlightCode(incompleteMatch[2], lang), opts)
  } else {
    result += escapeHtml(remaining)
  }
  return result
}

function renderInlineCode(s: string, opts: MarkdownOptions): string {
  return s.replace(/`([^`\n]+)`/g, `<code class="${opts.prefix}inline-code">$1</code>`)
}

function renderBold(s: string, opts: MarkdownOptions): string {
  let r = s.replace(/\*\*(.+?)\*\*/g, `<strong class="${opts.prefix}bold">$1</strong>`)
  r = r.replace(/__(.+?)__/g, `<strong class="${opts.prefix}bold">$1</strong>`)
  return r
}

function renderItalic(s: string, opts: MarkdownOptions): string {
  return s.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, `<em class="${opts.prefix}italic">$1</em>`)
}

function renderStrikethrough(s: string): string {
  return s.replace(/~~(.+?)~~/g, '<del>$1</del>')
}

function renderLinks(s: string, opts: MarkdownOptions): string {
  return s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, `<a href="$2" target="_blank" rel="noopener" class="${opts.prefix}link">$1</a>`)
    .replace(/(?!\()\bhttps?:\/\/[^\s<>\]]+/gi, `<a href="$&" target="_blank" rel="noopener" class="${opts.prefix}link">$&</a>`)
}

function renderHeaders(s: string, opts: MarkdownOptions): string {
  return s
    .replace(/^######\s+(.+)$/gm, '<h6>$1</h6>')
    .replace(/^#####\s+(.+)$/gm, '<h5>$1</h5>')
    .replace(/^####\s+(.+)$/gm, `<h4 class="${opts.prefix}h4">$1</h4>`)
    .replace(/^###\s+(.+)$/gm, `<h3 class="${opts.prefix}h3">$1</h3>`)
    .replace(/^##\s+(.+)$/gm, `<h2 class="${opts.prefix}h2">$1</h2>`)
    .replace(/^#\s+(.+)$/gm, `<h1 class="${opts.prefix}h1">$1</h1>`)
}

function renderBlockquotes(s: string, opts: MarkdownOptions): string {
  const lines = s.split('\n')
  let inBq = false, r: string[] = []
  for (const line of lines) {
    if (line.startsWith('> ')) {
      if (!inBq) { r.push(`<blockquote class="${opts.prefix}blockquote">`); inBq = true }
      r.push(line.substring(2))
    } else {
      if (inBq) { r.push('</blockquote>'); inBq = false }
      r.push(line)
    }
  }
  if (inBq) r.push('</blockquote>')
  return r.join('\n')
}

function renderLists(s: string, opts: MarkdownOptions): string {
  const lines = s.split('\n'), out: string[] = []
  let listOpen = false, listType = ''
  for (const line of lines) {
    const ul = line.match(/^[-*]\s+(.*)$/)
    const ol = line.match(/^\d+\.\s+(.*)$/)
    if (ul) {
      if (listOpen && listType !== 'ul') { out.push(listType === 'ol' ? '</ol>' : '</ul>'); listOpen = false; listType = '' }
      if (!listOpen || listType !== 'ul') { out.push(`<ul class="${opts.prefix}list">`); listOpen = true; listType = 'ul' }
      out.push(`<li>${ul[1]}</li>`)
    } else if (ol) {
      if (listOpen && listType !== 'ol') { out.push(listType === 'ul' ? '</ul>' : '</ol>'); listOpen = false; listType = '' }
      if (!listOpen || listType !== 'ol') { out.push(`<ol class="${opts.prefix}list-ordered">`); listOpen = true; listType = 'ol' }
      out.push(`<li>${ol[1]}</li>`)
    } else {
      if (listOpen) { out.push(listType === 'ul' ? '</ul>' : '</ol>'); listOpen = false; listType = '' }
      out.push(line)
    }
  }
  if (listOpen) out.push(listType === 'ul' ? '</ul>' : '</ol>')
  return out.join('\n')
}

function renderHorizontalRule(s: string, opts: MarkdownOptions): string {
  return s.replace(/^(-{3,}|\*{3,}|_{3,})$/gm, `<hr class="${opts.prefix}hr">`)
}

function renderTables(s: string, opts: MarkdownOptions): string {
  const lines = s.split('\n'), result: string[] = []
  let inTable = false, tableHtml = ''
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()
    if (line.startsWith('|') && line.endsWith('|')) {
      const cells = line.split('|').slice(1, -1).map(c => c.trim())
      if (!inTable) {
        inTable = true; tableHtml = `<table class="${opts.prefix}table"><thead><tr>`
        cells.forEach(c => { tableHtml += `<th>${c}</th>` })
        tableHtml += '</tr></thead><tbody>'
      } else if (cells.every(c => /^[-:]+$/.test(c))) continue
      else { tableHtml += '<tr>'; cells.forEach(c => { tableHtml += `<td>${c}</td>` }); tableHtml += '</tr>' }
    } else {
      if (inTable) { tableHtml += '</tbody></table>'; result.push(tableHtml); tableHtml = ''; inTable = false }
      result.push(lines[i])
    }
  }
  if (inTable) { tableHtml += '</tbody></table>'; result.push(tableHtml) }
  return result.join('\n')
}

function renderParagraphs(html: string, opts: MarkdownOptions): string {
  return html.split(/\n{2,}/).map(block => {
    const t = block.trim()
    if (!t) return ''
    if (/^<(ul|ol|li|h[1-6]|div|pre)/.test(t)) return t
    return `<p class="${opts.prefix}p">${t}</p>`
  }).join('\n')
}

export function renderMarkdown(text: string, opts: MarkdownOptions): string {
  if (!text) return ''
  let html = text
  html = renderCodeBlocks(html, opts)
  html = renderInlineCode(html, opts)
  html = renderBold(html, opts)
  html = renderItalic(html, opts)
  html = renderLists(html, opts)
  html = renderHeaders(html, opts)
  html = renderLinks(html, opts)
  if (opts.tables) html = renderTables(html, opts)
  if (opts.blockquote) html = renderBlockquotes(html, opts)
  if (opts.strikethrough) html = renderStrikethrough(html)
  if (opts.hr) html = renderHorizontalRule(html, opts)
  if (opts.paragraphs !== false) html = renderParagraphs(html, opts)
  return html
}

export const mdToHtml = (text: string) => renderMarkdown(text, {
  prefix: 'markdown-', copyButton: true, tables: true,
  blockquote: true, strikethrough: true, hr: true, paragraphs: false
})

export const renderThinkingMarkdown = (text: string) => renderMarkdown(text, {
  prefix: 'thinking-'
})
