export const STAFF_PRIMARY_NAV_ITEMS = [
  { key: 'regattas', to: '/staff/regattas', routeNames: ['staff-regattas'] },
  {
    key: 'rulesets',
    to: '/staff/rulesets',
    routeNames: ['staff-rulesets', 'staff-ruleset-create', 'staff-ruleset-detail'],
  },
]

export function getStaffRegattaNavItems(regattaId) {
  if (!regattaId) {
    return []
  }

  return [
    { key: 'setup', to: `/staff/regattas/${regattaId}`, routeNames: ['staff-regatta-detail'] },
    { key: 'draw', to: `/staff/regattas/${regattaId}/draw`, routeNames: ['staff-regatta-draw'] },
    {
      key: 'finance',
      to: `/staff/regattas/${regattaId}/finance`,
      routeNames: [
        'staff-regatta-finance',
        'staff-regatta-finance-invoices',
        'staff-regatta-finance-invoice',
        'staff-regatta-finance-entry',
        'staff-regatta-finance-club',
      ],
    },
    {
      key: 'operator_access',
      to: `/staff/regattas/${regattaId}/operator-access`,
      routeNames: ['staff-regatta-operator-access'],
    },
    { key: 'blocks', to: `/staff/regattas/${regattaId}/blocks`, routeNames: ['staff-blocks-management'] },
  ]
}

export function getStaffBreadcrumbs(route, t) {
  if (!route.params?.regattaId) {
    return []
  }

  const regattaId = route.params.regattaId
  const breadcrumbs = [
    {
      label: t('breadcrumb.regattas'),
      to: '/staff/regattas',
    },
    {
      label: String(regattaId),
      to: `/staff/regattas/${regattaId}`,
    },
  ]

  if (route.name === 'staff-regatta-operator-access') {
    breadcrumbs.push({
      label: t('breadcrumb.operator_access'),
      to: null,
    })
  }

  return breadcrumbs
}
