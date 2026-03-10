import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import RegattaDetail from '../views/staff/RegattaDetail.vue'
import RegattaSetupSection from '../views/staff/RegattaSetupSection.vue'

const entryStore = [
  {
    id: 'entry-entered',
    event_id: 'event-1',
    block_id: 'block-1',
    crew_id: 'crew-1',
    billing_club_id: 'club-1',
    status: 'entered'
  },
  {
    id: 'entry-row-withdrawn-before-draw',
    event_id: 'event-2',
    block_id: 'block-1',
    crew_id: 'crew-2',
    billing_club_id: 'club-1',
    status: 'withdrawn_before_draw'
  },
  {
    id: 'entry-row-withdrawn-after-draw',
    event_id: 'event-3',
    block_id: 'block-1',
    crew_id: 'crew-3',
    billing_club_id: 'club-1',
    status: 'withdrawn_after_draw'
  }
]

const mockSetupApi = {
  listEventGroups: vi.fn(async () => ({ data: [] })),
  createEventGroup: vi.fn(async () => ({})),
  listEvents: vi.fn(async () => ({ data: [] })),
  createEvent: vi.fn(async () => ({})),
  listAthletes: vi.fn(async () => ({ data: [] })),
  createAthlete: vi.fn(async () => ({})),
  listCrews: vi.fn(async () => ({ data: [] })),
  createCrew: vi.fn(async () => ({})),
  listEntries: vi.fn(async ({}, params) => ({ data: params?.status ? entryStore.filter((entry) => entry.status === params.status) : entryStore })),
  createEntry: vi.fn(async (regattaId, payload) => {
    entryStore.push({
      id: 'entry-new',
      ...payload,
      status: 'entered'
    })
    return { id: 'entry-new' }
  }),
  withdrawEntry: vi.fn(async (regattaId, entryId, payload) => {
    if (payload.reason.includes('conflict')) {
      const error = new Error('Conflict')
      error.code = 'CONFLICT'
      throw error
    }

    const entry = entryStore.find((item) => item.id === entryId)
    entry.status = payload.status
    return { id: entryId, status: payload.status }
  }),
  reinstateEntry: vi.fn(async (regattaId, entryId) => {
    const entry = entryStore.find((item) => item.id === entryId)
    entry.status = 'entered'
    return { id: entryId, status: 'entered' }
  })
}

vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createExportApi: vi.fn(() => ({})),
  createRegattaSetupApi: vi.fn(() => mockSetupApi),
  ApiError: class ApiError extends Error {}
}))

vi.mock('../composables/useExportJob', () => ({
  useExportJob: () => ({
    status: 'idle',
    jobId: '',
    downloadUrl: '',
    error: '',
    startExport: vi.fn(),
    resetState: vi.fn()
  })
}))

vi.mock('../components/export/ExportJobStatus.vue', () => ({
  default: {
    template: '<div data-testid="export-job-status" />'
  }
}))

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'

async function mountDetailPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId',
        name: 'staff-regatta-detail',
        component: RegattaDetail
      },
      {
        path: '/staff/regattas/:regattaId/setup/event-groups',
        name: 'staff-regatta-setup-event-groups',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/events',
        name: 'staff-regatta-setup-events',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/athletes',
        name: 'staff-regatta-setup-athletes',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/crews',
        name: 'staff-regatta-setup-crews',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/entries',
        name: 'staff-regatta-setup-entries',
        component: RegattaSetupSection
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}`)
  await router.isReady()

  return mount(RegattaDetail, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n]
    }
  })
}

async function settle() {
  await Promise.resolve()
  await Promise.resolve()
}

async function mountEntriesSection() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/setup/entries',
        name: 'staff-regatta-setup-entries',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/event-groups',
        name: 'staff-regatta-setup-event-groups',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/events',
        name: 'staff-regatta-setup-events',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/athletes',
        name: 'staff-regatta-setup-athletes',
        component: RegattaSetupSection
      },
      {
        path: '/staff/regattas/:regattaId/setup/crews',
        name: 'staff-regatta-setup-crews',
        component: RegattaSetupSection
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/setup/entries`)
  await router.isReady()

  return mount(RegattaSetupSection, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n]
    }
  })
}

