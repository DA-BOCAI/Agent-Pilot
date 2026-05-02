export function getTaskIdFromUrl(): string | null {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get('taskId')
}
