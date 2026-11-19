import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiError, type User } from '../api'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  async function refresh() {
    try {
      user.value = await api.current()
    } catch (error) {
      if (!(error instanceof ApiError) || ![1111, 401, 222].includes(error.code)) throw error
      user.value = null
    }
  }
  async function logout() {
    await api.logout()
    user.value = null
  }
  return { user, refresh, logout }
})
