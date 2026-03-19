import { type ComputedRef, computed, ref } from 'vue'

/**
 * Authenticated user model returned by the AuthService.
 *
 * In stub mode: populated from hard-coded dev values.
 * In auth0 mode: populated from the Auth0 id_token / access_token claims.
 */
export interface AuthUser {
  /** Auth0 sub claim or dev stub 'user:dev'. */
  userId: string
  /** Bigboote collaborator name resolved by the coordinator, e.g. '@dev'. */
  collaboratorName: string
  /** Roles extracted from the token's custom claim. */
  roles: string[]
  /** Raw Bearer token sent to the coordinator API. */
  accessToken: string
}

/**
 * AuthService interface — implemented by either the stub or the Auth0 adapter.
 *
 * Controlled by the `VITE_AUTH_MODE` environment variable:
 *   VITE_AUTH_MODE=stub   (default) — hard-coded dev user, always authenticated
 *   VITE_AUTH_MODE=auth0  (future)  — delegates to @auth0/auth0-vue
 */
export interface AuthService {
  /** Reactive flag; true when a valid session exists. */
  isAuthenticated: ComputedRef<boolean>
  /** The currently authenticated user, or null if not logged in. */
  user: ReturnType<typeof ref<AuthUser | null>>
  /** Obtain a fresh access token for attaching to API requests. */
  getAccessToken: () => Promise<string>
  /** Initiate login (no-op in stub mode). */
  login: () => Promise<void>
  /** Clear session (no-op in stub mode). */
  logout: () => Promise<void>
}

// ---------------------------------------------------------------------------
// Stub implementation — active when VITE_AUTH_MODE !== 'auth0'
// ---------------------------------------------------------------------------

const DEV_USER: AuthUser = {
  userId: 'user:dev',
  collaboratorName: '@dev',
  roles: ['admin'],
  accessToken: 'dev-token',
}

const _stubUser = ref<AuthUser | null>(DEV_USER)

const stubAuth: AuthService = {
  isAuthenticated: computed(() => _stubUser.value !== null),
  user: _stubUser,
  getAccessToken: async () => DEV_USER.accessToken,
  login: async () => { _stubUser.value = DEV_USER },
  logout: async () => { _stubUser.value = null },
}

// ---------------------------------------------------------------------------
// Singleton composable
// ---------------------------------------------------------------------------

/**
 * Returns the active AuthService for the current VITE_AUTH_MODE.
 *
 * Usage in components and route guards:
 * ```ts
 * const { isAuthenticated, user, getAccessToken } = useAuth()
 * ```
 */
export function useAuth(): AuthService {
  const mode = import.meta.env.VITE_AUTH_MODE ?? 'stub'

  if (mode === 'auth0') {
    // TODO(Phase 19+): swap in @auth0/auth0-vue adapter when real auth is needed.
    // For now fall through to stub so the build always compiles.
    console.warn('useAuth: VITE_AUTH_MODE=auth0 not yet implemented — falling back to stub')
  }

  return stubAuth
}
