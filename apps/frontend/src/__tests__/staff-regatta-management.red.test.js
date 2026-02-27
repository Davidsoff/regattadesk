import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import RegattaDetail from '../views/staff/RegattaDetail.vue'

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId',
        name: 'staff-regatta-detail',
        component: RegattaDetail
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}`)
  await router.isReady()

  return mount(RegattaDetail, {
    global: {
      plugins: [router, i18n]
    }
  })
}

describe('Staff regatta management workflows (issue #94, red)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders searchable/filterable tables for events, athletes, crews, and entries', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="events-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="athletes-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="crews-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="entries-table"]').exists()).toBe(true)

    expect(wrapper.find('input[name="entries_search"]').exists()).toBe(true)
    expect(wrapper.find('select[name="entries_status_filter"]').exists()).toBe(true)
  })

  it('provides action forms for event-group assignment and CRUD workflows', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('form[data-testid="event-form"]').exists()).toBe(true)
    expect(wrapper.find('select[name="event_group_id"]').exists()).toBe(true)

    expect(wrapper.find('form[data-testid="athlete-form"]').exists()).toBe(true)
    expect(wrapper.find('form[data-testid="crew-form"]').exists()).toBe(true)
    expect(wrapper.find('form[data-testid="entry-form"]').exists()).toBe(true)

    expect(wrapper.find('[data-testid="events-table"] [data-action="edit"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="events-table"] [data-action="delete"]').exists()).toBe(true)
  })

  it('surfaces audit metadata fields for destructive actions', async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('button[data-action="withdraw-entry"]').exists()).toBe(true)

    expect(wrapper.find('[data-testid="destructive-action-dialog"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"] textarea[name="reason"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"] [data-testid="audit-actor"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="destructive-action-dialog"] [data-testid="audit-timestamp"]').exists()).toBe(true)
  })

  it('shows validation errors and moves focus to the first invalid field', async () => {
    const wrapper = await mountPage()

    const form = wrapper.find('form[data-testid="entry-form"]')
    expect(form.exists()).toBe(true)

    await form.trigger('submit.prevent')

    const validationError = wrapper.find('[data-testid="entry-form-errors"] [role="alert"]')
    expect(validationError.exists()).toBe(true)
    expect(validationError.text()).toContain('required')

    const firstInvalid = wrapper.find('[data-testid="entry-form"] [aria-invalid="true"]')
    expect(firstInvalid.exists()).toBe(true)
    expect(document.activeElement).toBe(firstInvalid.element)
  })

  it('handles 409 conflict responses with a clear recovery action', async () => {
    const wrapper = await mountPage()

    const withdrawAction = wrapper.find('button[data-action="withdraw-entry"]')
    expect(withdrawAction.exists()).toBe(true)

    await withdrawAction.trigger('click')

    const confirm = wrapper.find('[data-testid="destructive-action-dialog"] button[data-action="confirm-withdraw"]')
    expect(confirm.exists()).toBe(true)
    await confirm.trigger('click')

    const conflictBanner = wrapper.find('[data-testid="entry-conflict-error"]')
    expect(conflictBanner.exists()).toBe(true)
    expect(conflictBanner.text()).toContain('conflict')
    expect(wrapper.find('[data-testid="entry-conflict-reload"]').exists()).toBe(true)
  })

  it('regresses withdrawal transitions so invalid status changes are blocked in UI', async () => {
    const wrapper = await mountPage()

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
