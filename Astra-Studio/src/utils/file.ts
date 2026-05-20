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
