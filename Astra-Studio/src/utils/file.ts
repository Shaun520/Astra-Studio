export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

export function getFileTypeLabel(file: File): string {
  const name = file.name.toLowerCase()
  if (name.endsWith('.pdf')) return 'PDF'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return 'Word'
  if (name.endsWith('.xls') || name.endsWith('.xlsx')) return 'Excel'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT'
  if (name.endsWith('.txt')) return 'TXT'
  if (name.endsWith('.md')) return 'Markdown'
  return file.type.split('/')[1]?.toUpperCase() || 'FILE'
}

export function downloadBlob(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export async function downloadFileFromUrl(url: string, fileName?: string): Promise<void> {
  try {
    const response = await fetch(url)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    
    const blob = await response.blob()
    const finalName = fileName || getFileNameFromResponse(response) || 'download.pdf'
    
    downloadBlob(blob, finalName)
  } catch (error) {
    console.error('[FileUtils] Download failed:', error)
    throw error
  }
}

function getFileNameFromResponse(response: Response): string | null {
  const contentDisposition = response.headers.get('Content-Disposition')
  if (!contentDisposition) return null
  
  const match = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
  return match?.[1]?.replace(/['"]/g, '') || null
}
