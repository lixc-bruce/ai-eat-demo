import request from './request'

export function askApi(params) {
  return request.post('/qa/ask', params)
}

export function getQaHistoryApi(params) {
  return request.get('/qa/history', { params })
}

export function getHotQuestionsApi() {
  return request.get('/qa/hot-questions')
}
