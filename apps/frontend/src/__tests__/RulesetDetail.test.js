import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '../i18n'
import RulesetDetail from '../views/staff/RulesetDetail.vue'

const RULESET_ID = 'f3cf2a08-91e0-469d-a851-41a6f3d0e3dc'

const mockRuleset = {
  id: RULESET_ID,
  name: 'Test Ruleset',
  version: '2024',
  description: 'Test description',
  age_calculation_type: 'actual_at_start',
  is_global: false
}

// Mock the draw API
const mockDrawApi = {
  getRuleset: vi.fn(),
  updateRuleset: vi.fn(),
  duplicateRuleset: vi.fn(),
  promoteRuleset: vi.fn()
}

// Mock API module
vi.mock('../api', () => ({
  createApiClient: vi.fn(() => ({})),
  createDrawApi: vi.fn(() => mockDrawApi)
}))

async function mountPage(options = {}) {
  const { userRole = 'staff' } = options

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/staff/rulesets/:rulesetId',
        name: 'staff-ruleset-detail',
        component: RulesetDetail
      }
    ]
  })

  await router.push(`/staff/rulesets/${RULESET_ID}`)
  await router.isReady()

  // Set user role in global context for testing
  globalThis.__REGATTADESK_AUTH__ = {
    staffAuthenticated: true,
    userRole: userRole
  }

  return mount(RulesetDetail, {
    attachTo: document.body,
    global: {
      plugins: [router, i18n]
    }
  })
}