describe('Staff regatta management workflows (issue #134)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    entryStore.splice(0, entryStore.length,
      {
        id: 'entry-entered',
        event_id: 'event-1',
        block_id: 'block-1',
        crew_id: 'crew-1',
        billing_club_id: 'club-1',
        status: 'entered'
      },
      {
        id: 'entry-row-withdrawn-before-draw',
        event_id: 'event-2',
        block_id: 'block-1',
        crew_id: 'crew-2',
        billing_club_id: 'club-1',
        status: 'withdrawn_before_draw'
      },
      {
        id: 'entry-row-withdrawn-after-draw',
        event_id: 'event-3',
        block_id: 'block-1',
        crew_id: 'crew-3',
        billing_club_id: 'club-1',
        status: 'withdrawn_after_draw'
      }
    )
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('replaces the placeholder detail page with route-backed setup links', async () => {
    const wrapper = await mountDetailPage()

    expect(wrapper.find('[data-testid="setup-nav"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Setup workflows')
    expect(wrapper.text()).toContain('Event groups')
    expect(wrapper.text()).toContain('Entries')
  })

  it('surfaces audit metadata fields for destructive actions in an accessible dialog', async () => {
    const wrapper = await mountEntriesSection()
    await settle()

    const withdrawAction = wrapper.find('button[data-action="withdraw-entry"]')
    expect(withdrawAction.exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"]').exists()).toBe(false)

    await withdrawAction.trigger('click')

    const dialog = wrapper.find('[data-testid="destructive-action-dialog"]')
    expect(dialog.exists()).toBe(true)
    expect(dialog.element.tagName).toBe('DIALOG')
    expect(dialog.attributes('aria-modal')).toBe('true')
    expect(dialog.attributes('aria-labelledby')).toBe('withdraw-dialog-title')

    expect(wrapper.find('[data-testid="destructive-action-dialog"] textarea[name="reason"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"] [data-testid="audit-actor"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"] [data-testid="audit-timestamp"]').exists()).toBe(true)
  })

  it('shows per-field validation errors and moves focus to the first invalid field', async () => {
    const wrapper = await mountEntriesSection()
    await settle()

    const form = wrapper.find('form[data-testid="setup-form"]')
    expect(form.exists()).toBe(true)

    await form.trigger('submit.prevent')
    await settle()

    const validationError = wrapper.find('[data-testid="entry-form-errors"] [role="alert"]')
    expect(validationError.exists()).toBe(true)
    expect(validationError.text().toLowerCase()).toContain('required')

    const firstInvalid = wrapper.find('input[name="event_id"]')
    expect(firstInvalid.attributes('aria-invalid')).toBe('true')
    expect(document.activeElement).toBe(firstInvalid.element)

    const secondInvalid = wrapper.find('input[name="crew_id"]')
    expect(secondInvalid.attributes('aria-invalid')).toBe('true')
  })

  it('handles 409 conflict responses with a clear recovery action', async () => {
    const wrapper = await mountEntriesSection()
    await settle()

    const withdrawAction = wrapper.find('button[data-action="withdraw-entry"]')
    expect(withdrawAction.exists()).toBe(true)

    await withdrawAction.trigger('click')

    const reason = wrapper.find('[data-testid="destructive-action-dialog"] textarea[name="reason"]')
    expect(reason.exists()).toBe(true)
    await reason.setValue('conflict response from backend')

    const confirm = wrapper.find('[data-testid="destructive-action-dialog"] button[data-action="confirm-withdraw"]')
    expect(confirm.exists()).toBe(true)
    await confirm.trigger('click')
    await settle()

    const conflictBanner = wrapper.find('[data-testid="entry-conflict-error"]')
    expect(conflictBanner.exists()).toBe(true)
    expect(conflictBanner.text().toLowerCase()).toContain('conflict')
    expect(wrapper.find('[data-testid="entry-conflict-reload"]').exists()).toBe(true)
  })

  it('regresses withdrawal transitions so invalid status changes are blocked in UI', async () => {
    const wrapper = await mountEntriesSection()
    await settle()

    const beforeDrawRow = wrapper.find('[data-testid="entry-row-withdrawn-before-draw"]')
    const afterDrawRow = wrapper.find('[data-testid="entry-row-withdrawn-after-draw"]')

    expect(beforeDrawRow.exists()).toBe(true)
    expect(afterDrawRow.exists()).toBe(true)

    expect(
      beforeDrawRow.find('button[data-action="set-status-withdrawn-before-draw"]').attributes('disabled')
    ).toBeDefined()

    expect(
      afterDrawRow.find('button[data-action="set-status-withdrawn-before-draw"]').attributes('disabled')
    ).toBeDefined()

    expect(
      afterDrawRow.find('button[data-action="set-status-entered"]').attributes('disabled')
    ).toBeDefined()
  })
})
