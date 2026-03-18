import { describe, expect, it } from 'vitest'

import * as api from '../api'
import router from '../router'

describe('Staff regatta setup contract (issue #134)', () => {
  it('registers route-backed setup sections under the staff shell', () => {
    expect(router.hasRoute('staff-regatta-setup-event-groups')).toBe(true)
    expect(router.hasRoute('staff-regatta-setup-events')).toBe(true)
    expect(router.hasRoute('staff-regatta-setup-athletes')).toBe(true)
    expect(router.hasRoute('staff-regatta-setup-crews')).toBe(true)
    expect(router.hasRoute('staff-regatta-setup-entries')).toBe(true)
  })

  it('exports a regatta setup API surface for staff workflows', () => {
    expect(typeof api.createRegattaSetupApi).toBe('function')
  })
})
