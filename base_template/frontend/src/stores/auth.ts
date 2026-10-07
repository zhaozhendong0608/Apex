import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginApi, logoutApi } from '@/api/auth'
import type { LoginDTO, UserVO } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('access_token') || '')
  const userInfo = ref<UserVO | null>(JSON.parse(localStorage.getItem('user_info') || 'null'))

  async function login(loginDTO: LoginDTO) {
    const res = await loginApi(loginDTO)
    if (res.code === 200 && res.data) {
      token.value = res.data.token
      userInfo.value = res.data.user
      localStorage.setItem('access_token', res.data.token)
      localStorage.setItem('user_info', JSON.stringify(res.data.user))
    }
    return res
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('access_token')
      localStorage.removeItem('user_info')
    }
  }

  return { token, userInfo, login, logout }
})
