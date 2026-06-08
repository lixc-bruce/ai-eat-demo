import axios from 'axios'
import { showToast } from 'vant'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    if (code === 401) {
      localStorage.removeItem('token')
      const currentPath = window.location.pathname
      window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`
      return Promise.reject(new Error('登录已过期'))
    }
    if (code === 429) {
      showToast(message || '操作太频繁，请稍后再试')
      return Promise.reject(new Error(message))
    }
    showToast(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  error => {
    if (error.code === 'ECONNABORTED') {
      showToast('请求超时，请重试')
    } else if (!error.response) {
      showToast('网络连接失败，请检查网络')
    } else {
      const status = error.response.status
      if (status === 401) {
        localStorage.removeItem('token')
        window.location.href = '/login'
      } else if (status === 429) {
        showToast('操作太频繁，请稍后再试')
      } else if (status >= 500) {
        showToast('服务繁忙，请稍后再试')
      }
    }
    return Promise.reject(error)
  }
)

export default request
