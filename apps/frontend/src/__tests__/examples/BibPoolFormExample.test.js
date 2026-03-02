import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BibPoolFormExample from '../../components/examples/BibPoolFormExample.vue'

describe('BibPoolFormExample - Validation Error Display', () => {
  let wrapper

  beforeEach(() => {
    globalThis.alert = vi.fn()
  })
  
  function createWrapper(regattaOverrides = {}, poolData = null) {
    const regatta = {
      id: 'regatta-1',
      name: 'Test Regatta',
      draw_revision: 0,
      results_revision: 0,
      ...regattaOverrides
    }
    
    return mount(BibPoolFormExample, {
      props: { 
        regatta,
        pool: poolData
      }
    })
  }

  describe('form display', () => {
    it('renders add form when pool prop is null', () => {
      wrapper = createWrapper()
      
      const heading = wrapper.find('h3')
      expect(heading.text()).toContain('Add')
    })

    it('renders edit form when pool prop is provided', () => {
      const pool = {
        name: 'Block A Pool',
        startBib: 1,
        endBib: 50
      }
      wrapper = createWrapper({}, pool)
      
      const heading = wrapper.find('h3')
      expect(heading.text()).toContain('Edit')
    })

    it('pre-fills form with pool data when editing', () => {
      const pool = {
        name: 'Block A Pool',
        startBib: 10,
        endBib: 60
      }
      wrapper = createWrapper({}, pool)
      
      const nameInput = wrapper.find('#pool-name')
      const startBibInput = wrapper.find('#start-bib')
      const endBibInput = wrapper.find('#end-bib')
      
      expect(nameInput.element.value).toBe('Block A Pool')
      expect(startBibInput.element.value).toBe('10')
      expect(endBibInput.element.value).toBe('60')
    })
  })

  describe('immutability guards', () => {
    it('shows warning banner when draw is published', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      const warning = wrapper.find('.warning-banner')
      expect(warning.exists()).toBe(true)
      expect(warning.text()).toContain('locked')
    })

    it('does not show warning when draw is not published', () => {
      wrapper = createWrapper({ draw_revision: 0 })
      
      const warning = wrapper.find('.warning-banner')
      expect(warning.exists()).toBe(false)
    })

    it('disables form inputs when draw is published', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      const nameInput = wrapper.find('#pool-name')
      const startBibInput = wrapper.find('#start-bib')
      const endBibInput = wrapper.find('#end-bib')
      
      expect(nameInput.attributes('disabled')).toBe('')
      expect(startBibInput.attributes('disabled')).toBe('')
      expect(endBibInput.attributes('disabled')).toBe('')
    })

    it('disables submit button when draw is published', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      const submitButton = wrapper.find('.btn-submit')
      expect(submitButton.attributes('disabled')).toBe('')
    })

    it('enables form when draw is not published', () => {
      wrapper = createWrapper({ draw_revision: 0 })
      
      const nameInput = wrapper.find('#pool-name')
      const submitButton = wrapper.find('.btn-submit')
      
      expect(nameInput.attributes('disabled')).toBeUndefined()
      expect(submitButton.attributes('disabled')).toBeUndefined()
    })
  })

  describe('validation error display', () => {
    it('does not show validation error initially', () => {
      wrapper = createWrapper()
      
      const error = wrapper.find('.validation-error')
      expect(error.exists()).toBe(false)
    })

    it('displays validation error with overlapping bibs', async () => {
      wrapper = createWrapper()
      
      // Set form to trigger validation error (startBib = 50)
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('#end-bib').setValue(100)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const error = wrapper.find('.validation-error')
      expect(error.exists()).toBe(true)
    })

    it('shows formatted overlapping bib numbers', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('#end-bib').setValue(100)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const bibNumbers = wrapper.find('.bib-numbers')
      expect(bibNumbers.exists()).toBe(true)
      expect(bibNumbers.text()).toContain('50')
      expect(bibNumbers.text()).toContain('51')
      expect(bibNumbers.text()).toContain('52')
      expect(bibNumbers.text()).toContain('100')
    })

    it('shows conflicting pool name', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('#end-bib').setValue(100)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const errorContent = wrapper.find('.error-content')
      expect(errorContent.text()).toContain('Block A Pool')
    })

    it('highlights invalid form fields', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('#end-bib').setValue(100)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const startBibInput = wrapper.find('#start-bib')
      const endBibInput = wrapper.find('#end-bib')
      
      expect(startBibInput.classes()).toContain('has-error')
      expect(endBibInput.classes()).toContain('has-error')
    })

    it('shows clear call-to-action in error message', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('#end-bib').setValue(100)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const errorAction = wrapper.find('.error-action')
      expect(errorAction.exists()).toBe(true)
      expect(errorAction.text()).toContain('Change')
      expect(errorAction.text()).toContain('delete')
    })
  })

  describe('form submission', () => {
    it('emits save event with form data on success', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#pool-name').setValue('Block B Pool')
      await wrapper.find('#start-bib').setValue(100)
      await wrapper.find('#end-bib').setValue(150)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()

      const saveEvents = wrapper.emitted('save')
      expect(saveEvents).toBeTruthy()
      expect(saveEvents.length).toBe(1)
      const payload = saveEvents[0][0]
      expect(payload).toMatchObject({
        name: 'Block B Pool',
        startBib: 100,
        endBib: 150
      })

      const error = wrapper.find('.validation-error')
      expect(error.exists()).toBe(false)
    })

    it('prevents submission when draw is published', async () => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      const form = wrapper.find('form')
      const submitButton = wrapper.find('.btn-submit')
      
      expect(submitButton.attributes('disabled')).toBe('')
      
      // Attempting to submit should not work
      await form.trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      expect(wrapper.emitted('save')).toBeUndefined()
    })

    it('shows loading state during submission', async () => {
      wrapper = createWrapper()
      
      // Initially not submitting
      let submitButton = wrapper.find('.btn-submit')
      expect(submitButton.text()).toBe('Save')
      
      // Note: In the actual component, isSubmitting would be managed
      // during async API calls. The test demonstrates the UI pattern.
    })
  })

  describe('cancel action', () => {
    it('emits cancel event when cancel button clicked', async () => {
      wrapper = createWrapper()
      
      const cancelButton = wrapper.find('.btn-cancel')
      await cancelButton.trigger('click')
      
      expect(wrapper.emitted('cancel')).toBeTruthy()
    })

    it('clears validation error on cancel', async () => {
      wrapper = createWrapper()
      
      // Trigger validation error
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      expect(wrapper.find('.validation-error').exists()).toBe(true)
      
      // Cancel should clear error
      await wrapper.find('.btn-cancel').trigger('click')
      await wrapper.vm.$nextTick()
      
      expect(wrapper.emitted('cancel')).toBeTruthy()
      expect(wrapper.find('.validation-error').exists()).toBe(false)
    })
  })

  describe('accessibility', () => {
    it('warning banner has alert role', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      const warning = wrapper.find('.warning-banner')
      expect(warning.attributes('role')).toBe('alert')
    })

    it('validation error has alert role', async () => {
      wrapper = createWrapper()
      
      await wrapper.find('#start-bib').setValue(50)
      await wrapper.find('form').trigger('submit.prevent')
      await wrapper.vm.$nextTick()
      
      const error = wrapper.find('.validation-error')
      expect(error.attributes('role')).toBe('alert')
    })

    it('form inputs have proper labels', () => {
      wrapper = createWrapper()
      
      const nameLabel = wrapper.find('label[for="pool-name"]')
      const startBibLabel = wrapper.find('label[for="start-bib"]')
      const endBibLabel = wrapper.find('label[for="end-bib"]')
      
      expect(nameLabel.exists()).toBe(true)
      expect(startBibLabel.exists()).toBe(true)
      expect(endBibLabel.exists()).toBe(true)
    })
  })
})
