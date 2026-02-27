import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { ref } from 'vue'
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

  describe('idle state', () => {
    it('renders nothing when idle', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'idle',
          jobId: null,
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.html()).toBe('<!--v-if-->')
    })
  })

  describe('pending state', () => {
    it('shows pending indicator', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'pending',
          jobId: '123',
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-status-pending"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-status-pending"]').text()).toContain('Preparing export')
    })

    it('shows spinner for pending', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'pending',
          jobId: '123',
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-spinner"]').exists()).toBe(true)
    })
  })

  describe('processing state', () => {
    it('shows processing indicator', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'processing',
          jobId: '123',
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-status-processing"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-status-processing"]').text()).toContain('Generating export')
    })

    it('shows spinner for processing', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'processing',
          jobId: '123',
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-spinner"]').exists()).toBe(true)
    })
  })

  describe('completed state', () => {
    it('shows download link', () => {
      const downloadUrl = '/api/v1/jobs/123/download'
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'completed',
          jobId: '123',
          downloadUrl,
          error: null,
          onStart: mockStartExport
        }
      })

      const downloadLink = wrapper.find('[data-testid="export-download-link"]')
      expect(downloadLink.exists()).toBe(true)
      expect(downloadLink.attributes('href')).toBe(downloadUrl)
      expect(downloadLink.text()).toContain('Download')
    })

    it('shows success message', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'completed',
          jobId: '123',
          downloadUrl: '/api/v1/jobs/123/download',
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-status-completed"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-status-completed"]').text()).toContain('Export ready')
    })

    it('shows expiration notice', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'completed',
          jobId: '123',
          downloadUrl: '/api/v1/jobs/123/download',
          error: null,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-expiration-notice"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-expiration-notice"]').text()).toContain('available for 1 hour')
    })
  })

  describe('failed state', () => {
    it('shows error message', () => {
      const errorMessage = 'Failed to generate PDF'
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'failed',
          jobId: '123',
          downloadUrl: null,
          error: errorMessage,
          onStart: mockStartExport
        }
      })

      expect(wrapper.find('[data-testid="export-status-failed"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="export-error-message"]').text()).toBe(errorMessage)
    })

    it('shows retry button', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'failed',
          jobId: '123',
          downloadUrl: null,
          error: 'Error',
          onStart: mockStartExport
        }
      })

      const retryButton = wrapper.find('[data-testid="export-retry-button"]')
      expect(retryButton.exists()).toBe(true)
      expect(retryButton.text()).toContain('Retry')
    })

    it('calls onStart when retry is clicked', async () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'failed',
          jobId: '123',
          downloadUrl: null,
          error: 'Error',
          onStart: mockStartExport
        }
      })

      await wrapper.find('[data-testid="export-retry-button"]').trigger('click')
      expect(mockStartExport).toHaveBeenCalledTimes(1)
    })
  })

  describe('accessibility', () => {
    it('has aria-live region for status updates', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'processing',
          jobId: '123',
          downloadUrl: null,
          error: null,
          onStart: mockStartExport
        }
      })

      const liveRegion = wrapper.find('[aria-live]')
      expect(liveRegion.exists()).toBe(true)
      expect(liveRegion.attributes('aria-live')).toBe('polite')
    })

    it('download link has appropriate accessibility attributes', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'completed',
          jobId: '123',
          downloadUrl: '/api/v1/jobs/123/download',
          error: null,
          onStart: mockStartExport
        }
      })

      const downloadLink = wrapper.find('[data-testid="export-download-link"]')
      expect(downloadLink.attributes('download')).toBeDefined()
    })

    it('error message has role="alert"', () => {
      const wrapper = mount(ExportJobStatus, {
        global: {
          plugins: [i18n]
        },
        props: {
          status: 'failed',
          jobId: '123',
          downloadUrl: null,
          error: 'Error',
          onStart: mockStartExport
        }
      })

      const errorContainer = wrapper.find('[data-testid="export-status-failed"]')
      expect(errorContainer.attributes('role')).toBe('alert')
    })
  })
})
