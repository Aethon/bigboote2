import axios from 'axios'
import { useAuth } from '@/composables/useAuth'

/**
 * Shared Axios instance for all coordinator API calls.
 *
 * Base URL is read from the VITE_API_BASE_URL environment variable; during
 * local development the Vite dev proxy rewrites /api → localhost:8080 so
 * the default empty string works without CORS issues.
 *
 * Request interceptor attaches `Authorization: Bearer <token>` to every
 * outgoing request using the active AuthService (stub or Auth0).
 *
 * See UX Design doc Section 2 and Phase 16 scaffold spec.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(async (config) => {
  const { getAccessToken } = useAuth()
  const token = await getAccessToken()
  config.headers.Authorization = `Bearer ${token}`
  return config
})
