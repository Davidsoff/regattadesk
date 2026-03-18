import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import InvoiceList from '../views/staff/InvoiceList.vue'
import InvoiceDetail from '../views/staff/InvoiceDetail.vue'
import { getRequestAt, jsonResponse } from './utils/testHelpers.js'

const REGATTA_ID = 'a0000000-0000-4000-8000-000000000001'
const CLUB_ID = 'c0000000-0000-4000-8000-000000000003'
const INVOICE_ID = 'd0000000-0000-4000-8000-000000000004'
const ENTRY_ID = 'e0000000-0000-4000-8000-000000000005'

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    fallbackLocale: 'en',
    messages: {
      en: {
        common: {
          open: 'Open',
          loading: 'Loading...',
          error: 'Something went wrong'
        },
        finance: {
          invalid_route_params: 'Invalid or missing route parameters.',
          bulk: {
            submitting: 'Submitting...'
          },
          entry: {
            entry_id: 'Entry ID'
          },
          invoice: {
            list_title: 'Invoice List',
            list_description: 'Track invoice generation jobs, review details, and mark invoices as paid.',
            detail_title: 'Invoice Details',
            invoice_number: 'Invoice Number',
            invoice_id: 'Invoice ID',
            club_id: 'Club ID',
            amount: 'Amount',
            status: 'Status',
            status_draft: 'Draft',
            status_sent: 'Sent',
            status_paid: 'Paid',
            status_cancelled: 'Cancelled',
            generated_at: 'Generated',
            sent_at: 'Sent At',
            paid_at: 'Paid At',
            paid_by: 'Paid By',
            payment_reference: 'Payment Reference',
            refresh: 'Refresh',
            generate: 'Generate Invoices',
            generate_pending: 'Invoice generation is queued. Refresh later if it remains pending.',
            generate_running: 'Invoice generation is in progress.',
            generate_completed: 'Invoice generation completed.',
            generate_success: 'Invoice generation completed and the invoice list was refreshed.',
            generate_error: 'Failed to generate invoices',
            mark_paid: 'Mark as Paid',
            mark_paid_success: 'Invoice marked as paid',
            mark_paid_error: 'Failed to mark invoice as paid',
            actor_required: 'A signed-in staff identity is required to record who marked this invoice as paid.',
            actor_missing: 'Signed-in staff identity unavailable',
            loading: 'Loading invoices...',
            no_invoices: 'No invoices found',
            view_details: 'View Details',
            entries: 'Invoice Entries'
          }
        }
      }
    }
  })
}

function buildInvoice(overrides = {}) {
  return {
    id: INVOICE_ID,
    regatta_id: REGATTA_ID,
    club_id: CLUB_ID,
    invoice_number: 'INV-2026-001',
    entries: [
      {
        entry_id: ENTRY_ID,
        amount: 12.5
      }
    ],
    total_amount: 12.5,
    currency: 'EUR',
    status: 'draft',
    generated_at: '2026-03-18T08:00:00Z',
    sent_at: null,
    paid_at: null,
    paid_by: null,
    payment_reference: null,
    ...overrides
  }
}

async function mountInvoiceList() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/finance/invoices',
        name: 'staff-regatta-finance-invoices',
        component: InvoiceList
      },
      {
        path: '/staff/regattas/:regattaId/finance/invoices/:invoiceId',
        name: 'staff-regatta-finance-invoice',
        component: { template: '<div>Invoice detail route</div>' }
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/finance/invoices`)
  await router.isReady()

  const wrapper = mount(InvoiceList, {
    global: {
      plugins: [router, createTestI18n()]
    }
  })

  await flushPromises()
  return { wrapper, router }
}

async function mountInvoiceDetail() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/finance/invoices/:invoiceId',
        name: 'staff-regatta-finance-invoice',
        component: InvoiceDetail
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/finance/invoices/${INVOICE_ID}`)
  await router.isReady()

  const wrapper = mount(InvoiceDetail, {
    global: {
      plugins: [router, createTestI18n()]
    }
  })

  await flushPromises()
  return { wrapper, router }
}

