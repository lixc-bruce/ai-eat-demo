import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, getProfileApi } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(null)
  const phone = ref('')
  const totalUsage = ref(0)
  const totalFavorites = ref(0)

  const isLoggedIn = computed(() => !!token.value)

  async function login(phoneNum, code) {
    const res = await loginApi(phoneNum, code)
    token.value = res.token
    userId.value = res.userId
    phone.value = res.phone
    localStorage.setItem('token', res.token)
  }

  async function fetchProfile() {
    if (!isLoggedIn.value) return
    const res = await getProfileApi()
    userId.value = res.userId
    phone.value = res.phone
    totalUsage.value = res.totalUsage
    totalFavorites.value = res.totalFavorites
  }

  function logout() {
    token.value = ''
    userId.value = null
    phone.value = ''
    localStorage.removeItem('token')
  }

  return { token, userId, phone, totalUsage, totalFavorites, isLoggedIn, login, fetchProfile, logout }
})
