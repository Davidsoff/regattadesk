export const SUCCESS_MESSAGE_DURATION_MS = 3000

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function validateRouteParam(param, name) {
  const value = Array.isArray(param) ? param[0] : param

  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    return null
  }

  return value
}

export function resolveStaffAuditActor() {
  const user = globalThis.__REGATTADESK_AUTH__?.user

  if (!user || typeof user !== 'object') {
    return ''
  }

  const candidates = [
    user.displayName,
    user.name,
    user.preferred_username,
    user.username,
    user.email,
    user.sub,
    user.id
  ]

  const actor = candidates.find((candidate) => typeof candidate === 'string' && candidate.trim().length > 0)
  return actor?.trim() || ''
}

export function formatFinanceAmount(amount, currency = 'EUR') {
  if (typeof amount !== 'number') {
    return '-'
  }

  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency
    }).format(amount)
  } catch {
    return amount.toFixed(2)
  }
}

export function formatFinanceDateTime(value) {
  if (!value) {
    return '-'
  }

  return new Date(value).toLocaleString()
}

export function translateInvoiceStatus(status, t) {
  switch (status) {
    case 'draft':
      return t('finance.invoice.status_draft')
    case 'sent':
      return t('finance.invoice.status_sent')
    case 'paid':
      return t('finance.invoice.status_paid')
    case 'cancelled':
      return t('finance.invoice.status_cancelled')
    default:
      return status || '-'
  }
}
