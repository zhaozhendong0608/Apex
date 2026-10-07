import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 Axios 实例
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器：动态注入 Bearer Token
service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('access_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：契约校验与错误阻断
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '网络请求错误')
      if (res.code === 401 || res.code === 4001) {
        localStorage.removeItem('access_token')
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.msg || 'Error'))
    }
    return res
  },
  (error) => {
    const errMsg = error.response?.data?.msg || error.message || '系统服务异常'
    ElMessage.error(errMsg)
    return Promise.reject(error)
  }
)

export default service
