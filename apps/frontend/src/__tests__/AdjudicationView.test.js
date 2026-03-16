import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import AdjudicationView from '../views/staff/AdjudicationView.vue'

const mockApi = {
  listInvestigations: vi.fn(),
  getEntryDetail: vi.fn(),
  openInvestigation: vi.fn(),
  applyPenalty: vi.fn(),
  applyDsq: vi.fn(),
  applyExclusion: vi.fn(),
  revertDsq: vi.fn()
}

vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createAdjudicationApi: vi.fn(() => mockApi)
}))

function investigation(entryId, suffix) {
  return {
    investigation_id: `investigation-${suffix}`,
    entry_id: entryId,
    crew_name: `Crew ${suffix}`,
    description: `Issue ${suffix}`,
    status: 'open'
  }
}

function detail(entryId, message, revision = 0) {
  return {
    entry: {
      entry_id: entryId,
      crew_name: `Crew ${entryId}`,
      status: 'entered',
      result_label: 'provisional',
      penalty_seconds: null
    },
    investigations: [],
    history: [],
    revision_impact: {
      current_results_revision: revision,
      next_results_revision: revision + 1,
      message
    }
  }
}

async function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/adjudication',
        name: 'staff-regatta-adjudication',
        component: AdjudicationView
      }
    ]
  })

  await router.push('/staff/regattas/regatta-1/adjudication')
  await router.isReady()

  return mount(AdjudicationView, {
    global: {
      plugins: [router, i18n]
    }
  })
}

