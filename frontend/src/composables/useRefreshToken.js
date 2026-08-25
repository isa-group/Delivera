import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const headers = {
  'Content-Type': 'application/json',
  'X-Device-Id': getDeviceId(),
}

const beforeRefreshTime = Number(import.meta.env.VITE_BEFORE_REFRESH_TIME)

let interval = null
let refreshTimmer = true


function parseJwt(token) {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch {
    return null
  }
}

let refreshPromise = null
let refreshCounter = 0
let lastRefreshAt = 0



export async function doRefresh() {
  refreshCounter++
  const auth = useAuthStore()
  const response = await fetch(
    `${import.meta.env.VITE_AUTH_API_URL}/api/v2/auth/refresh`,
    {
      method: 'POST',
      credentials: 'include',
      headers
    }
  )

  if (response.status === 401 || response.status === 403) {
    auth.logout()
    router.push('/')
    return
  }

  if (!response.ok) {
    throw new Error(`Refresh failed: ${response.status}`)
  }
  const data = await response.json()
  
  auth.applyLoginData(data)
  lastRefreshAt = Date.now()
 
  return data.token
}





export async function performRefresh() {
  if (refreshPromise != null){
    return refreshPromise
  } 
  
  refreshPromise = doRefresh()

  try {
    return await refreshPromise
  }finally {
    refreshPromise = null
  }
}

export async function refreshIfNeeded() {
  const auth = useAuthStore()
  if (!auth.token) {
    return
  }
  if (!auth.isJwtExpired(auth.token, beforeRefreshTime)) {
    return auth.token
  }

  const now = Date.now()

  if (now - lastRefreshAt < 5000) {
    return auth.token
  }

  const token = await performRefresh()
  return token
}

export function getDeviceId() {
  let deviceId = localStorage.getItem('deviceId')

  if (!deviceId) {
    deviceId = crypto.randomUUID()
    localStorage.setItem('deviceId', deviceId)
  }

  return deviceId
}