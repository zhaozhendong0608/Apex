import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeType = 'apple-blue' | 'emerald-green' | 'crimson-red' | 'royal-violet' | 'aurora-dark'

export const useThemeStore = defineStore('theme', () => {
  const currentTheme = ref<ThemeType>((localStorage.getItem('sys_theme') as ThemeType) || 'apple-blue')

  function setTheme(theme: ThemeType) {
    currentTheme.value = theme
    localStorage.setItem('sys_theme', theme)
    document.documentElement.setAttribute('data-theme', theme)
  }

  // 初始化主题生效
  setTheme(currentTheme.value)

  return { currentTheme, setTheme }
})
