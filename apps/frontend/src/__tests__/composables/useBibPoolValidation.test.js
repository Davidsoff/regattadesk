import { describe, it, expect } from 'vitest'
import { 
  parseBibPoolValidationError,
  formatOverlappingBibs,
  isBibPoolValidationError
} from '../../composables/useBibPoolValidation'

describe('useBibPoolValidation', () => {
  describe('isBibPoolValidationError', () => {
    it('returns true for BIB_POOL_VALIDATION_ERROR code', () => {
      const error = { code: 'BIB_POOL_VALIDATION_ERROR' }
      expect(isBibPoolValidationError(error)).toBe(true)
    })

    it('returns false for other error codes', () => {
      const error = { code: 'VALIDATION_ERROR' }
      expect(isBibPoolValidationError(error)).toBe(false)
    })

    it('returns false for null error', () => {
      expect(isBibPoolValidationError(null)).toBe(false)
    })

    it('returns false for undefined error', () => {
      expect(isBibPoolValidationError(undefined)).toBe(false)
    })

    it('returns false for error without code', () => {
      const error = { message: 'Some error' }
      expect(isBibPoolValidationError(error)).toBe(false)
    })
  })

  describe('parseBibPoolValidationError', () => {
    it('parses error with overlapping bibs array', () => {
      const error = {
        code: 'BIB_POOL_VALIDATION_ERROR',
        message: 'Bib range overlaps with existing pool',
        details: {
          overlapping_bibs: [50, 51, 52, 100],
          conflicting_pool_id: 'pool-123',
          conflicting_pool_name: 'Block A Pool'
        }
      }

      const result = parseBibPoolValidationError(error)

      expect(result).toEqual({
        message: 'Bib range overlaps with existing pool',
        overlappingBibs: [50, 51, 52, 100],
        conflictingPoolId: 'pool-123',
        conflictingPoolName: 'Block A Pool'
      })
    })

    it('handles missing details gracefully', () => {
      const error = {
        code: 'BIB_POOL_VALIDATION_ERROR',
        message: 'Validation error'
      }

      const result = parseBibPoolValidationError(error)

      expect(result).toEqual({
        message: 'Validation error',
        overlappingBibs: [],
        conflictingPoolId: null,
        conflictingPoolName: null
      })
    })

    it('handles missing conflicting pool name', () => {
      const error = {
        code: 'BIB_POOL_VALIDATION_ERROR',
        message: 'Overlap detected',
        details: {
          overlapping_bibs: [10, 11],
          conflicting_pool_id: 'pool-456'
        }
      }

      const result = parseBibPoolValidationError(error)

      expect(result.overlappingBibs).toEqual([10, 11])
      expect(result.conflictingPoolId).toBe('pool-456')
      expect(result.conflictingPoolName).toBeNull()
    })

    it('returns null for non-validation error', () => {
      const error = {
        code: 'OTHER_ERROR',
        message: 'Different error'
      }

      const result = parseBibPoolValidationError(error)
      expect(result).toBeNull()
    })

    it('returns null for null error', () => {
      const result = parseBibPoolValidationError(null)
      expect(result).toBeNull()
    })
  })

  describe('formatOverlappingBibs', () => {
    it('formats single bib number', () => {
      const result = formatOverlappingBibs([100])
      expect(result).toBe('100')
    })

    it('formats multiple bib numbers', () => {
      const result = formatOverlappingBibs([50, 51, 52, 100])
      expect(result).toBe('50, 51, 52, 100')
    })

    it('formats empty array', () => {
      const result = formatOverlappingBibs([])
      expect(result).toBe('')
    })

    it('sorts bib numbers numerically', () => {
      const result = formatOverlappingBibs([100, 50, 52, 51])
      expect(result).toBe('50, 51, 52, 100')
    })

    it('handles null input', () => {
      const result = formatOverlappingBibs(null)
      expect(result).toBe('')
    })

    it('handles undefined input', () => {
      const result = formatOverlappingBibs(undefined)
      expect(result).toBe('')
    })

    it('removes duplicates', () => {
      const result = formatOverlappingBibs([50, 50, 51, 51, 52])
      expect(result).toBe('50, 51, 52')
    })
  })

  describe('validation error display', () => {
    it('creates user-friendly error object with formatted bibs', () => {
      const error = {
        code: 'BIB_POOL_VALIDATION_ERROR',
        message: 'Bib range overlaps',
        details: {
          overlapping_bibs: [100, 50, 52, 51],
          conflicting_pool_id: 'pool-123',
          conflicting_pool_name: 'Block A Pool'
        }
      }

      const parsed = parseBibPoolValidationError(error)
      const formattedBibs = formatOverlappingBibs(parsed.overlappingBibs)

      expect(formattedBibs).toBe('50, 51, 52, 100')
      expect(parsed.conflictingPoolName).toBe('Block A Pool')
    })
  })
})
