<script setup lang="ts">
import AttachCard from './AttachCard.vue'
import { Library } from 'lucide-vue-next'
import { computed, ref, onMounted, nextTick, watch } from 'vue'
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
import 'highlight.js/styles/vs2015.css'

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

interface Props {
  role: 'user' | 'assistant'
  author: string
  time: string
  thinkTime?: string
  content: string
  isLoading?: boolean
  thinkingContent?: string
  attachments?: Array<{
    type: 'image' | 'video' | 'audio' | 'code' | 'file'
    tag?: string
    title?: string
    subtitle?: string
    meta?: string
    codeContent?: string
    src?: string | null
    fileName?: string
    fileSize?: string
    fileType?: string
  }>
  sources?: Array<{
    chunk_id: number | null
    content_snippet: string
    document_name: string
    page_number: number | null
    score: number
  }>
}

const props = defineProps<Props>()

const thinkingExpanded = ref(true)
const hasThinking = computed(() => !!props.thinkingContent?.trim())
const thinkingDisplayContent = computed(() => props.thinkingContent?.trim() || '')
const renderedThinkingContent = computed(() => {
  const content = thinkingDisplayContent.value
  if (!content) return ''
  
  let html = content
  
  html = renderThinkingMarkdown(html)
  
  return html
})
const thinkingParagraphs = computed(() => {
  const content = thinkingDisplayContent.value
  if (!content) return []
  return content.split(/\n{1,}/).filter(p => p.trim())
})
const hasContent = () => !!props.content?.trim()
const hasAnyResponse = () => !!props.thinkingContent?.trim() || !!props.content?.trim()
const hasAttachments = () => (props.attachments?.length ?? 0) > 0

function normalizeContent(text: string): string {
  if (!text) return ''
  return text
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/[ \t]+$/gm, '')
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

function highlightCode(code: string, language?: string): string {
  if (!language || !hljs.getLanguage(language)) {
    return hljs.highlightAuto(code).value
  }
  try {
    return hljs.highlight(code, { language }).value
  } catch {
    return hljs.highlightAuto(code).value
  }
}

