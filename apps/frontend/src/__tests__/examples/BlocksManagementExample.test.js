import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import BlocksManagementExample from '../../components/examples/BlocksManagementExample.vue'

describe('BlocksManagementExample - Immutability Guards', () => {
  let wrapper
  
  function createWrapper(regattaOverrides = {}) {
    const regatta = {
      id: 'regatta-1',
      name: 'Test Regatta',
      draw_revision: 0,
      results_revision: 0,
      ...regattaOverrides
    }
    
    return mount(BlocksManagementExample, {
      props: { regatta }
    })
  }

  describe('when draw is not published', () => {
    beforeEach(() => {
      wrapper = createWrapper({ draw_revision: 0 })
    })

    it('does not show warning banner', () => {
      const banner = wrapper.find('.warning-banner')
      expect(banner.exists()).toBe(false)
    })

    it('does not show lock indicators on blocks', () => {
      const indicators = wrapper.findAll('.immutable-indicator')
      expect(indicators).toHaveLength(0)
    })

    it('edit buttons are enabled', () => {
      const editButtons = wrapper.findAll('.btn-edit')
      editButtons.forEach(button => {
        expect(button.attributes('disabled')).toBeUndefined()
      })
    })

    it('delete buttons are enabled', () => {
      const deleteButtons = wrapper.findAll('.btn-delete')
      deleteButtons.forEach(button => {
        expect(button.attributes('disabled')).toBeUndefined()
      })
    })

    it('add button is enabled', () => {
      const addButton = wrapper.find('.btn-add')
      expect(addButton.attributes('disabled')).toBeUndefined()
    })
  })

  describe('when draw is published', () => {
    beforeEach(() => {
      wrapper = createWrapper({ draw_revision: 1 })
    })

    it('shows warning banner', () => {
      const banner = wrapper.find('.warning-banner')
      expect(banner.exists()).toBe(true)
      expect(banner.text()).toContain('Draw Published')
      expect(banner.text()).toContain('immutable')
    })

    it('shows lock indicators on each block', () => {
      const indicators = wrapper.findAll('.immutable-indicator')
      expect(indicators.length).toBeGreaterThan(0)
      
      indicators.forEach(indicator => {
        expect(indicator.text()).toContain('Locked')
      })
    })

    it('edit buttons are disabled', () => {
      const editButtons = wrapper.findAll('.btn-edit')
      editButtons.forEach(button => {
        expect(button.attributes('disabled')).toBe('')
      })
    })

    it('delete buttons are disabled', () => {
      const deleteButtons = wrapper.findAll('.btn-delete')
      deleteButtons.forEach(button => {
        expect(button.attributes('disabled')).toBe('')
      })
    })

    it('add button is disabled', () => {
      const addButton = wrapper.find('.btn-add')
      expect(addButton.attributes('disabled')).toBe('')
    })

    it('shows helpful tooltip on disabled buttons', () => {
      const editButton = wrapper.find('.btn-edit')
      const title = editButton.attributes('title')
      
      expect(title).toContain('Cannot edit')
      expect(title).toContain('draw publication')
      expect(title).toContain('Unpublish')
    })
  })

  describe('user interactions when published', () => {
    beforeEach(() => {
      wrapper = createWrapper({ draw_revision: 1 })
      
      // Mock window.alert
      global.alert = vi.fn()
    })

    it('prevents edit action via disabled state', () => {
      const editButton = wrapper.find('.btn-edit')
      
      // Disabled buttons in the DOM don't trigger click events
      // This is correct behavior - the button itself prevents the action
      expect(editButton.attributes('disabled')).toBe('')
      expect(editButton.attributes('title')).toContain('Cannot edit')
    })

    it('prevents delete action via disabled state', () => {
      const deleteButton = wrapper.find('.btn-delete')
      
      expect(deleteButton.attributes('disabled')).toBe('')
      expect(deleteButton.attributes('title')).toContain('Cannot edit')
    })

    it('prevents add action via disabled state', () => {
      const addButton = wrapper.find('.btn-add')
      
      expect(addButton.attributes('disabled')).toBe('')
      expect(addButton.attributes('title')).toContain('Cannot edit')
    })
  })

  describe('accessibility', () => {
    it('warning banner has alert role', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      const banner = wrapper.find('.warning-banner')
      
      expect(banner.attributes('role')).toBe('alert')
    })

    it('disabled buttons have descriptive titles', () => {
      wrapper = createWrapper({ draw_revision: 1 })
      const buttons = wrapper.findAll('button[disabled]')
      
      buttons.forEach(button => {
        const title = button.attributes('title')
        expect(title).toBeTruthy()
        expect(title.length).toBeGreaterThan(20)
      })
    })
  })
})
