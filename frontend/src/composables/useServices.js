import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getDeviceId } from './useRefreshToken'

export function useServices(service) {
  const SERVICE_MAP = {
    "auth-service": import.meta.env.VITE_AUTH_API_URL,
    "delivera-service": import.meta.env.VITE_API_URL,
  }
  const auth = useAuthStore()
  const router = useRouter()
  const { t, te } = useI18n()
  const baseUrl = resolveBaseUrl(service)

 

  function resolveBaseUrl(service) {
    return SERVICE_MAP[service] || import.meta.env.VITE_API_URL
  }
  

  function translateError(data, fallbackKey) {
    if (data?.code && te(`error.${data.code}`)) {
      return t(`error.${data.code}`)
    }
    if (data?.message && te(`error.${data.message}`)) {
      return t(`error.${data.message}`)
    }
    return data?.message || t(fallbackKey)
  }


  async function request( endpoint, options = {}) {
    const headers = { 'Content-Type': 'application/json' ,'X-Device-Id': getDeviceId(), ...options.headers }

    if (auth.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }

    const response = await fetch(`${baseUrl}/api/v2${endpoint}`, {
      ...options,
      headers,
      credentials: "include"
    })

    // Sólo forzamos logout si el usuario estaba autenticado y la llamada no es de auth.
    // Evita que un 401 sobre un endpoint público cierre sesión al vuelo.
    if (response.status === 401 && auth.token && !endpoint.startsWith('/auth/')) {
      auth.logout()
      router.push('/')
      throw new Error('No autorizado')
    }

    return response
  }

  async function get(endpoint) {
    return request(endpoint)
  }

  async function post(endpoint, body) {
    return request(endpoint, { method: 'POST', body: JSON.stringify(body) })
  }

  async function put(endpoint, body) {
    return request(endpoint, { method: 'PUT', body: JSON.stringify(body) })
  }

  async function patch(endpoint, body) {
    return request(endpoint, { method: 'PATCH', body: JSON.stringify(body) })
  }

  async function del(endpoint) {
    return request(endpoint, { method: 'DELETE' })
  }

  return { get, post, put, patch, del, translateError }
}

export async function fetchPublicOrder(reference) {
  const res = await fetch(
    `${import.meta.env.VITE_API_URL}/api/v2/orders/public/search?reference=${encodeURIComponent(reference)}`
  )
  if (!res.ok) throw new Error('not_found')
  return res.json()
}
