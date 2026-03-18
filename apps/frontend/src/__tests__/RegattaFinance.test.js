import { describe, it, expect, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createI18n } from 'vue-i18n'
import RegattaFinance from '../views/staff/RegattaFinance.vue'

function jsonResponse(payload) {
  return {
    ok: true,
    status: 200,
    headers: { get: () => 'application/json' },
    json: async () => payload,
    text: async () => JSON.stringify(payload)
  }
}

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    fallbackLocale: 'en',
    messages: {
      en: {
        common: {
          search: 'Search',
          loading: 'Loading...',
          open: 'Open'
        },
        entry: {
          crew: 'Crew'
        },
        finance: {
          title: 'Finance',
          invalid_route_params: 'Invalid or missing route parameters.',
          navigation: {
            entries: 'Entries',
            clubs: 'Clubs',
            bulk: 'Bulk Update',
            invoices: 'Invoices'
          },
          home: {
            search_label: 'Search finance records',
            payment_status_label: 'Payment status filter',
            all_statuses: 'All statuses',
            status_paid: 'Paid',
            status_unpaid: 'Unpaid',
            status_partial: 'Partial',
            entries_heading: 'Entries',
            clubs_heading: 'Clubs',
            bulk_heading: 'Bulk Update',
            invoices_heading: 'Invoices',
            invoices_description: 'Generate, refresh, and review invoices for each club.',
            entry_empty: 'No matching entries',
            club_empty: 'No matching clubs',
            view_entry: 'Open entry',
            view_club: 'Open club'
          },
          entry: {
            entry_id: 'Entry ID',
            payment_status: 'Payment status'
          },
          club: {
            club_id: 'Club ID',
            paid_entries: 'Paid entries',
            unpaid_entries: 'Unpaid entries'
          },
          invoice: {
            club_name: 'Club',
            no_invoices: 'No invoices found'
          },
          bulk: {
            title: 'Bulk Payment Status',
            subtitle: 'Mark multiple records quickly.',
            select_one: 'Select something',
            invalid_uuid: 'Invalid UUID',
            error: 'Failed to bulk update payment status',
            entry_ids_label: 'Entry IDs',
            entry_ids_placeholder: 'uuid',
            club_ids_label: 'Club IDs',
            club_ids_placeholder: 'uuid',
            target_status: 'Target status',
            status_paid: 'Paid',
            status_unpaid: 'Unpaid',
            payment_reference: 'Payment reference',
            idempotency_key: 'Idempotency key',
            review_button: 'Review',
            confirm: 'Confirm',
            confirm_prefix: 'Confirm',
            confirm_entries_and: 'entries and',
            confirm_clubs_as: 'clubs as',
            submitting: 'Submitting',
            confirm_apply: 'Apply',
            result: 'Result',
            total_requested: 'Total requested',
            processed: 'Processed',
            updated: 'Updated: {count}',
            unchanged: 'Unchanged',
            failed: 'Failed',
            idempotent_replay: 'Idempotent replay',
            partial_failures: 'Partial failures',
            scope: 'Scope',
            code: 'Code',
            message: 'Message',
            density: 'Density',
            density_comfortable: 'Comfortable',
            density_compact: 'Compact'
          }
        }
      }
    }
  })
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/finance',
        name: 'staff-regatta-finance',
        component: RegattaFinance
      },
      {
        path: '/staff/regattas/:regattaId/finance/entries/:entryId',
        name: 'staff-regatta-finance-entry',
        component: { template: '<div>Entry detail</div>' }
      },
      {
        path: '/staff/regattas/:regattaId/finance/clubs/:clubId',
        name: 'staff-regatta-finance-club',
        component: { template: '<div>Club detail</div>' }
      },
      {
        path: '/staff/regattas/:regattaId/finance/invoices',
        name: 'staff-regatta-finance-invoices',
        component: { template: '<div>Invoices</div>' }
      }
    ]
  })
}

describe('RegattaFinance', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads searchable entry and club sections with discoverable drilldowns', async () => {
    const fetchMock = vi.fn((input) => {
      const url = input instanceof Request ? input.url : String(input)

      if (url.includes('/finance/entries')) {
        return Promise.resolve(jsonResponse({
          entries: [
            {
              entry_id: '7f7af3d8-9090-49d5-b21c-9cc12d35a0e6',
              crew_name: 'Crew One',
              club_name: 'Finance Club',
              payment_status: 'unpaid'
            }
          ]
        }))
      }

      if (url.includes('/finance/clubs')) {
        return Promise.resolve(jsonResponse({
          clubs: [
            {
              club_id: '81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd',
              club_name: 'Finance Club',
              payment_status: 'partial',
              paid_entries: 1,
              unpaid_entries: 1
            }
          ]
        }))
      }

      return Promise.resolve(jsonResponse({}))
    })
    vi.stubGlobal('fetch', fetchMock)

    const router = createTestRouter()
    await router.push('/staff/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/finance')
    await router.isReady()

    const wrapper = mount(RegattaFinance, {
      global: {
        plugins: [router, createTestI18n()]
      }
    })

    await flushPromises()

    const requestUrls = fetchMock.mock.calls.map(([request]) => request.url)
    expect(
      requestUrls.some((url) =>
        url.startsWith(
          'http://localhost:3000/api/v1/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/finance/entries'
        )
      )
    ).toBe(true)
    expect(
      requestUrls.some((url) =>
        url.startsWith(
          'http://localhost:3000/api/v1/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/finance/clubs'
        )
      )
    ).toBe(true)
    expect(wrapper.text()).toContain('Entries')
    expect(wrapper.text()).toContain('Clubs')
    expect(wrapper.text()).toContain('Bulk Payment Status')
    expect(wrapper.text()).toContain('Generate, refresh, and review invoices for each club.')
    expect(wrapper.text()).toContain('Crew One')
    expect(wrapper.text()).toContain('Finance Club')
    expect(wrapper.html()).toContain('/staff/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/finance/entries/7f7af3d8-9090-49d5-b21c-9cc12d35a0e6')
    expect(wrapper.html()).toContain('/staff/regattas/f3cf2a08-91e0-469d-a851-41a6f3d0e3dc/finance/clubs/81a4c9ea-2e7d-4e67-8c0e-4657d8ce26fd')
    expect(wrapper.find('nav a[href="#finance-entries"]').exists()).toBe(true)
  })

  it('does not call the finance API when the regatta route param is invalid', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const router = createTestRouter()
    await router.push('/staff/regattas/not-a-uuid/finance')
    await router.isReady()

    const wrapper = mount(RegattaFinance, {
      global: {
        plugins: [router, createTestI18n()]
      }
    })

    await flushPromises()

    expect(fetchMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Invalid or missing route parameters.')
  })
})
