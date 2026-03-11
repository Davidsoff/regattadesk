import { getStorage, getStorageValue } from '../utils/storage.js'

function getAuthContext() {
  if (!globalThis.__REGATTADESK_AUTH__) {
    globalThis.__REGATTADESK_AUTH__ = {}
  }

  return globalThis.__REGATTADESK_AUTH__
}

function hasStaffAuth() {
  const context = getAuthContext()
  if (context.staffAuthenticated === true) {
    return true
  }

  const authFlag = getStorageValue('rd_staff_authenticated')
  return authFlag === 'true' || authFlag === '1'
}

/**
 * If the navigation target carries a ?token= query parameter, persist it to
 * the auth context and localStorage so subsequent guard checks find it.
 * This is the only place that writes auth state — kept separate so the
 * predicate hasOperatorToken() below stays pure (read-only).
 */
function activateQueryToken(to) {
  const queryToken = typeof to.query?.token === 'string' ? to.query.token.trim() : ''
  if (!queryToken) return

  const context = getAuthContext()
  context.operatorAuth = queryToken

  getStorage()?.setItem('rd_operator_token', queryToken)
}

/** Pure predicate — reads auth state without mutating it. */
function hasOperatorToken(to) {
  const context = getAuthContext()
  const contextToken = typeof context.operatorAuth === 'string' ? context.operatorAuth.trim() : ''
  if (contextToken.length > 0) return true

  const queryToken = typeof to.query?.token === 'string' ? to.query.token.trim() : ''
  if (queryToken.length > 0) return true

  const storedToken = getStorageValue('rd_operator_token')?.trim() ?? ''
  return storedToken.length > 0
}

export function staffGuard(to, from, next) {
  if (hasStaffAuth()) {
    next()
    return
  }

  next({ name: 'unauthorized', query: { redirect: to.fullPath } })
}

export function operatorGuard(to, from, next) {
  activateQueryToken(to)
  if (hasOperatorToken(to)) {
    next()
    return
  }

  next({ name: 'unauthorized', query: { redirect: to.fullPath } })
}
