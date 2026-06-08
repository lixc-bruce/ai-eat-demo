import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPreferenceApi, updatePreferenceApi } from '@/api/user'

export const usePreferenceStore = defineStore('preference', () => {
  const taste = ref('')
  const taboos = ref([])
  const goal = ref('')
  const scene = ref('')

  async function fetchPreference() {
    const res = await getPreferenceApi()
    if (res) {
      taste.value = res.taste || ''
      taboos.value = res.taboos || []
      goal.value = res.goal || ''
      scene.value = res.scene || ''
    }
  }

  async function updatePreference(payload) {
    await updatePreferenceApi(payload)
    taste.value = payload.taste || ''
    taboos.value = payload.taboos || []
    goal.value = payload.goal || ''
    scene.value = payload.scene || ''
  }

  return { taste, taboos, goal, scene, fetchPreference, updatePreference }
})
