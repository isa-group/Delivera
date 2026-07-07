import router from '@/router'
import { useAuthStore } from '@/stores/auth'


function parseJwt(token) {
    return JSON.parse(atob(token.split('.')[1]))
}

export function getDeviceId() {
  let deviceId = localStorage.getItem('deviceId')

  if (!deviceId) {
    deviceId = crypto.randomUUID()
    localStorage.setItem('deviceId', deviceId)
  }

  return deviceId
}


let interval = null


const headers = {
  'Content-Type': 'application/json',
  'X-Device-Id': getDeviceId(),
}

const beforeRefreshTime = Number(import.meta.env.VITE_BEFORE_REFRESH_TIME)

export function startAuthRefresh() {
  const auth = useAuthStore()
  if (!auth.token) {
    return
  }
  
  const payload = parseJwt(auth.token)

  const expiresAt = payload.exp * 1000
  const now = Date.now()

  const delay = expiresAt - now - beforeRefreshTime // 1 min before it expires 

  clearTimeout(interval)

  

  interval = setTimeout(async () => {
    try {
      const response = await fetch(
        `${import.meta.env.VITE_AUTH_API_URL}/api/v2/auth/refresh`,
        {
          method: 'POST',
          credentials: 'include',
          headers
        }
      )
      
      console.log(response.status)
      if (response.status === 401 || response.status === 403) {
        stopAuthRefresh()
        auth.logout()
        router.push('/')
        return
      }
      if (!response.ok) {
        throw new Error(`Refresh failed: ${response.status}`)
      }
      const data = await response.json()
      auth.applyLoginData(data)
      startAuthRefresh()
    } catch(error) {
      console.error('Refresh failed', error)
      
      setTimeout(()=> {
        startAuthRefresh()
      }, 30 * 1000) 
    }
  }, Math.max(delay, 0) )
}


export function stopAuthRefresh() {
  clearTimeout(interval)
  interval = null
}

