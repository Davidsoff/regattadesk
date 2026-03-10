import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createOperatorI18n } from './operatorTestUtils'
import RegattasList from '../views/operator/RegattasList.vue'

vi.mock('../operatorContext', () => ({
  resolveOperatorToken: vi.fn(() => 'token-138-secret'),
  resolveOperatorStation: vi.fn(() => 'finish-line')
}))

describe('RegattasList', () => {
  it('masks the operator token in the status text', () => {
    const wrapper = mount(RegattasList, {
      global: {
        plugins: [createOperatorI18n()]
      }
    })

    expect(wrapper.text()).toContain('••••cret')
    expect(wrapper.text()).not.toContain('token-138-secret')
  })
})
