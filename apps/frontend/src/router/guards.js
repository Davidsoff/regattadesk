function getBrowserWindow() {
  if (globalThis.window === undefined) {
    return null
  }

  return globalThis.window
}

function getAuthContext() {
  if (!globalThis.__REGATTADESK_AUTH__) {
    globalThis.__REGATTADESK_AUTH__ = {}
  }

  return globalThis.__REGATTADESK_AUTH__
}

function getLocalStorageValue(key) {
  const browserWindow = getBrowserWindow()
  if (!browserWindow || typeof browserWindow.localStorage?.getItem !== 'function') {
    return null
  }

  return browserWindow.localStorage.getItem(key)
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
  const browserWindow = getBrowserWindow()

  if (queryToken) {
    context.operatorToken = queryToken

    if (browserWindow && typeof browserWindow.localStorage?.setItem === 'function') {
      browserWindow.localStorage.setItem('rd_operator_token', queryToken)
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
