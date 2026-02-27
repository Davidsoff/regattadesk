/**
 * Composable for managing async export job lifecycle with polling.
 * 
 * Features:
 * - Requests export jobs
 * - Polls job status with exponential backoff
 * - Handles job completion, failure, and retry
 * - Manual cleanup required in non-component contexts
 */

import { ref, unref, onUnmounted, getCurrentInstance } from 'vue'

const INITIAL_POLL_DELAY_MS = 2000 // 2 seconds
const MAX_POLL_DELAY_MS = 30000 // 30 seconds
const BACKOFF_MULTIPLIER = 2

/**
 * Composable for export job management with polling.
 * 
 * @param {object} exportApi - Export API instance
 * @param {import('vue').Ref<string>|string} regattaId - Regatta UUID (reactive or static)
 * @returns {object} Export job state and actions
 */
export function useExportJob(exportApi, regattaId) {
  // Job state
  const status = ref('idle') // idle | pending | processing | completed | failed
  const jobId = ref(null)
  const downloadUrl = ref(null)
  const error = ref(null)

  // Polling state
  let pollTimeoutId = null
  let currentPollDelay = INITIAL_POLL_DELAY_MS

  /**
   * Clear any active polling timeout.
   */
  function clearPolling() {
    if (pollTimeoutId !== null) {
      clearTimeout(pollTimeoutId)
      pollTimeoutId = null
    }
  }

  /**
   * Reset state for a new export attempt.
   */
  function resetState() {
    clearPolling()
    status.value = 'idle'
    jobId.value = null
    downloadUrl.value = null
    error.value = null
    currentPollDelay = INITIAL_POLL_DELAY_MS
  }

  /**
   * Poll job status and update state.
   */
  async function pollJobStatus() {
    if (!jobId.value) {
      return
    }

    try {
      const response = await exportApi.getJobStatus(jobId.value)

      status.value = response.status

      if (response.status === 'completed') {
        downloadUrl.value = response.download_url
        clearPolling()
      } else if (response.status === 'failed') {
        error.value = response.error
        clearPolling()
      } else {
        // Continue polling with exponential backoff
        currentPollDelay = Math.min(currentPollDelay * BACKOFF_MULTIPLIER, MAX_POLL_DELAY_MS)
        pollTimeoutId = setTimeout(() => {
          pollJobStatus()
        }, currentPollDelay)
      }
    } catch (err) {
      status.value = 'failed'
      error.value = err.message || 'Failed to check job status'
      clearPolling()
    }
  }

  /**
   * Start an export job and begin polling.
   */
  async function startExport() {
    resetState()

    try {
      const regattaIdValue = unref(regattaId)
      const response = await exportApi.requestPrintableExport(regattaIdValue)

      jobId.value = response.job_id

      // Initial status check
      await pollJobStatus()
    } catch (err) {
      status.value = 'failed'
      error.value = err.message || 'Failed to start export'
    }
  }

  /**
   * Cleanup function to stop polling.
   */
  function cleanup() {
    clearPolling()
  }

  // Cleanup on component unmount (only if in component context)
  if (getCurrentInstance()) {
    onUnmounted(() => {
      cleanup()
    })
  }

  return {
    // State
    status,
    jobId,
    downloadUrl,
    error,

    // Actions
    startExport,
    cleanup
  }
}