describe('AdjudicationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('keeps the open investigation entry id synced with the selected entry', async () => {
    mockApi.listInvestigations.mockResolvedValue([
      investigation('entry-1', 'one'),
      investigation('entry-2', 'two')
    ])
    mockApi.getEntryDetail
      .mockResolvedValueOnce(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))
      .mockResolvedValueOnce(detail('entry-2', 'Next adjudication change will advance results revision to 1.'))

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockApi.getEntryDetail).toHaveBeenCalledWith('regatta-1', 'entry-1')
    })
    await flushPromises()
    await vi.waitFor(() => {
      expect(wrapper.findAll('button.investigation-item')).toHaveLength(2)
    })
    expect(wrapper.get('[data-testid="open-entry-id"]').element.value).toBe('entry-1')

    await wrapper.findAll('button.investigation-item')[1].trigger('click')

    await vi.waitFor(() => {
      expect(mockApi.getEntryDetail).toHaveBeenCalledWith('regatta-1', 'entry-2')
    })
    expect(wrapper.get('[data-testid="open-entry-id"]').element.value).toBe('entry-2')
  })

  it('shows a load error when selecting another investigation fails', async () => {
    mockApi.listInvestigations.mockResolvedValue([
      investigation('entry-1', 'one'),
      investigation('entry-2', 'two')
    ])
    mockApi.getEntryDetail
      .mockResolvedValueOnce(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))
      .mockRejectedValueOnce(new Error('Selection failed'))

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockApi.getEntryDetail).toHaveBeenCalledWith('regatta-1', 'entry-1')
    })
    await flushPromises()
    await vi.waitFor(() => {
      expect(wrapper.findAll('button.investigation-item')).toHaveLength(2)
    })

    await wrapper.findAll('button.investigation-item')[1].trigger('click')

    await vi.waitFor(() => {
      expect(wrapper.get('[role="alert"]').text()).toContain('Selection failed')
    })
  })

  it('opens an investigation and refreshes the selected entry detail', async () => {
    const openedDetail = {
      ...detail('entry-1', 'Next adjudication change will advance results revision to 1.'),
      investigations: [investigation('entry-1', 'one')],
      history: [
        {
          action: 'investigation_opened',
          actor: 'jury-user',
          results_revision: 0,
          reason: 'Wash riding reported by marshal',
          created_at: '2026-03-16T10:00:00Z'
        }
      ]
    }

    mockApi.listInvestigations
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([investigation('entry-1', 'one')])
    mockApi.openInvestigation.mockResolvedValue(openedDetail)
    mockApi.getEntryDetail.mockResolvedValue(openedDetail)

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockApi.listInvestigations).toHaveBeenCalledWith('regatta-1')
    })
    await flushPromises()

    await wrapper.get('[data-testid="open-entry-id"]').setValue('entry-1')
    await wrapper.find('textarea').setValue('Wash riding reported by marshal')
    await wrapper.find('form.stack').trigger('submit')

    await vi.waitFor(() => {
      expect(mockApi.openInvestigation).toHaveBeenCalledWith('regatta-1', {
        entry_id: 'entry-1',
        description: 'Wash riding reported by marshal'
      })
    })

    expect(mockApi.listInvestigations).toHaveBeenCalledTimes(2)
    expect(mockApi.getEntryDetail).toHaveBeenCalledWith('regatta-1', 'entry-1')
    expect(wrapper.text()).toContain('Investigation opened.')
    expect(wrapper.text()).toContain('Wash riding reported by marshal')
  })

  it('validates penalty seconds before posting', async () => {
    mockApi.listInvestigations.mockResolvedValue([investigation('entry-1', 'one')])
    mockApi.getEntryDetail.mockResolvedValue(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockApi.getEntryDetail).toHaveBeenCalled()
    })
    await flushPromises()
    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="action-reason"]').exists()).toBe(true)
    })

    await wrapper.get('[data-testid="action-reason"]').setValue('Late buoy turn')
    await wrapper.get('[data-testid="penalty-seconds"]').setValue('0')
    await wrapper.get('[data-testid="penalty-form"]').trigger('submit')

    expect(mockApi.applyPenalty).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('Penalty seconds must be a whole number greater than zero.')
  })

  it('preserves the mutation revision-impact message after refresh', async () => {
    mockApi.listInvestigations
      .mockResolvedValueOnce([investigation('entry-1', 'one')])
      .mockResolvedValueOnce([investigation('entry-1', 'one')])
    mockApi.getEntryDetail
      .mockResolvedValueOnce(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))
      .mockResolvedValueOnce(detail('entry-1', 'Stale refreshed message', 1))
    mockApi.applyPenalty.mockResolvedValue(
      detail('entry-1', 'Results revision advanced to 1 after penalty.', 1)
    )

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(mockApi.getEntryDetail).toHaveBeenCalledWith('regatta-1', 'entry-1')
    })
    await flushPromises()
    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="action-reason"]').exists()).toBe(true)
    })

    await wrapper.get('[data-testid="action-reason"]').setValue('Late buoy turn')
    await wrapper.get('[data-testid="penalty-seconds"]').setValue('15')
    await wrapper.get('[data-testid="penalty-form"]').trigger('submit')

    await vi.waitFor(() => {
      expect(mockApi.applyPenalty).toHaveBeenCalledWith('regatta-1', 'entry-1', {
        reason: 'Late buoy turn',
        note: undefined,
        penalty_seconds: 15
      })
    })

    expect(wrapper.text()).toContain('Results revision advanced to 1 after penalty.')
    expect(wrapper.get('.revision-message').text()).toBe('Results revision advanced to 1 after penalty.')
  })

  it('requires confirmation before destructive adjudication actions are submitted', async () => {
    mockApi.listInvestigations.mockResolvedValue([investigation('entry-1', 'one')])
    mockApi.getEntryDetail.mockResolvedValue({
      ...detail('entry-1', 'Next adjudication change will advance results revision to 1.'),
      history: [
        {
          action: 'investigation_opened',
          actor: 'jury-user',
          results_revision: 0,
          created_at: '2026-03-16T10:00:00Z'
        }
      ]
    })
    mockApi.applyDsq.mockResolvedValue(detail('entry-1', 'Results revision advanced to 1 after DSQ.', 1))

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="action-reason"]').exists()).toBe(true)
    })

    await wrapper.get('[data-testid="action-reason"]').setValue('Lane violation confirmed on review')
    await wrapper.get('[data-testid="request-dsq"]').trigger('click')

    expect(mockApi.applyDsq).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="adjudication-confirmation"]').text()).toContain('Disqualify Crew entry-1?')
    expect(wrapper.get('[data-testid="adjudication-confirmation"]').text()).toContain('Current state: entered, result label provisional')

    await wrapper.get('[data-testid="confirm-action"]').trigger('click')

    await vi.waitFor(() => {
      expect(mockApi.applyDsq).toHaveBeenCalledWith('regatta-1', 'entry-1', {
        reason: 'Lane violation confirmed on review',
        note: undefined
      })
    })
  })

  it('applies an exclusion after confirmation and shows the revised status', async () => {
    mockApi.listInvestigations
      .mockResolvedValueOnce([investigation('entry-1', 'one')])
      .mockResolvedValueOnce([investigation('entry-1', 'one')])
    mockApi.getEntryDetail
      .mockResolvedValueOnce(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))
      .mockResolvedValueOnce({
        ...detail('entry-1', 'Results revision advanced to 1 after exclusion.', 1),
        entry: {
          entry_id: 'entry-1',
          crew_name: 'Crew entry-1',
          status: 'excluded',
          result_label: 'edited',
          penalty_seconds: null
        },
        history: [
          {
            action: 'exclusion',
            actor: 'jury-user',
            results_revision: 1,
            reason: 'Outside assistance confirmed',
            created_at: '2026-03-16T10:05:00Z'
          }
        ]
      })
    mockApi.applyExclusion.mockResolvedValue({
      ...detail('entry-1', 'Results revision advanced to 1 after exclusion.', 1),
      entry: {
        entry_id: 'entry-1',
        crew_name: 'Crew entry-1',
        status: 'excluded',
        result_label: 'edited',
        penalty_seconds: null
      },
      history: [
        {
          action: 'exclusion',
          actor: 'jury-user',
          results_revision: 1,
          reason: 'Outside assistance confirmed',
          created_at: '2026-03-16T10:05:00Z'
        }
      ]
    })

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="action-reason"]').exists()).toBe(true)
    })

    await wrapper.get('[data-testid="action-reason"]').setValue('Outside assistance confirmed')
    await wrapper.get('[data-testid="request-exclusion"]').trigger('click')

    expect(mockApi.applyExclusion).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="adjudication-confirmation"]').text()).toContain('Exclude Crew entry-1?')

    await wrapper.get('[data-testid="confirm-action"]').trigger('click')

    await vi.waitFor(() => {
      expect(mockApi.applyExclusion).toHaveBeenCalledWith('regatta-1', 'entry-1', {
        reason: 'Outside assistance confirmed',
        note: undefined
      })
    })

    expect(wrapper.text()).toContain('Status: excluded')
    expect(wrapper.get('.revision-message').text()).toBe('Results revision advanced to 1 after exclusion.')
  })

  it('allows cancelling a destructive adjudication confirmation', async () => {
    mockApi.listInvestigations.mockResolvedValue([investigation('entry-1', 'one')])
    mockApi.getEntryDetail.mockResolvedValue(detail('entry-1', 'Next adjudication change will advance results revision to 1.'))

    const wrapper = await mountPage()

    await vi.waitFor(() => {
      expect(wrapper.find('[data-testid="action-reason"]').exists()).toBe(true)
    })

    await wrapper.get('[data-testid="action-reason"]').setValue('Restore original state')
    await wrapper.get('[data-testid="request-revert-dsq"]').trigger('click')
    await wrapper.get('[data-testid="cancel-action"]').trigger('click')

    expect(mockApi.revertDsq).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="adjudication-confirmation"]').exists()).toBe(false)
  })
})
