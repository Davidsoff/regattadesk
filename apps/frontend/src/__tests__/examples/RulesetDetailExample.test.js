import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RulesetDetailExample from '../../components/examples/RulesetDetailExample.vue'

describe('RulesetDetailExample - Role-Based Visibility', () => {
  let wrapper
  
  function createWrapper(rulesetOverrides = {}, regattaOverrides = {}) {
    const ruleset = {
      id: 'ruleset-1',
      name: 'Test Ruleset',
      is_global: false,
      ageCalculation: 'Actual age at start',
      genderValidation: true,
      minAge: 12,
      maxAge: 18,
      ...rulesetOverrides
    }
    
    const regatta = {
      id: 'regatta-1',
      name: 'Test Regatta',
      draw_revision: 0,
      results_revision: 0,
      ...regattaOverrides
    }
    
    return mount(RulesetDetailExample, {
      props: { ruleset, regatta }
    })
  }

  describe('when user is super_admin', () => {
    beforeEach(() => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'super_admin'
        }
      }
    })

    it('shows promote button for non-global ruleset', async () => {
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      const promoteButton = wrapper.find('.btn-promote')
      expect(promoteButton.exists()).toBe(true)
    })

    it('does not show promote button for global ruleset', async () => {
      wrapper = createWrapper({ is_global: true })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      const promoteButton = wrapper.find('.btn-promote')
      expect(promoteButton.exists()).toBe(false)
    })

    it('promote button is enabled', async () => {
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      const promoteButton = wrapper.find('.btn-promote')
      expect(promoteButton.attributes('disabled')).toBeUndefined()
    })
  })

  describe('when user is staff (not super_admin)', () => {
    beforeEach(() => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }
    })

    it('does not show promote button', async () => {
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      const promoteButton = wrapper.find('.btn-promote')
      expect(promoteButton.exists()).toBe(false)
    })

    it('shows edit and duplicate buttons', async () => {
      wrapper = createWrapper()
      await wrapper.vm.$nextTick()
      
      const editButton = wrapper.find('.btn-edit')
      const duplicateButton = wrapper.find('.btn-duplicate')
      
      expect(editButton.exists()).toBe(true)
      expect(duplicateButton.exists()).toBe(true)
    })
  })

  describe('immutability guards', () => {
    beforeEach(() => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }
    })

    it('shows warning when draw is published', () => {
      wrapper = createWrapper({}, { draw_revision: 1 })
      
      const warning = wrapper.find('.warning-box')
      expect(warning.exists()).toBe(true)
      expect(warning.text()).toContain('Ruleset Locked')
    })

    it('does not show warning when draw is not published', () => {
      wrapper = createWrapper({}, { draw_revision: 0 })
      
      const warning = wrapper.find('.warning-box')
      expect(warning.exists()).toBe(false)
    })

    it('disables edit button when draw is published', () => {
      wrapper = createWrapper({}, { draw_revision: 1 })
      
      const editButton = wrapper.find('.btn-edit')
      expect(editButton.attributes('disabled')).toBe('')
    })

    it('enables edit button when draw is not published', () => {
      wrapper = createWrapper({}, { draw_revision: 0 })
      
      const editButton = wrapper.find('.btn-edit')
      expect(editButton.attributes('disabled')).toBeUndefined()
    })

    it('duplicate button always enabled', () => {
      wrapper = createWrapper({}, { draw_revision: 1 })
      
      const duplicateButton = wrapper.find('.btn-duplicate')
      expect(duplicateButton.attributes('disabled')).toBeUndefined()
    })
  })

  describe('403 error handling', () => {
    beforeEach(() => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }
    })

    it('does not show error initially', async () => {
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      
      const error = wrapper.find('.error-message')
      expect(error.exists()).toBe(false)
    })

    it('shows role-specific error message for unauthorized promotion', async () => {
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      // Manually trigger promoteToGlobal to test error handling
      await wrapper.vm.promoteToGlobal()
      await wrapper.vm.$nextTick()
      
      const error = wrapper.find('.error-message')
      expect(error.exists()).toBe(true)
      expect(error.text()).toContain('super_admin role')
    })
  })

  describe('badges', () => {
    it('shows global badge for global ruleset', () => {
      wrapper = createWrapper({ is_global: true })
      
      const badge = wrapper.find('.badge-global')
      expect(badge.exists()).toBe(true)
      expect(badge.text()).toContain('Global')
    })

    it('shows local badge for regatta ruleset', () => {
      wrapper = createWrapper({ is_global: false })
      
      const badge = wrapper.find('.badge-local')
      expect(badge.exists()).toBe(true)
      expect(badge.text()).toContain('Regatta')
    })
  })

  describe('accessibility', () => {
    it('warning box has alert role', () => {
      wrapper = createWrapper({}, { draw_revision: 1 })
      
      const warning = wrapper.find('.warning-box')
      expect(warning.attributes('role')).toBe('alert')
    })

    it('error message has alert role', async () => {
      globalThis.__REGATTADESK_AUTH__ = {
        user: {
          role: 'staff'
        }
      }
      
      wrapper = createWrapper({ is_global: false })
      await wrapper.vm.$nextTick()
      await wrapper.vm.loadRole()
      await wrapper.vm.$nextTick()
      
      await wrapper.vm.promoteToGlobal()
      await wrapper.vm.$nextTick()
      
      const error = wrapper.find('.error-message')
      expect(error.attributes('role')).toBe('alert')
    })
  })
})
