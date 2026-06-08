import request from './request'

export function loginApi(phone, code) {
  return request.post('/user/login', { phone, code })
}

export function sendCodeApi(phone) {
  return request.post('/user/send-code', { phone })
}

export function getProfileApi() {
  return request.get('/user/profile')
}

export function getPreferenceApi() {
  return request.get('/user/preference')
}

export function updatePreferenceApi(payload) {
  return request.put('/user/preference', payload)
}