describe('RulesetDetail view (FEGAP-008-A)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    
    // Reset mock implementations
    mockDrawApi.getRuleset.mockResolvedValue(mockRuleset)
  })

  afterEach(() => {
    document.body.innerHTML = ''
    delete globalThis.__REGATTADESK_AUTH__
  })

  describe('View rendering', () => {
    it('loads and displays ruleset details', async () => {
      const wrapper = await mountPage()
      
      // Wait for data to load
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalledWith(RULESET_ID)
      })

      // Check that form fields are rendered with data
      expect(wrapper.find('input[name="name"]').element.value).toBe('Test Ruleset')
      expect(wrapper.find('input[name="version"]').element.value).toBe('2024')
      expect(wrapper.find('textarea[name="description"]').element.value).toBe('Test description')
      expect(wrapper.find('select[name="age_calculation_type"]').element.value).toBe('actual_at_start')
    })

    it('displays age calculation type as human-readable label', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalledWith(RULESET_ID)
      })

      const ageCalcSelect = wrapper.find('select[name="age_calculation_type"]')
      const options = ageCalcSelect.findAll('option')
      
      // Check that options have human-readable labels from i18n
      expect(options.length).toBeGreaterThan(1)
      expect(options[1].text()).toMatch(/Actual Age at Start|Werkelijke leeftijd bij start/)
    })

    it('displays is_global status clearly', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalledWith(RULESET_ID)
      })

      // Should display global status
      const statusElement = wrapper.find('[data-testid="global-status"]')
      expect(statusElement.exists()).toBe(true)
      expect(statusElement.text()).toMatch(/Regatta-Owned|Regatta-eigendom/)
    })

    it('shows loading state while fetching data', async () => {
      mockDrawApi.getRuleset.mockImplementation(() => new Promise(resolve => setTimeout(resolve, 100)))
      
      const wrapper = await mountPage()

      // Should show loading indicator
      expect(wrapper.find('[data-testid="loading"]').exists()).toBe(true)
    })

    it('handles load errors gracefully', async () => {
      mockDrawApi.getRuleset.mockRejectedValue(new Error('Failed to load'))
      
      const wrapper = await mountPage()

      await vi.waitFor(() => {
        const errorMessage = wrapper.find('[role="alert"]')
        expect(errorMessage.exists()).toBe(true)
        expect(errorMessage.text()).toMatch(/Failed to load|laden mislukt/i)
      })
    })
  })

  describe('Form validation', () => {
    it('validates required fields before submission', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      // Clear required field
      await wrapper.find('input[name="name"]').setValue('')
      
      // Try to save
      const saveButton = wrapper.find('button[data-testid="save-button"]')
      await saveButton.trigger('click')

      // Should show validation error
      const validationError = wrapper.find('[role="alert"]')
      expect(validationError.exists()).toBe(true)
      expect(validationError.text()).toMatch(/required|verplicht/i)
      
      // Should not call API
      expect(mockDrawApi.updateRuleset).not.toHaveBeenCalled()
    })

    it('validates name field is required', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('input[name="name"]').setValue('')
      await wrapper.find('button[data-testid="save-button"]').trigger('click')

      expect(wrapper.find('[role="alert"]').text()).toMatch(/name.*required|naam.*verplicht/i)
    })

    it('validates version field is required', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('input[name="version"]').setValue('')
      await wrapper.find('button[data-testid="save-button"]').trigger('click')

      expect(wrapper.find('[role="alert"]').text()).toMatch(/version.*required|versie.*verplicht/i)
    })

    it('validates age_calculation_type field is required', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('select[name="age_calculation_type"]').setValue('')
      await wrapper.find('button[data-testid="save-button"]').trigger('click')

      expect(wrapper.find('[role="alert"]').text()).toMatch(/age calculation.*required|leeftijdsberekeningstype.*verplicht/i)
    })

    it('allows submission with valid data', async () => {
      mockDrawApi.updateRuleset.mockResolvedValue({ ...mockRuleset, name: 'Updated Name' })
      
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('input[name="name"]').setValue('Updated Name')
      await wrapper.find('button[data-testid="save-button"]').trigger('click')

      await vi.waitFor(() => {
        expect(mockDrawApi.updateRuleset).toHaveBeenCalledWith(RULESET_ID, {
          name: 'Updated Name',
          version: '2024',
          description: 'Test description',
          age_calculation_type: 'actual_at_start'
        })
      })
    })
  })

  describe('Duplicate action', () => {
    it('shows duplicate dialog when duplicate button clicked', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      const duplicateButton = wrapper.find('button[data-testid="duplicate-button"]')
      expect(duplicateButton.exists()).toBe(true)
      
      await duplicateButton.trigger('click')

      const dialog = wrapper.find('[data-testid="duplicate-dialog"]')
      expect(dialog.exists()).toBe(true)
      expect(dialog.element.tagName).toBe('DIALOG')
    })

    it('duplicates ruleset with new name and version', async () => {
      const newRuleset = {
        id: 'new-id',
        name: 'Duplicated Ruleset',
        version: '2025',
        description: 'Test description',
        age_calculation_type: 'actual_at_start',
        is_global: false
      }
      mockDrawApi.duplicateRuleset.mockResolvedValue(newRuleset)
      
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="duplicate-button"]').trigger('click')
      
      const dialog = wrapper.find('[data-testid="duplicate-dialog"]')
      await dialog.find('input[name="new_name"]').setValue('Duplicated Ruleset')
      await dialog.find('input[name="new_version"]').setValue('2025')
      await dialog.find('button[data-testid="duplicate-confirm"]').trigger('click')

      await vi.waitFor(() => {
        expect(mockDrawApi.duplicateRuleset).toHaveBeenCalledWith(RULESET_ID, {
          new_name: 'Duplicated Ruleset',
          new_version: '2025'
        })
      })
    })

    it('validates duplicate form requires new name and version', async () => {
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="duplicate-button"]').trigger('click')
      
      const dialog = wrapper.find('[data-testid="duplicate-dialog"]')
      await dialog.find('button[data-testid="duplicate-confirm"]').trigger('click')

      // Should show validation error
      const error = dialog.find('[role="alert"]')
      expect(error.exists()).toBe(true)
      expect(mockDrawApi.duplicateRuleset).not.toHaveBeenCalled()
    })

    it('handles duplicate errors gracefully', async () => {
      mockDrawApi.duplicateRuleset.mockRejectedValue(new Error('Name already exists'))
      
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="duplicate-button"]').trigger('click')
      
      const dialog = wrapper.find('[data-testid="duplicate-dialog"]')
      await dialog.find('input[name="new_name"]').setValue('Existing Name')
      await dialog.find('input[name="new_version"]').setValue('2024')
      await dialog.find('button[data-testid="duplicate-confirm"]').trigger('click')

      await vi.waitFor(() => {
        const error = dialog.find('[role="alert"]')
        expect(error.exists()).toBe(true)
        expect(error.text()).toMatch(/error|mislukt/i)
      })
    })
  })

  describe('Promote action (super_admin only)', () => {
    it('shows promote button only for super_admin users', async () => {
      const wrapper = await mountPage({ userRole: 'super_admin' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      const promoteButton = wrapper.find('button[data-testid="promote-button"]')
      expect(promoteButton.exists()).toBe(true)
    })

    it('hides promote button for non-super_admin users', async () => {
      const wrapper = await mountPage({ userRole: 'staff' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      const promoteButton = wrapper.find('button[data-testid="promote-button"]')
      expect(promoteButton.exists()).toBe(false)
    })

    it('shows confirmation dialog before promoting', async () => {
      const wrapper = await mountPage({ userRole: 'super_admin' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="promote-button"]').trigger('click')

      const dialog = wrapper.find('[data-testid="promote-dialog"]')
      expect(dialog.exists()).toBe(true)
      expect(dialog.text()).toMatch(/promote.*global/i)
    })

    it('promotes ruleset to global on confirmation', async () => {
      const promotedRuleset = { ...mockRuleset, is_global: true }
      mockDrawApi.promoteRuleset.mockResolvedValue(promotedRuleset)
      
      const wrapper = await mountPage({ userRole: 'super_admin' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="promote-button"]').trigger('click')
      await wrapper.find('button[data-testid="promote-confirm"]').trigger('click')

      await vi.waitFor(() => {
        expect(mockDrawApi.promoteRuleset).toHaveBeenCalledWith(RULESET_ID)
      })
    })

    it('handles 403 forbidden errors with appropriate message', async () => {
      const forbiddenError = new Error('Forbidden')
      forbiddenError.status = 403
      mockDrawApi.promoteRuleset.mockRejectedValue(forbiddenError)
      
      const wrapper = await mountPage({ userRole: 'super_admin' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="promote-button"]').trigger('click')
      await wrapper.find('button[data-testid="promote-confirm"]').trigger('click')

      await vi.waitFor(() => {
        const error = wrapper.find('[role="alert"]')
        expect(error.exists()).toBe(true)
        expect(error.text()).toMatch(/permission|forbidden|toestemming/i)
      })
    })

    it('hides promote button for already-global rulesets', async () => {
      const globalRuleset = { ...mockRuleset, is_global: true }
      mockDrawApi.getRuleset.mockResolvedValue(globalRuleset)
      
      const wrapper = await mountPage({ userRole: 'super_admin' })
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      const promoteButton = wrapper.find('button[data-testid="promote-button"]')
      expect(promoteButton.exists()).toBe(false)
    })
  })

  describe('Loading states', () => {
    it('shows loading state during save operation', async () => {
      mockDrawApi.updateRuleset.mockImplementation(() => new Promise(resolve => setTimeout(resolve, 100)))
      
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('input[name="name"]').setValue('Updated Name')
      await wrapper.find('button[data-testid="save-button"]').trigger('click')

      // Button should show loading state
      const saveButton = wrapper.find('button[data-testid="save-button"]')
      expect(saveButton.attributes('disabled')).toBeDefined()
      expect(saveButton.text()).toMatch(/loading|laden/i)
    })

    it('shows loading state during duplicate operation', async () => {
      mockDrawApi.duplicateRuleset.mockImplementation(() => new Promise(resolve => setTimeout(resolve, 100)))
      
      const wrapper = await mountPage()
      
      await vi.waitFor(() => {
        expect(mockDrawApi.getRuleset).toHaveBeenCalled()
      })

      await wrapper.find('button[data-testid="duplicate-button"]').trigger('click')
      
      const dialog = wrapper.find('[data-testid="duplicate-dialog"]')
      await dialog.find('input[name="new_name"]').setValue('New Name')
      await dialog.find('input[name="new_version"]').setValue('2025')
      await dialog.find('button[data-testid="duplicate-confirm"]').trigger('click')

      const confirmButton = dialog.find('button[data-testid="duplicate-confirm"]')
      expect(confirmButton.attributes('disabled')).toBeDefined()
    })
  })
})
