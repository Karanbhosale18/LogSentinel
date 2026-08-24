import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE || '/api'
const http = axios.create({ baseURL, withCredentials: true })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/') {
      // Reload to clear auth state in App.jsx and show login
      window.location.href = '/'
    }
    return Promise.reject(error)
  }
)

export async function getStats() {
  return (await http.get('/stats')).data
}

export async function getMeta() {
  return (await http.get('/meta')).data
}

export async function listLogs(params) {
  return (await http.get('/logs', { params })).data
}

export async function getLog(id) {
  return (await http.get(`/logs/${id}`)).data
}

export async function analyzeLog(id) {
  return (await http.post(`/logs/${id}/analyze`)).data
}

export async function uploadCsv(file) {
  const form = new FormData()
  form.append('file', file)
  return (await http.post('/logs/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })).data
}

export async function rescan() {
  return (await http.post('/logs/rescan')).data
}

export async function clearAll() {
  return (await http.delete('/logs')).data
}

// ---- Authentication ----

export async function login(username, password) {
  return (await http.post('/auth/login', { username, password })).data
}

export async function logout() {
  return (await http.post('/auth/logout')).data
}

export async function getMe() {
  return (await http.get('/auth/me')).data
}