function renderCodeBlocks(s: string): string {
  console.log('[renderCodeBlocks] Input length:', s.length)
  const codeBlockRegex = /```(\w*)\s*\n([\s\S]*?)```/g
  const incompleteCodeBlockRegex = /```(\w*)\s*\n([\s\S]*)$/  // 匹配不完整的代码块
  let match
  let result = ''
  let lastIndex = 0
  let codeBlockCount = 0

  while ((match = codeBlockRegex.exec(s)) !== null) {
    codeBlockCount++
    const before = s.substring(lastIndex, match.index)
    result += escapeHtml(before)

    const language = match[1] || ''
    const code = match[2].replace(/^[\n]|[\n]$/g, '')
    console.log('[renderCodeBlocks] Found complete code block #' + codeBlockCount + ', language:', language, ', code length:', code.length)
    const highlighted = highlightCode(code, language)
    const label = language ? `<span class="code-block-lang">${language}</span>` : ''
    const escapedCode = escapeHtml(code).replace(/`/g, '&#96;')
    result += `<div class="code-block-wrapper" data-code="${escapedCode}"><div class="code-block-header"><span class="code-block-lang">${language}</span><button class="code-copy-btn" title="复制代码"><svg class="copy-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><svg class="copied-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="display:none"><polyline points="20 6 9 17 4 12"></polyline></svg><span class="copy-text">复制</span></button></div><pre class="code-block-pre"><code class="hljs">${highlighted}</code></pre></div>`

    lastIndex = match.index + match[0].length
  }

  // 检查是否有不完整的代码块（流式传输中）
  const remainingText = s.substring(lastIndex)
  const incompleteMatch = incompleteCodeBlockRegex.exec(remainingText)

  if (incompleteMatch) {
    codeBlockCount++
    const beforeIncomplete = remainingText.substring(0, incompleteMatch.index)
    result += escapeHtml(beforeIncomplete)

    const language = incompleteMatch[1] || ''
    const code = incompleteMatch[2]
    console.log('[renderCodeBlocks] Found INCOMPLETE code block #' + codeBlockCount + ', language:', language, ', code length:', code.length)
    const highlighted = highlightCode(code, language)
    const label = language ? `<span class="code-block-lang">${language}</span>` : ''
    const escapedCode = escapeHtml(code).replace(/`/g, '&#96;')
    result += `<div class="code-block-wrapper" data-code="${escapedCode}"><div class="code-block-header"><span class="code-block-lang">${language}</span><button class="code-copy-btn" title="复制代码"><svg class="copy-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg><svg class="copied-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="display:none"><polyline points="20 6 9 17 4 12"></polyline></svg><span class="copy-text">复制</span></button></div><pre class="code-block-pre"><code class="hljs">${highlighted}</code></pre></div>`
  } else {
    result += escapeHtml(remainingText)
  }

  console.log('[renderCodeBlocks] Total code blocks found:', codeBlockCount, ', Output length:', result.length)
  return result
}

function renderInlineCode(s: string): string {
  return s.replace(/`([^`\n]+)`/g, '<code class="inline-code">$1</code>')
}

function renderBold(s: string): string {
  let result = s.replace(/\*\*(.+?)\*\*/g, '<strong class="font-semibold">$1</strong>')
  result = result.replace(/__(.+?)__/g, '<strong class="font-semibold">$1</strong>')
  return result
}

function renderItalic(s: string): string {
  return s.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')
}

function renderStrikethrough(s: string): string {
  return s.replace(/~~(.+?)~~/g, '<del>$1</del>')
}

function renderLinks(s: string): string {
  return s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="markdown-link">$1</a>')
    .replace(/(?!\()\bhttps?:\/\/[^\s<>\]]+/gi, '<a href="$&" target="_blank" rel="noopener" class="markdown-link">$&</a>')
}

function renderHeaders(s: string): string {
  return s
    .replace(/^######\s+(.+)$/gm, '<h6>$1</h6>')
    .replace(/^#####\s+(.+)$/gm, '<h5>$1</h5>')
    .replace(/^####\s+(.+)$/gm, '<h4>$1</h4>')
    .replace(/^###\s+(.+)$/gm, '<h3 class="markdown-h3">$1</h3>')
    .replace(/^##\s+(.+)$/gm, '<h2 class="markdown-h2">$1</h2>')
    .replace(/^#\s+(.+)$/gm, '<h1 class="markdown-h1">$1</h1>')
}

function renderBlockquotes(s: string): string {
  const lines = s.split('\n')
  let inBlockquote = false
  const result: string[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.startsWith('> ')) {
      if (!inBlockquote) {
        result.push('<blockquote class="blockquote">')
        inBlockquote = true
      }
      result.push(line.substring(2))
    } else {
      if (inBlockquote) {
        result.push('</blockquote>')
        inBlockquote = false
      }
      result.push(line)
    }
  }

  if (inBlockquote) {
    result.push('</blockquote>')
  }

  return result.join('\n')
}

function renderLists(s: string): string {
  const lines = s.split('\n')
  const out: string[] = []
  let listOpen = false
  let listType = ''

  for (const line of lines) {
    const ulMatch = line.match(/^[-*]\s+(.*)$/)
    const olMatch = line.match(/^\d+\.\s+(.*)$/)

    if (ulMatch) {
      if (listOpen && listType !== 'ul') {
        out.push(listType === 'ol' ? '</ol>' : '</ul>')
        listOpen = false
        listType = ''
      }
      if (!listOpen || listType !== 'ul') {
        out.push('<ul class="markdown-list">')
        listOpen = true
        listType = 'ul'
      }
      out.push(`<li>${ulMatch[1]}</li>`)
    } else if (olMatch) {
      if (listOpen && listType !== 'ol') {
        out.push(listType === 'ul' ? '</ul>' : '</ol>')
        listOpen = false
        listType = ''
      }
      if (!listOpen || listType !== 'ol') {
        out.push('<ol class="markdown-list-ordered">')
        listOpen = true
        listType = 'ol'
      }
      out.push(`<li>${olMatch[1]}</li>`)
    } else {
      if (listOpen) {
        out.push(listType === 'ul' ? '</ul>' : '</ol>')
        listOpen = false
        listType = ''
      }
      out.push(line)
    }
  }

  if (listOpen) {
    out.push(listType === 'ul' ? '</ul>' : '</ol>')
  }

  return out.join('\n')
}

function renderHorizontalRule(s: string): string {
  return s.replace(/^(-{3,}|\*{3,}|_{3,})$/gm, '<hr class="markdown-hr">')
}

function renderTables(s: string): string {
  const lines = s.split('\n')
  const result: string[] = []
  let inTable = false
  let tableHtml = ''

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim()

    if (line.startsWith('|') && line.endsWith('|')) {
      const cells = line.split('|').slice(1, -1).map(c => c.trim())

      if (!inTable) {
        inTable = true
        tableHtml = '<table class="markdown-table"><thead><tr>'
        cells.forEach(cell => {
          tableHtml += `<th>${cell}</th>`
        })
        tableHtml += '</tr></thead><tbody>'
      } else if (cells.every(c => /^[-:]+$/.test(c))) {
        continue
      } else {
        tableHtml += '<tr>'
        cells.forEach(cell => {
          tableHtml += `<td>${cell}</td>`
        })
        tableHtml += '</tr>'
      }
    } else {
      if (inTable) {
        tableHtml += '</tbody></table>'
        result.push(tableHtml)
        tableHtml = ''
        inTable = false
      }
      result.push(lines[i])
    }
  }

  if (inTable) {
    tableHtml += '</tbody></table>'
    result.push(tableHtml)
  }

  return result.join('\n')
}

function renderThinkingMarkdown(text: string): string {
  if (!text) return ''
  
  let html = escapeHtml(text)
  
  html = renderThinkingCodeBlocks(html)
  html = renderThinkingInlineCode(html)
  html = renderThinkingBold(html)
  html = renderThinkingItalic(html)
  html = renderThinkingLists(html)
  html = renderThinkingHeaders(html)
  html = renderThinkingLinks(html)
  html = renderThinkingParagraphs(html)
  
  return html
}

function renderThinkingCodeBlocks(s: string): string {
  const codeBlockRegex = /```(\w*)\s*\n([\s\S]*?)```/g
  const incompleteCodeBlockRegex = /```(\w*)\s*\n([\s\S]*)$/
  
  let match
  let result = ''
  let lastIndex = 0
  
  while ((match = codeBlockRegex.exec(s)) !== null) {
    result += s.substring(lastIndex, match.index)
    
    const language = match[1] || ''
    const code = match[2].replace(/^[\n]|[\n]$/g, '')
    const highlighted = highlightCode(code, language)
    
    result += `<div class="thinking-code-block"><div class="thinking-code-header"><span class="thinking-code-lang">${language || 'code'}</span></div><pre class="thinking-code-pre"><code>${highlighted}</code></pre></div>`
    
    lastIndex = match.index + match[0].length
  }
  
  const remainingText = s.substring(lastIndex)
  const incompleteMatch = incompleteCodeBlockRegex.exec(remainingText)
  
  if (incompleteMatch) {
    result += remainingText.substring(0, incompleteMatch.index)
    
    const language = incompleteMatch[1] || ''
    const code = incompleteMatch[2]
    const highlighted = highlightCode(code, language)
    
    result += `<div class="thinking-code-block"><div class="thinking-code-header"><span class="thinking-code-lang">${language || 'code'}</span></div><pre class="thinking-code-pre"><code>${highlighted}</code></pre></div>`
  } else {
    result += remainingText
  }
  
  return result
}

function renderThinkingInlineCode(s: string): string {
  return s.replace(/`([^`\n]+)`/g, '<code class="thinking-inline-code">$1</code>')
}

function renderThinkingBold(s: string): string {
  let result = s.replace(/\*\*(.+?)\*\*/g, '<strong class="thinking-bold">$1</strong>')
  result = result.replace(/__(.+?)__/g, '<strong class="thinking-bold">$1</strong>')
  return result
}

function renderThinkingItalic(s: string): string {
  return s.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em class="thinking-italic">$1</em>')
}

function renderThinkingLists(s: string): string {
  const lines = s.split('\n')
  const result: string[] = []
  let inUnorderedList = false
  let inOrderedList = false
  
  for (const line of lines) {
    const unorderedMatch = line.match(/^(\s*)[-*+]\s+(.*)/)
    const orderedMatch = line.match(/^(\s*)\d+\.\s+(.*)$/)
    
    if (unorderedMatch) {
      if (!inUnorderedList) {
        if (inOrderedList) {
          result.push('</ol>')
          inOrderedList = false
        }
        result.push('<ul class="thinking-list">')
        inUnorderedList = true
      }
      result.push(`<li class="thinking-list-item">${unorderedMatch[2]}</li>`)
    } else if (orderedMatch) {
      if (!inOrderedList) {
        if (inUnorderedList) {
          result.push('</ul>')
          inUnorderedList = false
        }
        result.push('<ol class="thinking-orderedList">')
        inOrderedList = true
      }
      result.push(`<li class="thinking-orderedList-item">${orderedMatch[2]}</li>`)
    } else {
      if (inUnorderedList) {
        result.push('</ul>')
        inUnorderedList = false
      } else if (inOrderedList) {
        result.push('</ol>')
        inOrderedList = false
      }
      result.push(line)
    }
  }
  
  if (inUnorderedList) result.push('</ul>')
  if (inOrderedList) result.push('</ol>')
  
  return result.join('\n')
}

function renderThinkingHeaders(s: string): string {
  let result = s
  result = result.replace(/^#### (.+)$/gm, '<h4 class="thinking-h4">$1</h4>')
  result = result.replace(/^### (.+)$/gm, '<h3 class="thinking-h3">$1</h3>')
  result = result.replace(/^## (.+)$/gm, '<h2 class="thinking-h2">$1</h2>')
  result = result.replace(/^# (.+)$/gm, '<h1 class="thinking-h1">$1</h1>')
  return result
}

function renderThinkingLinks(s: string): string {
  return s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" class="thinking-link">$1</a>')
}

function renderThinkingParagraphs(html: string): string {
  const blocks = html.split(/\n{2,}/)
  return blocks.map(block => {
    const trimmed = block.trim()
    if (!trimmed) return ''
    if (/^<(ul|ol|li|h[1-6]|div|pre)/.test(trimmed)) {
      return trimmed
    }
    return `<p class="thinking-p">${trimmed}</p>`
  }).join('\n')
}

function mdToHtml(text: string): string {
  let s = text

  s = renderCodeBlocks(s)
  s = renderInlineCode(s)
  s = renderTables(s)
  s = renderHeaders(s)
  s = renderBold(s)
  s = renderItalic(s)
  s = renderStrikethrough(s)
  s = renderLinks(s)
  s = renderBlockquotes(s)
  s = renderLists(s)
  s = renderHorizontalRule(s)

  return s
}

function splitParagraphs(html: string): string {
  const blocks: string[] = []
  let current = ''
  let inCodeBlock = false
  let depth = 0

  for (let i = 0; i < html.length; i++) {
    const char = html[i]

    if (!inCodeBlock && html.startsWith('<div class="code-block-wrapper', i)) {
      if (current.trim()) {
        blocks.push(current)
        current = ''
      }
      inCodeBlock = true
      depth = 1
    }

    current += char

    if (inCodeBlock) {
      if (char === '<') {
        const nextTag = html.substring(i, i + 4)
        if (nextTag === '<div') depth++
        else if (nextTag === '</di' || html.substring(i, i + 6) === '</div>') {
          depth--
          if (depth === 0) {
            inCodeBlock = false
            blocks.push(current)
            current = ''
          }
        }
      }
    }
  }

  if (current.trim()) {
    blocks.push(current)
  }

  return blocks.map(block => {
    const trimmed = block.trim()
    if (!trimmed) return ''

    if (/^(<div\s+class="code-block-wrapper"|<ul|<ol|<blockquote|<h[1-6]|<hr|<pre|<table)/.test(trimmed)) {
      return trimmed
    }

    return `<p class="markdown-p">${trimmed}</p>`
  }).join('\n')
}

const renderedContent = computed(() => {
  if (!props.content) return ''

  const normalized = normalizeContent(props.content)

  console.log('[Markdown] Role:', props.role, 'Input length:', normalized.length)
  console.log('[Markdown] First 200 chars:', normalized.substring(0, 200))

  let result: string

  if (props.role === 'user') {
    console.log('[Markdown] User message - extracting plain text from HTML')
    result = extractPlainText(normalized)
  } else if (containsSystemHtmlTags(normalized)) {
    console.log('[Markdown] Detected system HTML tags (like stopped message), using smart processing')
    result = processMixedContent(normalized)
  } else if (normalized.includes('&lt;') || normalized.includes('&gt;')) {
    console.log('[Markdown] Detected escaped HTML, unescaping...')
    const unescaped = normalized
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&amp;/g, '&')
      .replace(/&quot;/g, '"')
    result = mdToHtml(unescaped)
  } else {
    result = mdToHtml(normalized)
  }

  const finalResult = removeDuplicateContent(splitParagraphs(result))

  return finalResult
})

function extractPlainText(html: string): string {
  let text = html

  text = text.replace(/<p[^>]*>/gi, '')
  text = text.replace(/<\/p>/gi, '\n')
  text = text.replace(/<br\s*\/?>/gi, '\n')
  text = text.replace(/<div[^>]*>/gi, '\n')
  text = text.replace(/<\/div>/gi, '')
  text = text.replace(/<span[^>]*>/gi, '')
  text = text.replace(/<\/span>/gi, '')
  text = text.replace(/<strong[^>]*>/gi, '**')
  text = text.replace(/<\/strong>/gi, '**')
  text = text.replace(/<em[^>]*>/gi, '*')
  text = text.replace(/<\/em>/gi, '*')
  text = text.replace(/<a[^>]*href="([^"]*)"[^]*>(.*?)<\/a>/gi, '$2 ($1)')
  text = text.replace(/<code[^>]*>/gi, '`')
  text = text.replace(/<\/code>/gi, '`')
  text = text.replace(/<[^>]+>/g, '')

  text = text.replace(/&nbsp;/g, ' ')
  text = text.replace(/&amp;/g, '&')
  text = text.replace(/&lt;/g, '<')
  text = text.replace(/&gt;/g, '>')
  text = text.replace(/&quot;/g, '"')

  text = text.split('\n').map(line => line.trim()).filter(line => line).join('\n')

  return mdToHtml(text)
}

function containsSystemHtmlTags(text: string): boolean {
  return /<p\s+class="[^"]*text-text-4[^"]*"/.test(text) ||
         /<p\s+class="[^"]*text-danger[^"]*"/.test(text) ||
         /<p\s+class="[^"]*italic[^"]*"[^>]*>已停止/.test(text) ||
         /<p\s+class="[^"]*italic[^"]*"[^>]*>文件上传失败/.test(text)
}

function processMixedContent(text: string): string {
  const parts = text.split(/(<p\s+class="[^"]*"(?:\s+[^>]*)?>.*?<\/p>)/gs)
  
  return parts.map(part => {
    const trimmedPart = part.trim()
    
    if (/^<p\s+class="/.test(trimmedPart)) {
      console.log('[processMixedContent] Keeping system HTML as-is:', trimmedPart.substring(0, 50))
      return trimmedPart
    } else if (trimmedPart) {
      return mdToHtml(trimmedPart)
    }
    
    return ''
  }).join('')
}

function removeDuplicateContent(html: string): string {
  const codeBlockRegex = /<div\s+class="code-block-wrapper"[^>]*>[\s\S]*?<\/div>\s*<\/div>/gi
  const codeBlocks: string[] = []
  let match

  while ((match = codeBlockRegex.exec(html)) !== null) {
    codeBlocks.push(match[0])
  }

  if (codeBlocks.length === 0) return html

  for (const block of codeBlocks) {
    const dataCodeMatch = block.match(/data-code="([^"]*)"/)
    if (!dataCodeMatch) continue

    const encodedCode = dataCodeMatch[1]
    const decodedCode = encodedCode
      .replace(/&#96;/g, '`')
      .replace(/&amp;/g, '&')
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')

    if (decodedCode.length < 20) continue

    const escapedForRegex = decodedCode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const snippet = escapedForRegex.substring(0, 100)

    const duplicatePattern = new RegExp(snippet + '[\\s\\S]{0,50}', 'i')

    if (duplicatePattern.test(html)) {
      const blockIndex = html.indexOf(block)
      const beforeBlock = html.substring(0, blockIndex)

      if (duplicatePattern.test(beforeBlock)) {
        html = html.replace(duplicatePattern, '').replace(/\n{3,}/g, '\n\n')
        console.log('[removeDuplicateContent] Removed duplicate content before code block')
      }
    }
  }

  return html
}

const msgRef = ref<HTMLElement | null>(null)

onMounted(() => {
  nextTick(() => {
    bindCopyButtons()
  })
})

watch(() => props.content, () => {
  nextTick(() => {
    bindCopyButtons()
  })
})

function bindCopyButtons() {
  if (!msgRef.value) return

  const buttons = msgRef.value.querySelectorAll('.code-copy-btn:not([data-bound])')
  buttons.forEach(btn => {
    btn.addEventListener('click', handleCopyClick)
    btn.setAttribute('data-bound', 'true')
  })
}

async function handleCopyClick(e: Event) {
  const btn = e.currentTarget as HTMLElement
  const wrapper = btn.closest('.code-block-wrapper') as HTMLElement
  if (!wrapper) return

  const code = wrapper.dataset.code || ''
  if (!code) return

  try {
    await navigator.clipboard.writeText(code)
    
    btn.classList.add('copied')
    const copyIcon = btn.querySelector('.copy-icon') as HTMLElement
    const copiedIcon = btn.querySelector('.copied-icon') as HTMLElement
    const copyText = btn.querySelector('.copy-text') as HTMLElement
    
    if (copyIcon) copyIcon.style.display = 'none'
    if (copiedIcon) copiedIcon.style.display = 'block'
    if (copyText) copyText.textContent = '已复制'

    setTimeout(() => {
      btn.classList.remove('copied')
      if (copyIcon) copyIcon.style.display = 'block'
      if (copiedIcon) copiedIcon.style.display = 'none'
      if (copyText) copyText.textContent = '复制'
    }, 2000)
  } catch (err) {
    console.error('[Copy] Failed to copy:', err)
  }
}
</script>

<template>
  <div class="msg" :class="role">
    <div v-if="role !== 'user'" class="msg-avatar w-[26px] h-[26px] shrink-0 rounded-md grid place-items-center text-xs bg-accent text-bg font-serif italic text-[14px]">A</div>
    <div class="msg-body max-w-[88%]" :class="role === 'user' ? 'ml-auto' : ''">
      <div class="msg-author text-[11.5px] text-text-3 mb-1 flex items-center gap-2" :class="role === 'user' ? 'justify-end' : ''">
        <b class="text-text font-medium">{{ author }}</b>
        <span class="time tabular-nums font-mono text-[10.5px]">{{ time }}{{ thinkTime ? ` · 思考 ${thinkTime}` : '' }}</span>
      </div>
      <div v-if="hasContent() || isLoading" class="msg-content text-[14px] leading-[1.7] text-text px-4 py-2.5 rounded-2xl"
        :class="role === 'user' ? 'bg-accent/12 border border-accent/20' : 'bg-bg-2 border border-border'"
      >
        <div v-if="isLoading && !hasAnyResponse()" class="bubble-loading flex items-center gap-[6px] py-2 px-1">
          <span class="bubble bubble-1 w-[8px] h-[8px] rounded-full bg-accent/70"></span>
          <span class="bubble bubble-2 w-[8px] h-[8px] rounded-full bg-accent/80"></span>
          <span class="bubble bubble-3 w-[8px] h-[8px] rounded-full bg-accent"></span>
        </div>
        <div v-else ref="msgRef" class="relative markdown-body">
          <div v-if="hasThinking" class="thinking-block mb-4 rounded-xl overflow-hidden">
            <button
              class="thinking-header flex items-center gap-1.5 w-full text-[13px] cursor-pointer bg-transparent border-0 px-4 py-3 hover:opacity-80 transition-opacity font-sans"
              style="background: linear-gradient(135deg, rgba(16, 185, 129, 0.08) 0%, rgba(20, 184, 166, 0.06) 100%); color: #059669;"
              @click="thinkingExpanded = !thinkingExpanded"
            >
              <span>已深度思考{{ thinkTime ? `(用时${thinkTime})` : '' }}</span>
              <svg
                class="w-3.5 h-3.5 transition-transform duration-200 ml-auto"
                :class="{ 'rotate-180': thinkingExpanded }"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                stroke-linecap="round" stroke-linejoin="round"
              >
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </button>
            <div
              v-show="thinkingExpanded"
              class="thinking-content px-4 pb-4 pl-5 text-[14px] leading-[1.8]"
              style="background: rgba(16, 185, 129, 0.04); color: var(--color-text-2); border-left: 3px solid rgba(16, 185, 129, 0.25);"
            >
              <div v-html="renderedThinkingContent"></div>
            </div>
          </div>
          <div v-html="renderedContent"></div>
          <span v-if="isLoading" class="cursor inline-block w-[2px] h-[1em] bg-accent ml-[2px] align-middle"></span>
        </div>
        <!-- <div v-if="sources && sources.length > 0 && role === 'assistant'" class="sources-area mt-3 px-4 py-3 rounded-xl border border-accent/20 bg-accent/5">
          <div class="sources-header flex items-center gap-1.5 mb-2 text-[12px] font-medium text-accent">
            <Library class="w-[14px] h-[14px]" />
            <span>知识库参考 ({{ sources.length }})</span>
          </div>
          <div class="sources-list flex flex-col gap-2">
            <div v-for="(src, idx) in sources.slice(0, 3)" :key="idx"
              class="source-card rounded-lg border border-border/50 bg-bg-1/60 px-3 py-2 text-[12px] leading-relaxed">
              <div class="source-meta flex items-center gap-2 mb-1">
                <span class="source-doc font-medium text-text">{{ src.document_name || '未知文档' }}</span>
                <span v-if="src.page_number" class="source-page text-text-4">P{{ src.page_number }}</span>
                <span v-if="src.score" class="source-score ml-auto text-accent/70 tabular-nums font-mono">{{ (src.score * 100).toFixed(0) }}%</span>
              </div>
              <p class="source-snippet text-text-3 line-clamp-2">{{ src.content_snippet }}</p>
            </div>
          </div>
        </div> -->
      </div>
      <AttachCard
        v-for="(att, i) in attachments"
        :key="i"
        :type="att.type"
        :tag="att.tag"
        :title="att.title"
        :subtitle="att.subtitle"
        :meta="att.meta"
        :codeContent="att.codeContent"
        :src="att.src || ''"
        :file-name="att.fileName"
        :file-size="att.fileSize"
        :file-type="att.fileType"
      />
    </div>
    <div v-if="role === 'user'" class="msg-avatar w-[26px] h-[26px] shrink-0 rounded-md grid place-items-center text-xs bg-[oklch(70%_0.05_200)] text-bg font-semibold">L</div>
  </div>
</template>

<style scoped>
.msg { display: flex; align-items: flex-start; gap: 10px; padding: 2px 0; }

.sources-area { animation: source-fade-in 0.3s ease-out; }
@keyframes source-fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.source-card { transition: border-color 0.15s ease, background-color 0.15s ease; }
.source-card:hover { border-color: var(--color-accent-line); background-color: var(--color-bg-hover); }
.line-clamp-2 { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

.bubble-loading .bubble { animation: bubble-float 1.4s ease-in-out infinite; }
.bubble-loading .bubble-1 { animation-delay: 0ms; }
.bubble-loading .bubble-2 { animation-delay: 200ms; }
.bubble-loading .bubble-3 { animation-delay: 400ms; }
@keyframes bubble-float {
  0%, 60%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-8px) scale(1.15);
    opacity: 1;
  }
}
.cursor { animation: blink 0.8s step-end infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

.thinking-header {
  user-select: none;
}

.thinking-content :deep(p) {
  margin: 0 0 8px;
}

.thinking-content :deep(p:last-child) {
  margin-bottom: 0;
}

.thinking-content :deep(.thinking-p) {
  margin: 0 0 10px;
  line-height: 1.7;
}

.thinking-content :deep(.thinking-p:last-child) {
  margin-bottom: 0;
}

.thinking-content :deep(.thinking-bold) {
  font-weight: 600;
  color: var(--color-text);
}

.thinking-content :deep(.thinking-italic) {
  font-style: italic;
}

.thinking-content :deep(.thinking-inline-code) {
  background: rgba(16, 185, 129, 0.12);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  color: #059669;
}

.thinking-content :deep(.thinking-list),
.thinking-content :deep(.thinking-orderedList) {
  margin: 8px 0;
  padding-left: 20px;
}

.thinking-content :deep(.thinking-list-item),
.thinking-content :deep(.thinking-orderedList-item) {
  margin: 4px 0;
  line-height: 1.6;
}

.thinking-content :deep(.thinking-list-item::marker) {
  color: #10b981;
}

.thinking-content :deep(.thinking-h1),
.thinking-content :deep(.thinking-h2),
.thinking-content :deep(.thinking-h3),
.thinking-content :deep(.thinking-h4) {
  font-weight: 600;
  color: var(--color-text);
  margin: 12px 0 8px;
}

.thinking-content :deep(.thinking-h1) {
  font-size: 16px;
  color: #059669;
}

.thinking-content :deep(.thinking-h2) {
  font-size: 15px;
  color: #059669;
}

.thinking-content :deep(.thinking-h3) {
  font-size: 14.5px;
}

.thinking-content :deep(.thinking-h4) {
  font-size: 14px;
}

.thinking-content :deep(.thinking-link) {
  color: #10b981;
  text-decoration: none;
  border-bottom: 1px solid rgba(16, 185, 129, 0.3);
  transition: all 0.2s ease;
}

.thinking-content :deep(.thinking-link:hover) {
  color: #059669;
  border-bottom-color: #059669;
}

.thinking-content :deep(.thinking-code-block) {
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  background: oklch(15% 0.01 260);
  border: 1px solid rgba(16, 185, 129, 0.15);
}

@media (prefers-color-scheme: light) {
  .thinking-content :deep(.thinking-code-block) {
    background: oklch(97% 0.005 150);
    border-color: rgba(16, 185, 129, 0.25);
  }
}

.thinking-content :deep(.thinking-code-header) {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: linear-gradient(to bottom, oklch(18% 0.01 260), oklch(18% 0.01 260));
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

@media (prefers-color-scheme: light) {
  .thinking-content :deep(.thinking-code-header) {
    background: linear-gradient(to bottom, oklch(95% 0.01 150), oklch(95% 0.01 150));
    border-bottom-color: rgba(0, 0, 0, 0.08);
  }
}

.thinking-content :deep(.thinking-code-lang) {
  font-size: 10px;
  font-weight: 600;
  color: #10b981;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  padding: 2px 6px;
  background: rgba(16, 185, 129, 0.15);
  border-radius: 3px;
}

.thinking-content :deep(.thinking-code-pre) {
  margin: 0;
  padding: 12px 16px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.6;
}

.thinking-content :deep(.thinking-code-pre code) {
  font-family: 'Cascadia Code', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Courier New', monospace;
  color: oklch(90% 0.01 260);
}

.markdown-body {
  word-wrap: break-word;
  overflow-wrap: break-word;
  line-height: 1.7;
}

.markdown-body :deep(.markdown-p) {
  margin-bottom: 8px;
  line-height: 1.7;
}

.markdown-body :deep(.markdown-p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text);
}

.markdown-body :deep(.markdown-h1) {
  font-size: 1.5em;
  font-weight: 700;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--color-border);
}

.markdown-body :deep(.markdown-h2) {
  font-size: 1.3em;
  font-weight: 650;
  color: var(--color-text);
  margin-top: 20px;
}

.markdown-body :deep(.markdown-h3) {
  font-size: 1.15em;
  font-weight: 600;
  color: var(--color-text-2);
}

.markdown-body :deep(.code-block-wrapper) {
  position: relative;
  margin: 20px 0;
  border-radius: 10px;
  border: 1px solid var(--color-border, rgba(0, 0, 0, 0.15));
  background: oklch(15% 0.01 260);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden !important;
  max-width: 100%;
}

@media (prefers-color-scheme: light) {
  .markdown-body :deep(.code-block-wrapper) {
    background: oklch(18% 0.005 260);
    border-color: rgba(0, 0, 0, 0.12);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  }
}

.markdown-body :deep(.code-block-lang) {
  font-size: 10.5px;
  font-weight: 600;
  color: oklch(70% 0.02 260);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}

.markdown-body :deep(.code-block-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  flex-shrink: 0;
  background: linear-gradient(to bottom, oklch(15% 0.01 260) 0%, oklch(15% 0.01 260 90%) 100%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  position: relative;
  z-index: 5;
  overflow: hidden;
}

@media (prefers-color-scheme: light) {
  .markdown-body :deep(.code-block-header) {
    background: linear-gradient(to bottom, oklch(18% 0.005 260) 0%, oklch(18% 0.005 260 90%) 100%);
    border-bottom-color: rgba(0, 0, 0, 0.1);
  }
}

.markdown-body :deep(.code-copy-btn) {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 500;
  color: #aaa;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 1;
}

.markdown-body :deep(.code-copy-btn:hover) {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.25);
  color: #fff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.markdown-body :deep(.code-copy-btn:active) {
  transform: translateY(0);
}

.markdown-body :deep(.code-copy-btn.copied) {
  background: rgba(16, 185, 129, 0.25);
  border-color: rgba(16, 185, 129, 0.4);
  color: #10b981;
}

.markdown-body :deep(.code-copy-btn .copy-text) {
  font-size: 10.5px;
  letter-spacing: 0.02em;
}

.markdown-body :deep(.code-block-pre) {
  margin: 0;
  padding: 16px 20px;
  font-size: 13px;
  line-height: 1.7;
  overflow-x: auto !important;
  overflow-y: hidden !important;
  background: transparent;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.2) transparent;
  position: relative;
  z-index: 1;
  max-width: 100%;
}

.markdown-body :deep(.code-block-pre::-webkit-scrollbar) {
  height: 6px;
}

.markdown-body :deep(.code-block-pre::-webkit-scrollbar-track) {
  background: transparent;
}

.markdown-body :deep(.code-block-pre::-webkit-scrollbar-thumb) {
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
}

.markdown-body :deep(.code-block-pre::-webkit-scrollbar-thumb:hover) {
  background-color: rgba(255, 255, 255, 0.35);
}

.markdown-body :deep(.code-block-pre code) {
  display: block;
  font-family: 'Cascadia Code', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  tab-size: 4;
  word-break: normal;
  word-wrap: normal;
  white-space: pre;
  color: oklch(90% 0.01 260);
  max-width: 100%;
  overflow-x: auto;
  overflow-wrap: break-word;
}

.markdown-body :deep(.inline-code) {
  display: inline;
  margin: 0 2px;
  padding: 2px 7px;
  font-size: 86%;
  font-family: 'Cascadia Code', 'Fira Code', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  border-radius: 5px;
  background: linear-gradient(135deg, oklch(95% 0.02 250) 0%, oklch(94% 0.03 280) 100%);
  border: 1px solid oklch(88% 0.05 250);
  color: #d73a49;
  vertical-align: middle;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: var(--color-text);
}

.markdown-body :deep(em) {
  font-style: italic;
  color: var(--color-text);
}

.markdown-body :deep(del) {
  text-decoration: line-through;
  color: var(--color-text-3);
}

.markdown-body :deep(.markdown-link) {
  color: var(--color-accent);
  text-decoration: none;
  transition: all 150ms ease;
  word-break: break-all;
}

.markdown-body :deep(.markdown-link:hover) {
  color: var(--color-accent-2);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-body :deep(.blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid var(--color-accent);
  background: oklch(97% 0.005 250);
  color: var(--color-text-2);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(.blockquote p) {
  margin-bottom: 0;
}

.markdown-body :deep(.markdown-list),
.markdown-body :deep(.markdown-list-ordered) {
  margin: 12px 0;
  padding-left: 24px;
}

.markdown-body :deep(.markdown-list) {
  list-style-type: disc;
}

.markdown-body :deep(.markdown-list-ordered) {
  list-style-type: decimal;
}

.markdown-body :deep(li) {
  margin-bottom: 4px;
  line-height: 1.6;
}

.markdown-body :deep(li:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(.markdown-hr) {
  margin: 20px 0;
  border: none;
  border-top: 1px solid var(--color-border);
}

.markdown-body :deep(pre) {
  white-space: pre;
  word-break: normal;
  word-wrap: normal;
}

.markdown-body :deep(.markdown-table) {
  width: 100%;
  margin: 16px 0;
  border-collapse: collapse;
  border-spacing: 0;
  overflow: auto;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.markdown-body :deep(.markdown-table thead) {
  background: var(--color-bg-2);
}

.markdown-body :deep(.markdown-table th),
.markdown-body :deep(.markdown-table td) {
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  text-align: left;
  font-size: 13px;
}

.markdown-body :deep(.markdown-table th) {
  font-weight: 600;
  color: var(--color-text);
  background: transparent;
}

.markdown-body :deep(.markdown-table td) {
  color: var(--color-text-2);
}

.markdown-body :deep(.markdown-table tbody tr:hover) {
  background: var(--color-bg-hover);
}

.markdown-body :deep(.markdown-table tbody tr:nth-child(even)) {
  background: var(--color-bg-2);
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 8px 0;
}
</style>