import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { ref } from 'vue'
import { useExportJob } from '../../composables/useExportJob'

describe('useExportJob', () => {
  let mockExportApi
  let mockRegattaId

  beforeEach(() => {
    vi.useFakeTimers()
    mockRegattaId = ref('f3cf2a08-91e0-469d-a851-41a6f3d0e3dc')
    mockExportApi = {
      requestPrintableExport: vi.fn(),
      getJobStatus: vi.fn()
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  describe('initial state', () => {
    it('starts with idle status', () => {
      const { status, jobId, downloadUrl, error } = useExportJob(mockExportApi, mockRegattaId)

      expect(status.value).toBe('idle')
      expect(jobId.value).toBeNull()
      expect(downloadUrl.value).toBeNull()
      expect(error.value).toBeNull()
    })
  })

  describe('startExport', () => {
    it('requests export and transitions to pending', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })
      mockExportApi.getJobStatus.mockResolvedValue({
        status: 'pending',
        download_url: null,
        error: null
      })

      const { startExport, status, jobId } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()

      expect(mockExportApi.requestPrintableExport).toHaveBeenCalledWith(mockRegattaId.value)
      expect(status.value).toBe('pending')
      expect(jobId.value).toBe(jobIdValue)
    })

    it('handles request errors', async () => {
      const errorMessage = 'Failed to start export'
      mockExportApi.requestPrintableExport.mockRejectedValue(new Error(errorMessage))

      const { startExport, status, error } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()

      expect(status.value).toBe('failed')
      expect(error.value).toBe(errorMessage)
    })
  })

  describe('job polling', () => {
    it('polls job status until completed', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      const downloadUrlValue = '/api/v1/jobs/9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b/download'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })

      // Mock progression: pending -> processing -> completed
      mockExportApi.getJobStatus
        .mockResolvedValueOnce({ status: 'pending', download_url: null, error: null })
        .mockResolvedValueOnce({ status: 'processing', download_url: null, error: null })
        .mockResolvedValueOnce({ status: 'completed', download_url: downloadUrlValue, error: null })

      const { startExport, status, downloadUrl } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()
      expect(status.value).toBe('pending')

      // Run first poll
      await vi.runOnlyPendingTimersAsync()
      expect(status.value).toBe('processing')

      // Run second poll
      await vi.runOnlyPendingTimersAsync()
      expect(status.value).toBe('completed')
      expect(downloadUrl.value).toBe(downloadUrlValue)
    })

    it('stops polling when job fails', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'
      const errorMessage = 'Failed to generate PDF'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })

      mockExportApi.getJobStatus
        .mockResolvedValueOnce({ status: 'pending', download_url: null, error: null })
        .mockResolvedValueOnce({ status: 'failed', download_url: null, error: errorMessage })

      const { startExport, status, error } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()
      expect(status.value).toBe('pending')

      await vi.runOnlyPendingTimersAsync()
      expect(status.value).toBe('failed')
      expect(error.value).toBe(errorMessage)

      // Verify polling stopped - no more calls
      const callCount = mockExportApi.getJobStatus.mock.calls.length
      await vi.advanceTimersByTimeAsync(10000)
      expect(mockExportApi.getJobStatus.mock.calls.length).toBe(callCount)
    })

    it('uses exponential backoff for polling', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })
      mockExportApi.getJobStatus.mockResolvedValue({
        status: 'processing',
        download_url: null,
        error: null
      })

      const { startExport } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()

      // Initial poll
      expect(mockExportApi.getJobStatus).toHaveBeenCalledTimes(1)

      // After 2 seconds (first backoff)
      await vi.runOnlyPendingTimersAsync()
      expect(mockExportApi.getJobStatus).toHaveBeenCalledTimes(2)

      // After 4 more seconds (second backoff)
      await vi.runOnlyPendingTimersAsync()
      expect(mockExportApi.getJobStatus).toHaveBeenCalledTimes(3)

      // After 8 more seconds (third backoff)
      await vi.runOnlyPendingTimersAsync()
      expect(mockExportApi.getJobStatus).toHaveBeenCalledTimes(4)
    })

    it('caps backoff at maximum interval', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })
      mockExportApi.getJobStatus.mockResolvedValue({
        status: 'processing',
        download_url: null,
        error: null
      })

      const { startExport } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()

      // Advance through multiple backoff intervals
      for (let i = 0; i < 10; i++) {
        await vi.advanceTimersByTimeAsync(30000) // Max backoff is 30s
      }

      // Should have called many times but with capped interval
      expect(mockExportApi.getJobStatus.mock.calls.length).toBeGreaterThan(5)
    })
  })

  describe('retry', () => {
    it('allows retrying after failure', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })
      mockExportApi.getJobStatus.mockResolvedValue({
        status: 'pending',
        download_url: null,
        error: null
      })

      const { startExport, status } = useExportJob(mockExportApi, mockRegattaId)

      // First attempt fails
      mockExportApi.requestPrintableExport.mockRejectedValueOnce(new Error('Network error'))
      await startExport()
      expect(status.value).toBe('failed')

      // Retry succeeds
      mockExportApi.requestPrintableExport.mockResolvedValueOnce({ job_id: jobIdValue })
      await startExport()
      expect(status.value).toBe('pending')
    })
  })

  describe('cleanup', () => {
    it('stops polling on cleanup', async () => {
      const jobIdValue = '9b7e6d5a-8c3f-4e2d-9a1b-6c5d4e3f2a1b'

      mockExportApi.requestPrintableExport.mockResolvedValue({ job_id: jobIdValue })
      mockExportApi.getJobStatus.mockResolvedValue({
        status: 'processing',
        download_url: null,
        error: null
      })

      const { startExport, cleanup } = useExportJob(mockExportApi, mockRegattaId)

      await startExport()
      const callCount = mockExportApi.getJobStatus.mock.calls.length

      cleanup()

      // Advance timers - should not poll anymore
      await vi.advanceTimersByTimeAsync(10000)
      expect(mockExportApi.getJobStatus.mock.calls.length).toBe(callCount)
    })
  })
})
