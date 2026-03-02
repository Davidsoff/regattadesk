import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import RulesetDetail from '../views/staff/RulesetDetail.vue'

function buildI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    fallbackLocale: 'en',
    messages: {
      en: {
        common: {
          back: 'Back'
        },
        rulesets: {
          title: 'Rulesets',
          createButton: 'Create Ruleset',
          emptyState: 'No rulesets found'
        }
      }
    }
  })
}

describe('RulesetDetail.vue', () => {
  it('renders create mode route', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/staff/rulesets/new', name: 'staff-ruleset-create', component: RulesetDetail }]
    })
    await router.push('/staff/rulesets/new')
    await router.isReady()

    const wrapper = mount(RulesetDetail, {
      global: {
        plugins: [router, buildI18n()]
      }
    })

    expect(wrapper.text()).toContain('Create Ruleset')
  })

  it('renders detail mode route with ruleset id', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/staff/rulesets/:rulesetId', name: 'staff-ruleset-detail', component: RulesetDetail }]
    })
    await router.push('/staff/rulesets/550e8400-e29b-41d4-a716-446655440000')
    await router.isReady()

    const wrapper = mount(RulesetDetail, {
      global: {
        plugins: [router, buildI18n()]
      }
    })

    expect(wrapper.text()).toContain('Rulesets')
    expect(wrapper.text()).toContain('550e8400-e29b-41d4-a716-446655440000')
  })
})
