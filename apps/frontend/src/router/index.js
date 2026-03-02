import { createRouter, createWebHistory } from 'vue-router'
import { staffGuard, operatorGuard } from './guards'

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
      children: [
        {
          path: '',
          redirect: '/staff/regattas'
        },
        {
          path: 'regattas',
          name: 'staff-regattas',
          component: () => import('../views/staff/RegattasList.vue')
        },
        {
          path: 'regattas/:regattaId',
          name: 'staff-regatta-detail',
          component: () => import('../views/staff/RegattaDetail.vue')
        },
        {
          path: 'regattas/:regattaId/finance',
          name: 'staff-regatta-finance',
          component: () => import('../views/staff/RegattaFinance.vue')
        },
        {
          path: 'regattas/:regattaId/blocks',
          name: 'staff-blocks-management',
          component: () => import('../views/staff/BlocksManagement.vue')
        },
        {
          path: 'regattas/:regattaId/finance/entries/:entryId',
          name: 'staff-regatta-finance-entry',
          component: () => import('../views/staff/EntryPaymentStatus.vue')
        },
        {
          path: 'regattas/:regattaId/finance/clubs/:clubId',
          name: 'staff-regatta-finance-club',
          component: () => import('../views/staff/ClubPaymentStatus.vue')
        },
        {
          path: 'regattas/:regattaId/finance/invoices',
          name: 'staff-regatta-finance-invoices',
          component: () => import('../views/staff/InvoiceList.vue')
        },
        {
          path: 'regattas/:regattaId/finance/invoices/:invoiceId',
          name: 'staff-regatta-finance-invoice',
          component: () => import('../views/staff/InvoiceDetail.vue')
        }
      ]
    },
    {
      path: '/operator',
      component: () => import('../layouts/OperatorLayout.vue'),
      beforeEnter: operatorGuard,
      children: [
        {
          path: '',
          redirect: '/operator/regattas'
        },
        {
          path: 'regattas',
          name: 'operator-regattas',
          component: () => import('../views/operator/RegattasList.vue')
        },
        {
          path: 'regattas/:regattaId',
          name: 'operator-regatta-detail',
          component: () => import('../views/operator/RegattaDetail.vue')
        },
        {
          path: 'regattas/:regattaId/line-scan',
          name: 'operator-line-scan',
          component: () => import('../views/operator/LineScan.vue')
        }
      ]
    },
    {
      path: '/public/v:drawRevision-:resultsRevision',
      component: () => import('../layouts/PublicLayout.vue'),
      children: [
        {
          path: '',
          redirect: (to) => `/public/v${to.params.drawRevision}-${to.params.resultsRevision}/schedule`
        },
        {
          path: 'schedule',
          name: 'public-schedule',
          component: () => import('../views/public/Schedule.vue')
        },
        {
          path: 'results',
          name: 'public-results',
          component: () => import('../views/public/Results.vue')
        }
      ]
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
