import request from './request'

export function generateMealApi(params) {
  return request.post('/meal/generate', params)
}

export function regenerateMealApi(params) {
  return request.post('/meal/regenerate', params)
}

export function getFallbackMealApi() {
  return request.get('/meal/fallback')
}
