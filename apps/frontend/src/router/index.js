import { createRouter, createWebHistory } from 'vue-router'
import { staffGuard, operatorGuard } from './guards'

function route(path, name, component) {
  return { path, name, component }
}

function redirectRoute(path, redirect) {
  return { path, redirect }
}

const staffRoutes = [
  redirectRoute('', '/staff/regattas'),
  route('regattas', 'staff-regattas', () => import('../views/staff/RegattasList.vue')),
  route('regattas/:regattaId', 'staff-regatta-detail', () => import('../views/staff/RegattaDetail.vue')),
  route('regattas/:regattaId/finance', 'staff-regatta-finance', () => import('../views/staff/RegattaFinance.vue')),
  route('regattas/:regattaId/blocks', 'staff-blocks-management', () => import('../views/staff/BlocksManagement.vue')),
  route('regattas/:regattaId/finance/entries/:entryId', 'staff-regatta-finance-entry', () => import('../views/staff/EntryPaymentStatus.vue')),
  route('regattas/:regattaId/finance/clubs/:clubId', 'staff-regatta-finance-club', () => import('../views/staff/ClubPaymentStatus.vue')),
  route('regattas/:regattaId/finance/invoices', 'staff-regatta-finance-invoices', () => import('../views/staff/InvoiceList.vue')),
  route('regattas/:regattaId/finance/invoices/:invoiceId', 'staff-regatta-finance-invoice', () => import('../views/staff/InvoiceDetail.vue')),
  route('rulesets', 'staff-rulesets', () => import('../views/staff/RulesetsList.vue')),
  route('rulesets/new', 'staff-ruleset-create', () => import('../views/staff/RulesetDetail.vue')),
  route('rulesets/:rulesetId', 'staff-ruleset-detail', () => import('../views/staff/RulesetDetail.vue'))
]

const operatorRoutes = [
  redirectRoute('', '/operator/regattas'),
  route('regattas', 'operator-regattas', () => import('../views/operator/RegattasList.vue')),
  route('regattas/:regattaId', 'operator-regatta-detail', () => import('../views/operator/RegattaDetail.vue')),
  route('regattas/:regattaId/line-scan', 'operator-line-scan', () => import('../views/operator/LineScan.vue'))
]

const publicRoutes = [
  {
    path: '',
    redirect: (to) => `/public/v${to.params.drawRevision}-${to.params.resultsRevision}/schedule`
  },
  route('schedule', 'public-schedule', () => import('../views/public/Schedule.vue')),
  route('results', 'public-results', () => import('../views/public/Results.vue'))
]

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/staff/regattas'
    },
    {
      path: '/staff',
      component: () => import('../layouts/StaffLayout.vue'),
      beforeEnter: staffGuard,
      children: staffRoutes
    },
    {
      path: '/operator',
      component: () => import('../layouts/OperatorLayout.vue'),
      beforeEnter: operatorGuard,
      children: operatorRoutes
    },
    {
      path: '/public/v:drawRevision-:resultsRevision',
      component: () => import('../layouts/PublicLayout.vue'),
      children: publicRoutes
    },
    {
      path: '/unauthorized',
      name: 'unauthorized',
      component: () => import('../views/Unauthorized.vue')
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFound.vue')
    }
  ]
})

export default router
