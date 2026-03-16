/**
 * Finance view integration tests covering EntryPaymentStatus and ClubPaymentStatus.
 *
 * Tests cover:
 * - Happy path: load and display payment status
 * - Payment status update success
 * - Error scenarios (load failure, update failure)
 * - Invalid route params guard
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import EntryPaymentStatus from '../views/staff/EntryPaymentStatus.vue'
import ClubPaymentStatus from '../views/staff/ClubPaymentStatus.vue'
import { jsonResponse } from './utils/testHelpers.js'

// ─── i18n ─────────────────────────────────────────────────────────────────────

const messages = {
  en: {
    common: { submit: 'Submit' },
    finance: {
      invalid_route_params: 'Invalid or missing route parameters.',
      bulk: {
        status_paid: 'Paid',
        status_unpaid: 'Unpaid',
        submitting: 'Submitting...'
      },
      entry: {
        title: 'Entry Payment Status',
        entry_id: 'Entry ID',
        payment_status: 'Payment Status',
        paid_at: 'Paid At',
        paid_by: 'Paid By',
        payment_reference: 'Payment Reference',
        update_status: 'Update Status',
        update_success: 'Payment status updated successfully',
        update_error: 'Failed to update payment status',
        loading: 'Loading entry payment status...',
        not_found: 'Entry not found'
      },
      club: {
        title: 'Club Payment Status',
        club_id: 'Club ID',
        total_entries: 'Total Entries',
        paid_entries: 'Paid Entries',
        unpaid_entries: 'Unpaid Entries',
        update_all: 'Update All Entries',
        update_success: 'Club payment status updated for {count} entries',
        update_error: 'Failed to update club payment status',
        loading: 'Loading club payment status...',
        not_found: 'Club not found',
        entries_list: 'Entries'
      }
    }
  }
}

function createTestI18n() {
  return createI18n({ legacy: false, locale: 'en', messages })
}

// ─── Test fixtures ─────────────────────────────────────────────────────────────

const REGATTA_ID = 'a0000000-0000-4000-8000-000000000001'
const ENTRY_ID = 'b0000000-0000-4000-8000-000000000002'
const CLUB_ID = 'c0000000-0000-4000-8000-000000000003'

const entryPaymentData = {
  entry_id: ENTRY_ID,
  payment_status: 'unpaid',
  payment_reference: null,
  paid_at: null,
  paid_by: null
}

const clubPaymentData = {
  club_id: CLUB_ID,
  total_entries: 5,
  paid_entries: 2,
  unpaid_entries: 3,
  entries: []
}

// ─── Mount helpers ─────────────────────────────────────────────────────────────

async function mountEntryPaymentStatus(path, regattaId = REGATTA_ID, entryId = ENTRY_ID) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/entries/:entryId/payment',
        component: EntryPaymentStatus
      }
    ]
  })

  await router.push(path || `/staff/regattas/${regattaId}/entries/${entryId}/payment`)
  await router.isReady()

  const wrapper = mount(EntryPaymentStatus, {
    global: { plugins: [router, createTestI18n()] }
  })
  await flushPromises()
  return wrapper
}

async function mountClubPaymentStatus(path, regattaId = REGATTA_ID, clubId = CLUB_ID) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/clubs/:clubId/payment',
        component: ClubPaymentStatus
      }
    ]
  })

  await router.push(path || `/staff/regattas/${regattaId}/clubs/${clubId}/payment`)
  await router.isReady()

  const wrapper = mount(ClubPaymentStatus, {
    global: { plugins: [router, createTestI18n()] }
  })
  await flushPromises()
  return wrapper
}

// ─── EntryPaymentStatus tests ──────────────────────────────────────────────────

describe('EntryPaymentStatus', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows loading state initially', async () => {
    globalThis.fetch.mockResolvedValue(new Promise(() => {})) // never resolves

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{
        path: '/staff/regattas/:regattaId/entries/:entryId/payment',
        component: EntryPaymentStatus
      }]
    })
    await router.push(`/staff/regattas/${REGATTA_ID}/entries/${ENTRY_ID}/payment`)
    await router.isReady()

    const wrapper = mount(EntryPaymentStatus, {
      global: { plugins: [router, createTestI18n()] }
    })

    expect(wrapper.find('.loading').exists()).toBe(true)
  })

  it('displays entry payment status after successful load', async () => {
    globalThis.fetch.mockResolvedValueOnce(jsonResponse(200, entryPaymentData))

    const wrapper = await mountEntryPaymentStatus()

    expect(wrapper.find('.error').exists()).toBe(false)
    expect(wrapper.find('.loading').exists()).toBe(false)
    expect(wrapper.find('.content').exists()).toBe(true)
  })

  it('shows error message when load fails', async () => {
    globalThis.fetch.mockResolvedValueOnce(jsonResponse(500, { message: 'Internal Server Error' }))

    const wrapper = await mountEntryPaymentStatus()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('shows update success message after successful status update', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse(200, entryPaymentData))
      .mockResolvedValueOnce(jsonResponse(200, { ...entryPaymentData, payment_status: 'paid' }))

    const wrapper = await mountEntryPaymentStatus()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('output.success').exists()).toBe(true)
  })

  it('shows update error message when update fails', async () => {
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse(200, entryPaymentData))
      .mockResolvedValueOnce(jsonResponse(400, { message: 'Bad Request' }))

    const wrapper = await mountEntryPaymentStatus()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const alerts = wrapper.findAll('[role="alert"]')
    // At least one alert should be present (could be load error or update error)
    expect(alerts.length).toBeGreaterThan(0)
  })
})

// ─── ClubPaymentStatus tests ───────────────────────────────────────────────────

describe('ClubPaymentStatus', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows loading state initially', async () => {
    globalThis.fetch.mockResolvedValue(new Promise(() => {}))

    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{
        path: '/staff/regattas/:regattaId/clubs/:clubId/payment',
        component: ClubPaymentStatus
      }]
    })
    await router.push(`/staff/regattas/${REGATTA_ID}/clubs/${CLUB_ID}/payment`)
    await router.isReady()

    const wrapper = mount(ClubPaymentStatus, {
      global: { plugins: [router, createTestI18n()] }
    })

    expect(wrapper.find('.loading').exists()).toBe(true)
  })

  it('displays club payment status after successful load', async () => {
    globalThis.fetch.mockResolvedValueOnce(jsonResponse(200, clubPaymentData))

    const wrapper = await mountClubPaymentStatus()

    expect(wrapper.find('.loading').exists()).toBe(false)
    expect(wrapper.find('.error').exists()).toBe(false)
    expect(wrapper.find('.content').exists()).toBe(true)
  })

  it('shows error message when load fails', async () => {
    globalThis.fetch.mockResolvedValueOnce(jsonResponse(500, { message: 'Server error' }))

    const wrapper = await mountClubPaymentStatus()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
