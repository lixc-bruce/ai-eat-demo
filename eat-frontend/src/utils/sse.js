/**
 * SSE 流式请求工具
 * @param {string} url - 请求URL
 * @param {object} body - 请求体
 * @param {object} callbacks - { onPlan, onToken, onDone, onError }
 * @returns {AbortController} - 用于取消请求
 */
export function sseRequest(url, body, callbacks) {
  const controller = new AbortController()
  const token = localStorage.getItem('token')

  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : ''
    },
    body: JSON.stringify(body),
    signal: controller.signal
  }).then(async response => {
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event: ')) {
          const eventType = line.slice(7).trim()
          callbacks.onEvent?.(eventType)
        } else if (line.startsWith('data: ')) {
          const data = line.slice(6)
          try {
            const parsed = JSON.parse(data)
            callbacks.onData?.(parsed)
          } catch {
            callbacks.onToken?.(data)
          }
        }
      }
    }
    callbacks.onDone?.()
  }).catch(err => {
    if (err.name !== 'AbortError') {
      callbacks.onError?.(err)
    }
  })

  return controller
}
