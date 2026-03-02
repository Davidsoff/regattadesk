import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { 
  isDrawPublished, 
  canEditAfterDraw,
  getImmutabilityMessage,
  useDrawImmutability
} from '../../composables/useDrawImmutability'

describe('useDrawImmutability', () => {
  describe('isDrawPublished', () => {
    it('returns false when draw_revision is 0', () => {
      const regatta = { draw_revision: 0, results_revision: 0 }
      expect(isDrawPublished(regatta)).toBe(false)
    })

    it('returns true when draw_revision is greater than 0', () => {
      const regatta = { draw_revision: 1, results_revision: 0 }
      expect(isDrawPublished(regatta)).toBe(true)
    })

    it('returns true when draw_revision is 5', () => {
      const regatta = { draw_revision: 5, results_revision: 3 }
      expect(isDrawPublished(regatta)).toBe(true)
    })

    it('returns false for null regatta', () => {
      expect(isDrawPublished(null)).toBe(false)
    })

    it('returns false for undefined regatta', () => {
      expect(isDrawPublished(undefined)).toBe(false)
    })

    it('returns false when draw_revision is missing', () => {
      const regatta = { results_revision: 0 }
      expect(isDrawPublished(regatta)).toBe(false)
    })
  })

  describe('canEditAfterDraw', () => {
    it('returns true when draw is not published', () => {
      const regatta = { draw_revision: 0 }
      expect(canEditAfterDraw(regatta)).toBe(true)
    })

    it('returns false when draw is published', () => {
      const regatta = { draw_revision: 1 }
      expect(canEditAfterDraw(regatta)).toBe(false)
    })

    it('returns true for null regatta (defensive)', () => {
      expect(canEditAfterDraw(null)).toBe(true)
    })
  })

  describe('getImmutabilityMessage', () => {
    it('returns null when draw is not published', () => {
      const regatta = { draw_revision: 0 }
      expect(getImmutabilityMessage(regatta)).toBeNull()
    })

    it('returns warning message when draw is published', () => {
      const regatta = { draw_revision: 1 }
      const message = getImmutabilityMessage(regatta)
      
      expect(message).toBeTruthy()
      expect(message).toContain('Cannot edit')
      expect(message).toContain('draw publication')
    })

    it('message mentions unpublish workflow', () => {
      const regatta = { draw_revision: 1 }
      const message = getImmutabilityMessage(regatta)
      
      expect(message).toContain('Unpublish')
      expect(message).toContain('Draw Workflow')
    })

    it('returns default message for null regatta', () => {
      const message = getImmutabilityMessage(null)
      expect(message).toBeNull()
    })
  })

  describe('immutability with custom resource type', () => {
    it('getImmutabilityMessage accepts resource type parameter', () => {
      const regatta = { draw_revision: 1 }
      
      const blockMessage = getImmutabilityMessage(regatta, 'block')
      expect(blockMessage).toContain('block')
      
      const bibPoolMessage = getImmutabilityMessage(regatta, 'bib pool')
      expect(bibPoolMessage).toContain('bib pool')
    })

    it('uses default resource type when not specified', () => {
      const regatta = { draw_revision: 1 }
      const message = getImmutabilityMessage(regatta)
      
      expect(message).toBeTruthy()
      expect(typeof message).toBe('string')
    })
  })

  describe('useDrawImmutability', () => {
    it('returns reactive state that updates with regattaRef changes', () => {
      const regattaRef = ref({ draw_revision: 0 })
      const { isPublished, canEdit, getMessage } = useDrawImmutability(regattaRef)

      expect(isPublished.value).toBe(false)
      expect(canEdit.value).toBe(true)
      expect(getMessage('ruleset')).toBeNull()

      regattaRef.value.draw_revision = 1

      expect(isPublished.value).toBe(true)
      expect(canEdit.value).toBe(false)
      expect(getMessage('ruleset')).toContain('Cannot edit ruleset after draw publication')
    })
  })
})