describe('invoice staff views', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    delete globalThis.__REGATTADESK_AUTH__
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    delete globalThis.__REGATTADESK_AUTH__
  })

  it('polls invoice generation jobs and refreshes the list after completion', async () => {
    vi.useFakeTimers()

    const initialList = { data: [], pagination: { has_more: false, next_cursor: null } }
    const refreshedList = {
      data: [buildInvoice()],
      pagination: { has_more: false, next_cursor: null }
    }

    let invoiceListCalls = 0
    let jobStatusCalls = 0

    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices`) && request.method === 'GET') {
        invoiceListCalls += 1
        return Promise.resolve(jsonResponse(200, invoiceListCalls === 1 ? initialList : refreshedList))
      }

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/generate`) && request.method === 'POST') {
        return Promise.resolve(
          jsonResponse(202, {
            job_id: 'f0000000-0000-4000-8000-000000000006',
            status: 'pending'
          })
        )
      }

      if (url.endsWith('/invoices/jobs/f0000000-0000-4000-8000-000000000006') && request.method === 'GET') {
        jobStatusCalls += 1
        return Promise.resolve(
          jsonResponse(200, {
            job_id: 'f0000000-0000-4000-8000-000000000006',
            status: jobStatusCalls === 1 ? 'running' : 'completed',
            invoice_ids: jobStatusCalls === 1 ? [] : [INVOICE_ID]
          })
        )
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceList()

    expect(wrapper.text()).toContain('No invoices found')

    await wrapper.find('button.primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Invoice generation is in progress.')

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(jobStatusCalls).toBe(2)
    expect(wrapper.text()).toContain('Invoice generation completed and the invoice list was refreshed.')
    expect(wrapper.text()).toContain('INV-2026-001')
    expect(wrapper.text()).toContain('Draft')
  })

  it('shows a user-facing error when polling the invoice generation job fails', async () => {
    const initialList = { data: [], pagination: { has_more: false, next_cursor: null } }

    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices`) && request.method === 'GET') {
        return Promise.resolve(jsonResponse(200, initialList))
      }

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/generate`) && request.method === 'POST') {
        return Promise.resolve(
          jsonResponse(202, {
            job_id: 'f0000000-0000-4000-8000-000000000006',
            status: 'pending'
          })
        )
      }

      if (url.endsWith('/invoices/jobs/f0000000-0000-4000-8000-000000000006') && request.method === 'GET') {
        return Promise.resolve(jsonResponse(500, { message: 'Polling failed' }))
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceList()

    await wrapper.find('button.primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Polling failed')
    expect(wrapper.find('button.primary').attributes('disabled')).toBeUndefined()
  })

  it('keeps existing invoice rows visible when an explicit refresh fails', async () => {
    const invoiceList = {
      data: [buildInvoice()],
      pagination: { has_more: false, next_cursor: null }
    }

    let invoiceListCalls = 0

    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices`) && request.method === 'GET') {
        invoiceListCalls += 1

        if (invoiceListCalls === 1) {
          return Promise.resolve(jsonResponse(200, invoiceList))
        }

        return Promise.resolve(jsonResponse(500, { message: 'Refresh failed' }))
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceList()

    expect(wrapper.text()).toContain('INV-2026-001')

    await wrapper.find('button.secondary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Refresh failed')
    expect(wrapper.text()).toContain('INV-2026-001')
  })

  it('marks an invoice as paid with the signed-in actor and no optional payment fields', async () => {
    globalThis.__REGATTADESK_AUTH__ = {
      user: {
        name: 'Finance Staff',
        role: 'financial_manager'
      }
    }

    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/${INVOICE_ID}`) && request.method === 'GET') {
        return Promise.resolve(jsonResponse(200, buildInvoice()))
      }

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/${INVOICE_ID}/mark_paid`) && request.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            200,
            buildInvoice({
              status: 'paid',
              paid_at: '2026-03-18T08:30:00Z',
              paid_by: 'Finance Staff'
            })
          )
        )
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceDetail()

    expect(wrapper.find('input[disabled]').element.value).toBe('Finance Staff')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = getRequestAt(globalThis.fetch, 1)
    expect(await request.json()).toEqual({ paid_by: 'Finance Staff' })
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.text()).toContain('Finance Staff')
    expect(wrapper.text()).toContain('Paid')
  })

  it('blocks mark-paid submission when no signed-in staff identity is available', async () => {
    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/${INVOICE_ID}`) && request.method === 'GET') {
        return Promise.resolve(jsonResponse(200, buildInvoice()))
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceDetail()

    const submitButton = wrapper.find('button[type="submit"]')
    expect(submitButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain(
      'A signed-in staff identity is required to record who marked this invoice as paid.'
    )
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
  })

  it('refreshes the paid-by actor when auth state appears after mount', async () => {
    globalThis.fetch.mockImplementation((request) => {
      const url = request.url

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/${INVOICE_ID}`) && request.method === 'GET') {
        return Promise.resolve(jsonResponse(200, buildInvoice()))
      }

      if (url.endsWith(`/regattas/${REGATTA_ID}/invoices/${INVOICE_ID}/mark_paid`) && request.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            200,
            buildInvoice({
              status: 'paid',
              paid_at: '2026-03-18T08:30:00Z',
              paid_by: 'Late Login User'
            })
          )
        )
      }

      return Promise.resolve(jsonResponse(404, { message: 'Not found' }))
    })

    const { wrapper } = await mountInvoiceDetail()

    expect(wrapper.find('input[disabled]').element.value).toBe('Signed-in staff identity unavailable')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    globalThis.__REGATTADESK_AUTH__ = {
      user: {
        name: 'Late Login User'
      }
    }

    globalThis.dispatchEvent(new Event('focus'))
    await flushPromises()

    expect(wrapper.find('input[disabled]').element.value).toBe('Late Login User')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const request = getRequestAt(globalThis.fetch, 1)
    expect(await request.json()).toEqual({ paid_by: 'Late Login User' })
  })
})
