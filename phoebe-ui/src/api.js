import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth } from './auth'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use(cfg => {
  if (auth.token) cfg.headers.Authorization = 'Bearer ' + auth.token
  if (auth.actingAccountId && auth.user?.role === 'ADMIN') cfg.headers['X-Account-Id'] = auth.actingAccountId
  return cfg
})

http.interceptors.response.use(
  resp => (resp.config.responseType === 'blob' ? resp : resp.data.data),
  err => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message
    if (status === 401 && !err.config.url.includes('/auth/login')) {
      auth.logout()
      location.hash = '#/login'
    }
    if (!err.config.silent) ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http

/** 下载二进制（模板、报表）。 */
export async function download(url, params, filename) {
  const resp = await http.get(url, { params, responseType: 'blob' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(resp.data)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}
