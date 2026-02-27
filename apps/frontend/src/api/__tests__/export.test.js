import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createExportApi } from '../export'

describe('export', () => {
  let api
  let mockClient

  beforeEach(() => {
    mockClient = {
      request: vi.fn(),
      post: vi.fn(),
      get: vi.fn()
    }
    api = createExportApi(mockClient)
  })

  describe('requestPrintableExport', () => {
    it('calls correct endpoint for printables export', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const mockResponse = {
        job_id: '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      }

      mockClient.post.mockResolvedValue(mockResponse)

      const result = await api.requestPrintableExport(regattaId)

      expect(mockClient.post).toHaveBeenCalledWith(
        `/regattas/${regattaId}/export/printables`,
        {}
      )
      expect(result).toEqual(mockResponse)
    })

    it('returns job_id on successful request', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockClient.post.mockResolvedValue({ job_id: jobId })

      const result = await api.requestPrintableExport(regattaId)

      expect(result.job_id).toBe(jobId)
    })

    it('handles API errors', async () => {
      const regattaId = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'
      const apiError = new Error('Regatta not found')
      apiError.code = 'NOT_FOUND'

      mockClient.post.mockRejectedValue(apiError)

      await expect(api.requestPrintableExport(regattaId)).rejects.toThrow('Regatta not found')
    })
  })

  describe('getJobStatus', () => {
    it('calls correct endpoint for job status', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      const mockResponse = {
        status: 'pending',
        download_url: null,
        error: null
      }

      mockClient.get.mockResolvedValue(mockResponse)

      const result = await api.getJobStatus(jobId)

      expect(mockClient.get).toHaveBeenCalledWith(`/jobs/${jobId}`)
      expect(result).toEqual(mockResponse)
    })

    it('returns pending status initially', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockClient.get.mockResolvedValue({
        status: 'pending',
        download_url: null,
        error: null
      })

      const result = await api.getJobStatus(jobId)

      expect(result.status).toBe('pending')
      expect(result.download_url).toBeNull()
      expect(result.error).toBeNull()
    })

    it('returns processing status', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockClient.get.mockResolvedValue({
        status: 'processing',
        download_url: null,
        error: null
      })

      const result = await api.getJobStatus(jobId)

      expect(result.status).toBe('processing')
    })

    it('returns completed status with download_url', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      const downloadUrl = '/api/v1/jobs/9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b/download'

      mockClient.get.mockResolvedValue({
        status: 'completed',
        download_url: downloadUrl,
        error: null
      })

      const result = await api.getJobStatus(jobId)

      expect(result.status).toBe('completed')
      expect(result.download_url).toBe(downloadUrl)
      expect(result.error).toBeNull()
    })

    it('returns failed status with error message', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockClient.get.mockResolvedValue({
        status: 'failed',
        download_url: null,
        error: 'Failed to generate PDF'
      })

      const result = await api.getJobStatus(jobId)

      expect(result.status).toBe('failed')
      expect(result.error).toBe('Failed to generate PDF')
    })

    it('handles API errors', async () => {
      const jobId = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      const apiError = new Error('Job not found')
      apiError.code = 'NOT_FOUND'

      mockClient.get.mockRejectedValue(apiError)

      await expect(api.getJobStatus(jobId)).rejects.toThrow('Job not found')
    })
  })
})
