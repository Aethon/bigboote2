import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

/**
 * Application router — all routes from UX Design doc Section 4.
 *
 * Auth guard:
 *   All routes except /overview require isAuthenticated.
 *   /overview is public (Lights-On / demo surface, no login required).
 *
 * Route meta:
 *   requiresAuth: true  → redirects to stub login if not authenticated
 *   layout: 'shell'     → wrapped in AppShell (nav sidebar)
 *   layout: 'bare'      → rendered without AppShell (full-viewport)
 */
const routes: RouteRecordRaw[] = [
  // Root redirect
  {
    path: '/',
    redirect: '/efforts',
  },

  // Lights-On / demo — public, no AppShell
  {
    path: '/overview',
    name: 'Overview',
    component: () => import('@/views/LightsOnView.vue'),
    meta: { requiresAuth: false, layout: 'bare' },
  },

  // AppShell wrapper — all authenticated routes live inside this
  {
    path: '/',
    component: () => import('@/AppShell.vue'),
    meta: { requiresAuth: true, layout: 'shell' },
    children: [
      // Effort list (dashboard)
      {
        path: 'efforts',
        name: 'EffortList',
        component: () => import('@/views/EffortListView.vue'),
      },

      // Effort workspace — three-panel layout
      {
        path: 'efforts/:effortId',
        name: 'EffortWorkspace',
        component: () => import('@/views/EffortWorkspaceView.vue'),
        props: true,
        children: [
          // Default: redirect to the #general channel
          {
            path: '',
            redirect: (to) => ({
              name: 'EffortChannel',
              params: { ...to.params, convName: 'general' },
            }),
          },
          // Channel conversation pane
          {
            path: 'channels/:convName',
            name: 'EffortChannel',
            component: () => import('@/views/ConversationPane.vue'),
            props: true,
          },
          // Direct-message pane
          {
            path: 'dm/:collaborator',
            name: 'EffortDm',
            component: () => import('@/views/ConversationPane.vue'),
            props: true,
          },
          // Document browser
          {
            path: 'documents',
            name: 'EffortDocuments',
            component: () => import('@/views/DocumentPane.vue'),
            props: true,
          },
        ],
      },

      // Agent Type manager
      {
        path: 'agent-types',
        name: 'AgentTypeList',
        component: () => import('@/views/AgentTypeListView.vue'),
      },
      {
        path: 'agent-types/new',
        name: 'AgentTypeNew',
        component: () => import('@/views/AgentTypeFormView.vue'),
      },
      {
        path: 'agent-types/:id',
        name: 'AgentTypeEdit',
        component: () => import('@/views/AgentTypeFormView.vue'),
        props: true,
      },
      {
        path: 'agent-types/:id/history',
        name: 'AgentTypeHistory',
        component: () => import('@/views/AgentTypeHistoryView.vue'),
        props: true,
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// Auth guard — redirect unauthenticated users away from protected routes.
// In stub mode (VITE_AUTH_MODE=stub) isAuthenticated is always true,
// so the guard is a no-op until real auth is wired.
router.beforeEach((to) => {
  const requiresAuth = to.matched.some((r) => r.meta.requiresAuth !== false)
  if (requiresAuth) {
    const { isAuthenticated } = useAuth()
    if (!isAuthenticated.value) {
      // In stub mode this never fires; in auth0 mode it would redirect to login.
      return { path: '/overview' }
    }
  }
})
