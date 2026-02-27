import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import RulesetsList from '../views/staff/RulesetsList.vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

describe('RulesetsList.vue', () => {
  let wrapper
  let mockApi

  beforeEach(() => {
    mockApi = {
      listRulesets: vi.fn()
    }
    
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div>Home</div>' } },
        { path: '/staff/rulesets', name: 'staff-rulesets', component: RulesetsList },
        { path: '/staff/rulesets/:rulesetId', name: 'staff-ruleset-detail', component: { template: '<div>Detail</div>' } }
      ]
    })

    const i18n = createI18n({
      legacy: false,
      locale: 'en',
      fallbackLocale: 'en',
      messages: {
        en: {
          rulesets: {
            title: 'Rulesets',
            createButton: 'Create Ruleset',
            tableCaption: 'List of rulesets',
            emptyState: 'No rulesets found',
            loadError: 'Failed to load rulesets',
            filter: {
              all: 'All rulesets',
              global: 'Global only',
              regattaOwned: 'Regatta-owned only'
            },
            columns: {
              name: 'Name',
              version: 'Version',
              ageCalculation: 'Age Calculation',
              scope: 'Scope',
              description: 'Description'
            },
            scope: {
              global: 'Global',
              regattaOwned: 'Regatta-owned'
            },
            ageCalculation: {
              actualAtStart: 'Actual age at start',
              ageAsOfJan1: 'Age as of January 1'
            }
          }
        }
      }
    })

    wrapper = mount(RulesetsList, {
      global: {
        plugins: [router, i18n],
        mocks: {
          $api: mockApi
        }
      }
    })
  })

  it('renders list of rulesets', async () => {
    const mockRulesets = [
      {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'FISA Rules',
        version: '2024',
        description: 'FISA rowing rules',
        age_calculation_type: 'actual_at_start',
        is_global: true
      },
      {
        id: '660e8400-e29b-41d4-a716-446655440000',
        name: 'Custom Rules',
        version: 'v1',
        description: null,
        age_calculation_type: 'age_as_of_jan_1',
        is_global: false
      }
    ]
    mockApi.listRulesets.mockResolvedValue({ data: mockRulesets })

    await wrapper.vm.loadRulesets()
    await wrapper.vm.$nextTick()

    expect(mockApi.listRulesets).toHaveBeenCalled()
    expect(wrapper.text()).toContain('FISA Rules')
    expect(wrapper.text()).toContain('Custom Rules')
  })

  it('filters by global rulesets', async () => {
    mockApi.listRulesets.mockResolvedValue({ data: [] })

    wrapper.vm.filterGlobal = true
    await wrapper.vm.loadRulesets()

    expect(mockApi.listRulesets).toHaveBeenCalledWith({ is_global: true })
  })

  it('filters by regatta-owned rulesets', async () => {
    mockApi.listRulesets.mockResolvedValue({ data: [] })

    wrapper.vm.filterGlobal = false
    await wrapper.vm.loadRulesets()

    expect(mockApi.listRulesets).toHaveBeenCalledWith({ is_global: false })
  })

  it('shows empty state when no rulesets', async () => {
    mockApi.listRulesets.mockResolvedValue({ data: [] })

    await wrapper.vm.loadRulesets()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('No rulesets found')
  })

  it('displays loading state', async () => {
    mockApi.listRulesets.mockImplementation(() => new Promise(() => {}))

    const loadingPromise = wrapper.vm.loadRulesets()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.loading).toBe(true)

    // Clean up
    mockApi.listRulesets.mockResolvedValue({ data: [] })
    await loadingPromise
  })

  it('displays error message on load failure', async () => {
    mockApi.listRulesets.mockRejectedValue(new Error('Network error'))

    await wrapper.vm.loadRulesets()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.error).toBeTruthy()
    expect(wrapper.text()).toContain('Failed to load rulesets')
  })

  it('navigates to ruleset detail on row click', async () => {
    const mockRulesets = [
      {
        id: '550e8400-e29b-41d4-a716-446655440000',
        name: 'FISA Rules',
        version: '2024',
        age_calculation_type: 'actual_at_start',
        is_global: true
      }
    ]
    mockApi.listRulesets.mockResolvedValue({ data: mockRulesets })

    await wrapper.vm.loadRulesets()
    await wrapper.vm.$nextTick()

    const navigateSpy = vi.spyOn(wrapper.vm.$router, 'push')
    await wrapper.vm.navigateToDetail('550e8400-e29b-41d4-a716-446655440000')

    expect(navigateSpy).toHaveBeenCalledWith({
      name: 'staff-ruleset-detail',
      params: { rulesetId: '550e8400-e29b-41d4-a716-446655440000' }
    })
  })

  it('shows "Create Ruleset" button', () => {
    expect(wrapper.text()).toContain('Create Ruleset')
  })
})
