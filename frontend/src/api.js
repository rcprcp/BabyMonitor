// Thin wrapper around fetch() that knows about the bearer token.
//
// The token is kept in localStorage (not a cookie) because the API is
// token-based; every call attaches it explicitly. On a 401 we clear it and
// notify listeners so the UI can bounce back to the login screen.

const TOKEN_KEY = 'babymonitor.token'
const NAME_KEY = 'babymonitor.name'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getName() {
  return localStorage.getItem(NAME_KEY)
}

export function setSession(token, name) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NAME_KEY, name)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NAME_KEY)
}

// Registered by the auth provider; invoked when the server rejects the token.
let onUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

// `options.suppressUnauthorized` is set by login: a 401 there means "wrong name
// or password", not "your session expired", so we let the server's message
// through instead of clearing local state.
async function request(method, path, body, options = {}) {
  const headers = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401 && !options.suppressUnauthorized) {
    clearSession()
    onUnauthorized()
    throw new Error('Your session expired. Please sign in again.')
  }

  if (response.status === 204) {
    return null
  }

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const message = data && data.error ? data.error : `Request failed (${response.status})`
    throw new Error(message)
  }

  return data
}

// Binary fetch for recordings. The recordings route sits behind the same bearer
// gate as the rest of /api, and a plain <a href> cannot send the header, so the
// Notifications page pulls the clip with the token and plays it from a blob URL.
async function requestBlob(path) {
  const headers = {}
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, { headers })

  if (response.status === 401) {
    clearSession()
    onUnauthorized()
    throw new Error('Your session expired. Please sign in again.')
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const data = await response.json()
      if (data && data.error) {
        message = data.error
      }
    } catch {
      // Not a JSON error body; keep the status message.
    }
    throw new Error(message)
  }

  return response.blob()
}

export const api = {
  login: (name, password) =>
    request('POST', '/api/auth/login', { name, password }, { suppressUnauthorized: true }),
  logout: () => request('POST', '/api/auth/logout'),
  me: () => request('GET', '/api/auth/me'),
  getProfile: () => request('GET', '/api/users/me'),
  updateTheme: (theme) => request('PUT', '/api/users/me/theme', { theme }),
  updateThemeColor: (themeColor) => request('PUT', '/api/users/me/theme-color', { themeColor }),

  listSettings: () => request('GET', '/api/settings'),
  getSetting: (tag) => request('GET', `/api/settings/${encodeURIComponent(tag)}`),
  updateSetting: (tag, value) =>
    request('PUT', `/api/settings/${encodeURIComponent(tag)}`, { value }),

  listNotifications: () => request('GET', '/api/notifications'),
  dismissNotification: (id) =>
    request('DELETE', `/api/notifications/${encodeURIComponent(id)}`),

  // Ambient readings, newest first. `limit` caps the number of rows.
  listEnvironment: (limit) =>
    request('GET', limit === undefined ? '/api/environment' : `/api/environment?limit=${limit}`),

  listCameras: () => request('GET', '/api/cameras'),
  getCamera: (cameraNumber) => request('GET', `/api/cameras/${encodeURIComponent(cameraNumber)}`),
  updateCameraParameter: (cameraNumber, key, value) =>
    request(
      'PUT',
      `/api/cameras/${encodeURIComponent(cameraNumber)}/parameters/${encodeURIComponent(key)}`,
      { value },
    ),
  updateCameraEnabled: (cameraNumber, enabled) =>
    request('PUT', `/api/cameras/${encodeURIComponent(cameraNumber)}/enabled`, { enabled }),
  getCameraMotion: (cameraNumber) =>
    request('GET', `/api/cameras/${encodeURIComponent(cameraNumber)}/motion`),
  applyCameraMotion: (cameraNumber) =>
    request('POST', `/api/cameras/${encodeURIComponent(cameraNumber)}/motion/apply`),
  stopCameraMotion: (cameraNumber) =>
    request('POST', `/api/cameras/${encodeURIComponent(cameraNumber)}/motion/stop`),

  // A camera's recordings, and one clip's bytes for in-app playback.
  listRecordings: (cameraNumber) =>
    request('GET', `/api/cameras/${encodeURIComponent(cameraNumber)}/recordings`),
  fetchRecording: (path) => requestBlob(path),
}
