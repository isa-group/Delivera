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


export function startAuthRefresh() {
  const auth = useAuthStore()

 
    const payload = parseJwt(auth.token)

    const expiresAt = payload.exp * 1000
    const now = Date.now()

    const delay = expiresAt - now - 60000 // 1 min before it expires 


  clearInterval(interval)

  const refreshTime = Number(import.meta.env.VITE_REFRESH_TIME)

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
    
      if (response.status === 401) {
        auth.logout()
        await fetch(
            `${import.meta.env.VITE_AUTH_API_URL}/api/v2/auth/logout`,
            {
              method: 'GET',
              credentials: 'include'
            }
          )
        return
      }

      const data = await response.json()

      auth.setToken(data.token)
      startAuthRefresh()
    } catch {
      auth.logout()
      await fetch(
        `${import.meta.env.VITE_AUTH_API_URL}/api/v2/auth/logout`,
        {
          method: 'GET',
          credentials: 'include'
        }
      )
      return
    }
  }, Math.max(delay, 0) )
}


export function stopAuthRefresh() {
  clearInterval(interval)
}

