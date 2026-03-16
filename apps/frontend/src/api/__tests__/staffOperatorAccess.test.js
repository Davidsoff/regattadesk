import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../client.js'
import { createStaffOperatorAccessApi } from '../staffOperatorAccess.js'

describe('createStaffOperatorAccessApi', () => {
  it('lists pending handoffs with filters and normalizes the response', async () => {
    const get = vi.fn().mockResolvedValue({
      data: [
        {
          id: 'handoff-1',
          requesting_device_id: 'device-9',
          expires_at: '2026-03-10T08:50:00Z',
        },
      ],
    })

    const api = createStaffOperatorAccessApi({
      generatedClient: { request: vi.fn() },
      get,
      post: vi.fn(),
    })

    const result = await api.listPendingHandoffs('regatta-1', {
      station: 'finish-line',
      token_id: 'token-1',
    })

    expect(get).toHaveBeenCalledWith('/regattas/regatta-1/operator/station_handoffs', {
      query: {
        station: 'finish-line',
        token_id: 'token-1',
      },
    })
    expect(result).toEqual([
      expect.objectContaining({
        id: 'handoff-1',
        requestingDeviceId: 'device-9',
        expiresAt: '2026-03-10T08:50:00Z',
      }),
    ])
  })

  it('returns a blob and filename for PDF exports from the generated client response', async () => {
    const blob = new Blob(['pdf-bytes'], { type: 'application/pdf' })
    const request = vi.fn().mockResolvedValue({
      data: blob,
      response: {
        status: 200,
        headers: new Headers({
          'Content-Disposition': 'attachment; filename="operator-token-finish-tower.pdf"',
          'Content-Type': 'application/pdf',
        }),
      },
    })

    const api = createStaffOperatorAccessApi({
      generatedClient: { request },
      get: vi.fn(),
      post: vi.fn(),
    })

    const result = await api.exportTokenPdf('regatta-1', 'token-1')

    expect(request).toHaveBeenCalledWith({
      method: 'GET',
      url: '/regattas/regatta-1/operator/tokens/token-1/export_pdf',
      parseAs: 'blob',
      responseStyle: 'fields',
    })
    expect(result).toMatchObject({
      blob,
      filename: 'operator-token-finish-tower.pdf',
      contentType: 'application/pdf',
      size: blob.size,
    })
  })

  it('normalizes handoff fields to camelCase', async () => {
    const get = vi.fn().mockResolvedValue({
      id: 'handoff-1',
      requesting_device_id: 'device-9',
      expires_at: '2026-03-10T08:50:00Z',
      created_at: '2026-03-10T08:45:00Z',
      completed_at: null,
    })

    const api = createStaffOperatorAccessApi({
      generatedClient: { request: vi.fn() },
      get,
      post: vi.fn(),
    })

    const result = await api.getStationHandoff('regatta-1', 'handoff-1')

    expect(result.requestingDeviceId).toBe('device-9')
    expect(result.expiresAt).toBe('2026-03-10T08:50:00Z')
    expect(result.createdAt).toBe('2026-03-10T08:45:00Z')
    expect(result.completedAt).toBeNull()
  })

  it('wraps generated client export failures as ApiError', async () => {
    const api = createStaffOperatorAccessApi({
      generatedClient: {
        request: vi.fn().mockResolvedValue({
          error: { code: 'FORBIDDEN', message: 'Nope' },
          response: { status: 403 },
        }),
      },
      get: vi.fn(),
      post: vi.fn(),
    })

    await expect(api.exportTokenPdf('regatta-1', 'token-1')).rejects.toBeInstanceOf(ApiError)
  })
})
