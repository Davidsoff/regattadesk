import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ExportJobStatus from '../components/export/ExportJobStatus.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  fallbackLocale: 'en',
  messages: {
    en: {
      common: {
        download: 'Download'
      },
      export: {
        status: {
          preparing: 'Preparing export...',
          generating: 'Generating export...',
          ready: 'Export ready',
          failed: 'Export failed'
        },
        retry: 'Retry',
        expiration_notice: 'Download available for 1 hour'
      }
    }
  }
})

describe('ExportJobStatus', () => {
  let mockStartExport

  beforeEach(() => {
    mockStartExport = vi.fn()
  })

  function mountStatus(overrides = {}) {
    return mount(ExportJobStatus, {
      global: {
        plugins: [i18n]
      },
      props: {
        status: 'idle',
        jobId: null,
        downloadUrl: null,
        error: null,
        onStart: mockStartExport,
        ...overrides
      }
    })
  }

  describe('idle state', () => {
    it('renders nothing when idle', () => {
      const wrapper = mountStatus()

      expect(wrapper.html()).toBe('<!--v-if-->')
    })
  })

  describe('pending state', () => {
    it('shows pending indicator and spinner', () => {
      const wrapper = mountStatus({
        status: 'pending',
        jobId: '123'
      })

      const indicator = wrapper.find('[data-testid="export-status-pending"]')
      expect(indicator.exists()).toBe(true)
      expect(indicator.text()).toContain('Preparing export')
      expect(wrapper.find('[data-testid="export-spinner"]').exists()).toBe(true)
    })
  })

  describe('processing state', () => {
    it('shows processing indicator and spinner', () => {
      const wrapper = mountStatus({
        status: 'processing',
        jobId: '123'
      })

      const indicator = wrapper.find('[data-testid="export-status-processing"]')
      expect(indicator.exists()).toBe(true)
      expect(indicator.text()).toContain('Generating export')
      expect(wrapper.find('[data-testid="export-spinner"]').exists()).toBe(true)
    })
  })

  describe('completed state', () => {
    it('shows download link', () => {
      const downloadUrl = '/api/v1/jobs/123/download'
      const wrapper = mountStatus({
        status: 'completed',
        jobId: '123',
        downloadUrl
      })

      const downloadLink = wrapper.find('[data-testid="export-download-link"]')
      expect(downloadLink.exists()).toBe(true)
      expect(downloadLink.attributes('href')).toBe(downloadUrl)
      expect(downloadLink.text()).toContain('Download')
    })

    it('shows success message and expiration notice', () => {
      const wrapper = mountStatus({
        status: 'completed',
        jobId: '123',
        downloadUrl: '/api/v1/jobs/123/download'
      })

      const success = wrapper.find('[data-testid="export-status-completed"]')
      expect(success.exists()).toBe(true)
      expect(success.text()).toContain('Export ready')

      const expiration = wrapper.find('[data-testid="export-expiration-notice"]')
      expect(expiration.exists()).toBe(true)
      expect(expiration.text()).toContain('available for 1 hour')
    })
  })

  describe('failed state', () => {
    it('shows error message', () => {
      const errorMessage = 'Failed to generate PDF'
      const wrapper = mountStatus({
        status: 'failed',
        jobId: '123',
        error: errorMessage
      })

      expect(wrapper.find('[data-testid="export-status-failed"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-error-message"]').text()).toBe(errorMessage)
    })

    it('shows retry button and retries on click', async () => {
      const wrapper = mountStatus({
        status: 'failed',
        jobId: '123',
        error: 'Error'
      })

      const retryButton = wrapper.find('[data-testid="export-retry-button"]')
      expect(retryButton.exists()).toBe(true)
      expect(retryButton.text()).toContain('Retry')

      await retryButton.trigger('click')
      expect(mockStartExport).toHaveBeenCalledTimes(1)
    })
  })

  describe('accessibility', () => {
    it('has aria-live region for status updates', () => {
      const wrapper = mountStatus({
        status: 'processing',
        jobId: '123'
      })

      const liveRegion = wrapper.find('[aria-live]')
      expect(liveRegion.exists()).toBe(true)
      expect(liveRegion.attributes('aria-live')).toBe('polite')
    })

    it('download link has appropriate accessibility attributes', () => {
      const wrapper = mountStatus({
        status: 'completed',
        jobId: '123',
        downloadUrl: '/api/v1/jobs/123/download'
      })

      const downloadLink = wrapper.find('[data-testid="export-download-link"]')
      expect(downloadLink.attributes('download')).toBeDefined()
    })

    it('error message has role="alert"', () => {
      const wrapper = mountStatus({
        status: 'failed',
        jobId: '123',
        error: 'Error'
      })

      const errorContainer = wrapper.find('[data-testid="export-status-failed"]')
      expect(errorContainer.attributes('role')).toBe('alert')
    })
  })
})
