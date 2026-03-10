import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import RegattasList from '../views/operator/RegattasList.vue'

vi.mock('../operatorContext', () => ({
  resolveOperatorToken: vi.fn(() => 'token-138-secret'),
  resolveOperatorStation: vi.fn(() => 'finish-line')
}))

function createTestI18n() {
  return createI18n({
    legacy: false,
    locale: 'en',
    messages: {
      en: {
        operator: {
          regattas: {
            title: 'Regattas',
            description: 'Select a regatta to work with',
            token_status: 'Active token {token} at station {station}.',
            access_hint: 'Open the assigned link.'
          }
        }
      }
    }
  })
}

describe('RegattasList', () => {
  it('masks the operator token in the status text', () => {
    const wrapper = mount(RegattasList, {
      global: {
        plugins: [createTestI18n()]
      }
    })

    expect(wrapper.text()).toContain('••••cret')
    expect(wrapper.text()).not.toContain('token-138-secret')
  })
})
