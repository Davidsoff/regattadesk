import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const mockExportApi = {
  requestPrintableExport: vi.fn(),
  getJobStatus: vi.fn(),
}

vi.mock('../api', async () => {
  const actual = await vi.importActual('../api')

  return {
    ...actual,
    createApiClient: () => ({ mocked: true }),
    createExportApi: () => mockExportApi,
  }
})

import i18n from '../i18n'
import StaffLayout from '../layouts/StaffLayout.vue'
import router from '../router'

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
const mockFetch = vi.fn()

async function mountAtPrintablesRoute() {
  await router.push(`/staff/regattas/${REGATTA_ID}/printables`)
  await router.isReady()

  return mount(StaffLayout, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n],
    },
  })
}

describe('Staff regatta printables UX (issue #143)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    globalThis.__REGATTADESK_AUTH__ = {
      staffAuthenticated: true,
    }
    mockFetch.mockReset()
    mockExportApi.requestPrintableExport.mockReset()
    mockExportApi.getJobStatus.mockReset()
    mockFetch
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: REGATTA_ID,
            name: 'Spring Head',
            draw_revision: 4,
            results_revision: 2,
            timezone: 'Europe/Amsterdam',
          }),
          {
            status: 200,
            headers: {
              'Content-Type': 'application/json',
            },
          }
        )
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            draw_revision: 7,
            results_revision: 5,
          }),
          {
            status: 200,
            headers: {
              'Content-Type': 'application/json',
            },
          }
        )
      )
    vi.stubGlobal('fetch', mockFetch)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })

  it('registers a dedicated staff printables route', () => {
    const resolved = router.resolve(`/staff/regattas/${REGATTA_ID}/printables`)

    expect(resolved.name).toBe('staff-regatta-printables')
    expect(resolved.params.regattaId).toBe(REGATTA_ID)
  })

  it('renders a dedicated printables page with export controls, job status, and header metadata preview', async () => {
    const wrapper = await mountAtPrintablesRoute()
    await flushPromises()

    expect(wrapper.find('[data-testid="printables-page"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="export-printables-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="printables-job-status"]').exists()).toBe(true)

    const metadataPreview = wrapper.find('[data-testid="printables-header-preview"]')
    expect(metadataPreview.exists()).toBe(true)
    expect(metadataPreview.text()).toContain('Spring Head')
    expect(metadataPreview.text()).toContain('Generated')
    expect(metadataPreview.text()).toContain('Draw Version: v7')
    expect(metadataPreview.text()).toContain('Results Version: v5')
    expect(metadataPreview.text()).toContain('Page: 1')
    expect(metadataPreview.text()).toContain('of 1')
    expect(mockFetch).toHaveBeenCalledWith(`/api/v1/regattas/${REGATTA_ID}`, expect.any(Object))
    expect(mockFetch).toHaveBeenCalledWith(`/public/regattas/${REGATTA_ID}/versions`, expect.any(Object))
  })

  it('surfaces pending-to-completed export flow with download link on the dedicated page', async () => {
    mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: 'job-123' })
    mockExportApi.getJobStatus
      .mockResolvedValueOnce({ status: 'pending', download_url: null, error: null })
      .mockResolvedValueOnce({
        status: 'completed',
        download_url: '/api/v1/jobs/job-123/download',
        error: null,
      })

    const wrapper = await mountAtPrintablesRoute()
    await flushPromises()

    await wrapper.find('[data-testid="export-printables-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="export-status-pending"]').exists()).toBe(true)

    await vi.runOnlyPendingTimersAsync()
    await flushPromises()

    const downloadLink = wrapper.find('[data-testid="export-download-link"]')
    expect(downloadLink.exists()).toBe(true)
    expect(downloadLink.attributes('href')).toBe('/api/v1/jobs/job-123/download')
  })

  it('surfaces failed export state and supports retry from the dedicated page', async () => {
    mockExportApi.requestPrintableExport
      .mockResolvedValueOnce({ job_id: 'job-failed' })
      .mockResolvedValueOnce({ job_id: 'job-retry' })
    mockExportApi.getJobStatus
      .mockResolvedValueOnce({ status: 'pending', download_url: null, error: null })
      .mockResolvedValueOnce({ status: 'failed', download_url: null, error: 'PDF generation failed' })
      .mockResolvedValueOnce({
        status: 'completed',
        download_url: '/api/v1/jobs/job-retry/download',
        error: null,
      })

    const wrapper = await mountAtPrintablesRoute()
    await flushPromises()

    await wrapper.find('[data-testid="export-printables-button"]').trigger('click')
    await flushPromises()
    await vi.runOnlyPendingTimersAsync()
    await flushPromises()

    expect(wrapper.find('[data-testid="export-status-failed"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="export-error-message"]').text()).toContain('PDF generation failed')

    await wrapper.find('[data-testid="export-retry-button"]').trigger('click')
    await flushPromises()

    const downloadLink = wrapper.find('[data-testid="export-download-link"]')
    expect(downloadLink.exists()).toBe(true)
    expect(downloadLink.attributes('href')).toBe('/api/v1/jobs/job-retry/download')
    expect(mockExportApi.requestPrintableExport).toHaveBeenCalledTimes(2)
  })

  it('lets staff regenerate an expired completed export from the dedicated page', async () => {
    mockExportApi.requestPrintableExport
      .mockResolvedValueOnce({ job_id: 'job-expired' })
      .mockResolvedValueOnce({ job_id: 'job-regenerated' })
    mockExportApi.getJobStatus
      .mockResolvedValueOnce({ status: 'completed', download_url: null, error: null })
      .mockResolvedValueOnce({
        status: 'completed',
        download_url: '/api/v1/jobs/job-regenerated/download',
        error: null,
      })

    const wrapper = await mountAtPrintablesRoute()
    await flushPromises()

    await wrapper.find('[data-testid="export-printables-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="export-regenerate-button"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="export-download-link"]').exists()).toBe(false)

    await wrapper.find('[data-testid="export-regenerate-button"]').trigger('click')
    await flushPromises()

    const downloadLink = wrapper.find('[data-testid="export-download-link"]')
    expect(downloadLink.exists()).toBe(true)
    expect(downloadLink.attributes('href')).toBe('/api/v1/jobs/job-regenerated/download')
    expect(mockExportApi.requestPrintableExport).toHaveBeenCalledTimes(2)
  })
})
