function isBrowser() {
  return typeof window !== 'undefined'
}

function getAuthContext() {
  if (!isBrowser()) {
    return {}
  }

  if (!window.__REGATTADESK_AUTH__) {
    window.__REGATTADESK_AUTH__ = {}
  }

  return window.__REGATTADESK_AUTH__
}

function getLocalStorageValue(key) {
  if (!isBrowser() || typeof window.localStorage?.getItem !== 'function') {
    return null
  }

  return window.localStorage.getItem(key)
}

function hasStaffAuth() {
  const context = getAuthContext()
  if (context.staffAuthenticated === true) {
    return true
  }

  const authFlag = getLocalStorageValue('rd_staff_authenticated')
  return authFlag === 'true' || authFlag === '1'
}

function hasOperatorToken(to) {
  const context = getAuthContext()
  const contextToken = typeof context.operatorToken === 'string' ? context.operatorToken.trim() : ''
  const queryToken = typeof to.query?.token === 'string' ? to.query.token.trim() : ''
  const storedToken = getLocalStorageValue('rd_operator_token')?.trim() ?? ''

  if (queryToken && isBrowser()) {
    context.operatorToken = queryToken

    if (typeof window.localStorage?.setItem === 'function') {
      window.localStorage.setItem('rd_operator_token', queryToken)
    }

    return true
  }

  if (contextToken.length > 0) {
    return true
  }

  if (storedToken.length > 0) {
    context.operatorToken = storedToken
    return true
  }

  return false
}

export function staffGuard(to, from, next) {
  if (hasStaffAuth()) {
    next()
    return
  }

  next({ name: 'unauthorized', query: { redirect: to.fullPath } })
}

export function operatorGuard(to, from, next) {
  if (hasOperatorToken(to)) {
    next()
    return
  }

  next({ name: 'unauthorized', query: { redirect: to.fullPath } })
}
