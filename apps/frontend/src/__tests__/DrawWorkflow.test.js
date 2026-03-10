import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { nextTick } from 'vue'

import i18n from '../i18n'
import DrawWorkflow from '../views/staff/DrawWorkflow.vue'

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'

function jsonResponse(body, status = 200) {
  const payload = JSON.stringify(body)
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get(name) {
        const key = name.toLowerCase()
        if (key === 'content-type') {
          return 'application/json'
        }
        if (key === 'content-length') {
          return String(payload.length)
        }
        return null
      }
    },
    json: vi.fn().mockResolvedValue(body),
    text: vi.fn().mockResolvedValue(payload)
  }
}

async function expectPostedRequest(call, expectedPath, expectedBody) {
  const request = globalThis.fetch.mock.calls[call][0]
  expect(request).toBeInstanceOf(Request)
  expect(new URL(request.url).pathname).toBe(expectedPath)
  expect(request.method).toBe('POST')

  if (expectedBody !== undefined) {
    await expect(request.clone().json()).resolves.toEqual(expectedBody)
  }
}

async function mountPage(regattaState = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/regattas/:regattaId/draw',
        name: 'staff-regatta-draw',
        component: DrawWorkflow
      }
    ]
  })

  await router.push(`/staff/regattas/${REGATTA_ID}/draw`)
  await router.isReady()

  return mount(DrawWorkflow, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n],
      provide: {
        regattaState
      }
    }
  })
}

describe('DrawWorkflow view (FEGAP-008-C)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    globalThis.fetch = vi.fn()
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  describe('display and state', () => {
    it('renders the draw workflow view', async () => {
      const wrapper = await mountPage()

      expect(wrapper.find('[data-testid="draw-workflow"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('Draw Workflow')
    })

    it('displays current draw status and revisions', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 2,
        resultsRevision: 1
      })

      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Published')
      const revisionDisplay = wrapper.find('[data-testid="revisions"]').text()
      expect(revisionDisplay).toContain('Current: 2')
      expect(revisionDisplay).toContain('Current: 1')
    })
  })

  describe('generate draw', () => {
    it('calls generate endpoint and updates seed/revision state', async () => {
      globalThis.fetch.mockResolvedValueOnce(
        jsonResponse({ seed: 12345, generated_entry_count: 42 })
      )

      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false,
        drawRevision: 0,
        resultsRevision: 0,
        prerequisites: {
          blocksConfigured: true,
          bibPoolsAssigned: true,
          entriesExist: true
        }
      })

      await wrapper.find('[data-testid="seed-toggle"]').trigger('click')
      const seedInput = wrapper.find('[data-testid="custom-seed-input"]')
      await seedInput.setValue('12345')
      await wrapper.find('[data-testid="generate-draw-button"]').trigger('click')
      await nextTick()

      await expectPostedRequest(0, `/api/v1/regattas/${REGATTA_ID}/draw/generate`, { seed: 12345 })

      await flushPromises()
      await nextTick()

      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Generated')
      expect(wrapper.find('[data-testid="generated-seed"]').text()).toContain('12345')
      expect(wrapper.find('[data-testid="revisions"]').text()).toContain('Current: 1')
    })

    it('shows validation error for invalid custom seed', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false,
        prerequisites: {
          blocksConfigured: true,
          bibPoolsAssigned: true,
          entriesExist: true
        }
      })

      await wrapper.find('[data-testid="seed-toggle"]').trigger('click')
      await wrapper.find('[data-testid="custom-seed-input"]').setValue('bad-seed')
      await wrapper.find('[data-testid="generate-draw-button"]').trigger('click')
      await nextTick()

      expect(globalThis.fetch).not.toHaveBeenCalled()
      expect(wrapper.find('[data-testid="error"]').text()).toContain('Seed must be a safe integer')
    })

    it('shows loading state while generate request is in flight', async () => {
      let resolveFetch
      const pendingFetch = new Promise((resolve) => {
        resolveFetch = resolve
      })
      globalThis.fetch.mockReturnValueOnce(pendingFetch)

      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false,
        prerequisites: {
          blocksConfigured: true,
          bibPoolsAssigned: true,
          entriesExist: true
        }
      })

      await wrapper.find('[data-testid="generate-draw-button"]').trigger('click')
      await nextTick()

      expect(wrapper.find('[data-testid="loading"]').exists()).toBe(true)

      resolveFetch(jsonResponse({ seed: 99, generated_entry_count: 3 }))
      await flushPromises()

      expect(wrapper.find('[data-testid="loading"]').exists()).toBe(false)
    })

    it('shows copy error when clipboard write fails', async () => {
      navigator.clipboard.writeText.mockRejectedValueOnce(new Error('denied'))

      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        generatedSeed: 101
      })

      await wrapper.find('[data-testid="generated-seed"] button').trigger('click')
      await nextTick()

      expect(wrapper.find('[data-testid="error"]').text()).toContain('Failed to copy seed to clipboard')
    })
  })

  describe('publish and unpublish', () => {
    it('publishes and updates draw/results revisions', async () => {
      globalThis.fetch.mockResolvedValueOnce(
        jsonResponse({ draw_revision: 2, results_revision: 1 })
      )

      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 1,
        resultsRevision: 0
      })

      await wrapper.find('[data-testid="publish-draw-button"]').trigger('click')
      await wrapper.find('[data-testid="publish-confirm-dialog"] button').trigger('click')
      await flushPromises()
      await nextTick()

      await expectPostedRequest(0, `/api/v1/regattas/${REGATTA_ID}/draw/publish`)

      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Published')
      const revisionsText = wrapper.find('[data-testid="revisions"]').text()
      expect(revisionsText).toContain('Current: 2')
      expect(revisionsText).toContain('Current: 1')
    })

    it('unpublishes and updates draw/results revisions', async () => {
      globalThis.fetch.mockResolvedValueOnce(
        jsonResponse({ draw_revision: 2, results_revision: 3 })
      )

      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 2,
        resultsRevision: 1,
        generatedSeed: 555
      })

      await wrapper.find('[data-testid="unpublish-draw-button"]').trigger('click')
      const unpublishDialog = wrapper.find('[data-testid="unpublish-confirm-dialog"]')
      expect(unpublishDialog.text()).toContain('update draw_revision from 2')

      await unpublishDialog.find('button').trigger('click')
      await flushPromises()
      await nextTick()

      await expectPostedRequest(0, `/api/v1/regattas/${REGATTA_ID}/draw/unpublish`)

      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Not Generated')
      const revisionsText = wrapper.find('[data-testid="revisions"]').text()
      expect(revisionsText).toContain('Current: 2')
      expect(revisionsText).toContain('Current: 3')
      expect(wrapper.find('[data-testid="generated-seed"]').exists()).toBe(false)
    })

    it('closes publish dialog when Escape is pressed', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 1,
        resultsRevision: 0
      })

      await wrapper.find('[data-testid="publish-draw-button"]').trigger('click')
      const dialog = wrapper.find('[data-testid="publish-confirm-dialog"]')
      expect(dialog.exists()).toBe(true)

      await dialog.trigger('keydown', { key: 'Escape' })
      await nextTick()

      expect(wrapper.find('[data-testid="publish-confirm-dialog"]').exists()).toBe(false)
    })
  })
})
