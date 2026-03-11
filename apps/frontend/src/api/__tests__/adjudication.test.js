import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAdjudicationApi } from '../adjudication'

describe('adjudication api', () => {
  let client
  let api

  beforeEach(() => {
    client = {
      get: vi.fn(),
      post: vi.fn()
    }
    api = createAdjudicationApi(client)
  })

  it('loads adjudication detail for an entry', async () => {
    client.get.mockResolvedValue({ entry: { entry_id: 'entry-1' } })

    const result = await api.getEntryDetail('regatta-1', 'entry-1')

    expect(client.get).toHaveBeenCalledWith('/regattas/regatta-1/adjudication/entries/entry-1')
    expect(result.entry.entry_id).toBe('entry-1')
  })

  it('submits a penalty action with review metadata', async () => {
    client.post.mockResolvedValue({ revision_impact: { current_results_revision: 2 } })

    const payload = {
      reason: 'Late buoy turn',
      note: 'Apply standard penalty',
      penalty_seconds: 15
    }

    await api.applyPenalty('regatta-1', 'entry-1', payload)

    expect(client.post).toHaveBeenCalledWith('/regattas/regatta-1/adjudication/entries/entry-1/penalty', payload)
  })

  it('reverts a dsq through the adjudication workflow endpoint', async () => {
    client.post.mockResolvedValue({ entry: { status: 'entered' } })

    const payload = {
      reason: 'Video review overturned the DSQ',
      note: 'Restore original state'
    }

    const result = await api.revertDsq('regatta-1', 'entry-1', payload)

    expect(client.post).toHaveBeenCalledWith('/regattas/regatta-1/adjudication/entries/entry-1/revert_dsq', payload)
    expect(result.entry.status).toBe('entered')
  })
})
