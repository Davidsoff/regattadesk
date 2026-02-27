import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { nextTick } from 'vue'

import i18n from '../i18n'
import DrawWorkflow from '../views/staff/DrawWorkflow.vue'

const REGATTA_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'

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
        regattaState: regattaState
      }
    }
  })
}

describe('DrawWorkflow view (FEGAP-008-C)', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('display and state', () => {
    it('renders the draw workflow view', async () => {
      const wrapper = await mountPage()
      
      expect(wrapper.find('[data-testid="draw-workflow"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('Draw Workflow')
    })

    it('displays current draw status as not generated', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false,
        drawRevision: 0,
        resultsRevision: 0
      })
      
      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Not Generated')
    })

    it('displays current draw status as generated', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 0,
        resultsRevision: 0
      })
      
      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Generated')
    })

    it('displays current draw status as published', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 1,
        resultsRevision: 0
      })
      
      expect(wrapper.find('[data-testid="draw-status"]').text()).toContain('Published')
    })

    it('displays draw_revision and results_revision', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 2,
        resultsRevision: 1
      })
      
      const revisionDisplay = wrapper.find('[data-testid="revisions"]')
      expect(revisionDisplay.text()).toContain('Current: 2')
      expect(revisionDisplay.text()).toContain('Current: 1')
    })
  })

  describe('generate draw', () => {
    it('shows generate draw button when not generated', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false
      })
      
      const generateButton = wrapper.find('[data-testid="generate-draw-button"]')
      expect(generateButton.exists()).toBe(true)
      expect(generateButton.attributes('disabled')).toBeUndefined()
    })

    it('disables generate button when already generated and published', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true
      })
      
      const generateButton = wrapper.find('[data-testid="generate-draw-button"]')
      expect(generateButton.attributes('disabled')).toBeDefined()
    })

    it('shows optional custom seed input', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false
      })
      
      const seedToggle = wrapper.find('[data-testid="seed-toggle"]')
      expect(seedToggle.exists()).toBe(true)
      
      await seedToggle.trigger('click')
      await nextTick()
      
      const seedInput = wrapper.find('[data-testid="custom-seed-input"]')
      expect(seedInput.exists()).toBe(true)
    })

    it('displays generated seed after generation', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        generatedSeed: 'test-seed-12345'
      })
      
      const seedDisplay = wrapper.find('[data-testid="generated-seed"]')
      expect(seedDisplay.exists()).toBe(true)
      expect(seedDisplay.text()).toContain('test-seed-12345')
    })
  })

  describe('publish draw', () => {
    it('shows publish button only when draw is generated', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 0
      })
      
      const publishButton = wrapper.find('[data-testid="publish-draw-button"]')
      expect(publishButton.exists()).toBe(true)
      expect(publishButton.attributes('disabled')).toBeUndefined()
    })

    it('disables publish button when draw not generated', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false
      })
      
      const publishButton = wrapper.find('[data-testid="publish-draw-button"]')
      expect(publishButton.attributes('disabled')).toBeDefined()
    })

    it('shows confirmation dialog when publish is clicked', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 1
      })
      
      await wrapper.find('[data-testid="publish-draw-button"]').trigger('click')
      await nextTick()
      
      const dialog = wrapper.find('[data-testid="publish-confirm-dialog"]')
      expect(dialog.exists()).toBe(true)
      expect(dialog.text()).toContain('from 1 to 2')
    })

    it('shows warning about revision increment in confirmation', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: false,
        drawRevision: 1
      })
      
      await wrapper.find('[data-testid="publish-draw-button"]').trigger('click')
      await nextTick()
      
      const dialog = wrapper.find('[data-testid="publish-confirm-dialog"]')
      expect(dialog.text()).toContain('Published draws enable public schedule visibility')
    })
  })

  describe('unpublish draw', () => {
    it('shows unpublish button only when draw is published', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 2
      })
      
      const unpublishButton = wrapper.find('[data-testid="unpublish-draw-button"]')
      expect(unpublishButton.exists()).toBe(true)
      expect(unpublishButton.attributes('disabled')).toBeUndefined()
    })

    it('disables unpublish button when no published draw', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false
      })
      
      const unpublishButton = wrapper.find('[data-testid="unpublish-draw-button"]')
      expect(unpublishButton.attributes('disabled')).toBeDefined()
    })

    it('shows confirmation dialog when unpublish is clicked', async () => {
      const wrapper = await mountPage({
        drawGenerated: true,
        drawPublished: true,
        drawRevision: 2
      })
      
      await wrapper.find('[data-testid="unpublish-draw-button"]').trigger('click')
      await nextTick()
      
      const dialog = wrapper.find('[data-testid="unpublish-confirm-dialog"]')
      expect(dialog.exists()).toBe(true)
      expect(dialog.text()).toContain('from 2 to 1')
    })
  })

  describe('prerequisites', () => {
    it('displays prerequisites checklist', async () => {
      const wrapper = await mountPage({
        prerequisites: {
          blocksConfigured: true,
          bibPoolsAssigned: true,
          entriesExist: true
        }
      })
      
      const prerequisites = wrapper.find('[data-testid="prerequisites"]')
      expect(prerequisites.exists()).toBe(true)
      expect(prerequisites.text()).toContain('Blocks configured')
      expect(prerequisites.text()).toContain('Bib pools assigned')
      expect(prerequisites.text()).toContain('Entries exist')
    })

    it('disables generate when prerequisites not met', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        prerequisites: {
          blocksConfigured: false,
          bibPoolsAssigned: false,
          entriesExist: false
        }
      })
      
      const generateButton = wrapper.find('[data-testid="generate-draw-button"]')
      expect(generateButton.attributes('disabled')).toBeDefined()
    })
  })

  describe('loading states', () => {
    it('shows loading state during generate action', async () => {
      const wrapper = await mountPage({
        drawGenerated: false,
        drawPublished: false,
        prerequisites: {
          blocksConfigured: true,
          bibPoolsAssigned: true,
          entriesExist: true
        }
      })
      
      // Mock a slow API call
      const mockApi = vi.spyOn(wrapper.vm, 'generateDraw')
      mockApi.mockImplementation(async () => {
        wrapper.vm.loading = true
        await new Promise(resolve => setTimeout(resolve, 100))
        wrapper.vm.loading = false
      })
      
      // Trigger generate action but don't await yet
      const generatePromise = wrapper.vm.generateDraw()
      await nextTick()
      
      // Check loading state is true
      expect(wrapper.vm.loading).toBe(true)
      
      // Wait for completion
      await generatePromise
      await nextTick()
      
      // Loading should be false now
      expect(wrapper.vm.loading).toBe(false)
    })
  })
})
