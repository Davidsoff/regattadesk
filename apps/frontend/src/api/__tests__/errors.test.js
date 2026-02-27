import { describe, it, expect } from 'vitest'
import { normalizeApiError, isApiError } from '../errors'

describe('errors', () => {
  describe('normalizeApiError', () => {
    it('extracts error from OpenAPI error_response schema', () => {
      const response = {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input provided'
        }
      }

      const normalized = normalizeApiError(response)

      expect(normalized).toEqual({
        code: 'VALIDATION_ERROR',
        message: 'Invalid input provided',
        details: undefined,
        requestId: undefined
      })
    })

    it('includes optional details field when present', () => {
      const response = {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input',
          details: {
            field: 'entry_id',
            constraint: 'must be valid UUID'
          }
        }
      }

      const normalized = normalizeApiError(response)

      expect(normalized.details).toEqual({
        field: 'entry_id',
        constraint: 'must be valid UUID'
      })
    })

    it('extracts request_id from top-level response when present', () => {
      const response = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Server error'
        },
        request_id: 'req-abc-123'
      }

      const normalized = normalizeApiError(response)

      expect(normalized.requestId).toBe('req-abc-123')
    })

    it('returns generic error when response has no error field', () => {
      const response = { success: false }

      const normalized = normalizeApiError(response)

      expect(normalized.code).toBe('UNKNOWN_ERROR')
      expect(normalized.message).toBe('')
    })

    it('returns generic error for null response', () => {
      const normalized = normalizeApiError(null)

      expect(normalized.code).toBe('UNKNOWN_ERROR')
      expect(normalized.message).toBe('')
    })

    it('returns generic error for non-object response', () => {
      const normalized = normalizeApiError('error string')

      expect(normalized.code).toBe('UNKNOWN_ERROR')
      expect(normalized.message).toBe('')
    })

    it('handles missing message field gracefully', () => {
      const response = {
        error: {
          code: 'VALIDATION_ERROR'
        }
      }

      const normalized = normalizeApiError(response)

      expect(normalized.code).toBe('VALIDATION_ERROR')
      expect(normalized.message).toBe('')
    })

    it('handles missing code field gracefully', () => {
      const response = {
        error: {
          message: 'Something went wrong'
        }
      }

      const normalized = normalizeApiError(response)

      expect(normalized.code).toBe('UNKNOWN_ERROR')
      expect(normalized.message).toBe('Something went wrong')
    })
  })

  describe('isApiError', () => {
    it('returns true for valid OpenAPI error_response', () => {
      const response = {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid input'
        }
      }

      expect(isApiError(response)).toBe(true)
    })

    it('returns false when error field is missing', () => {
      const response = { success: false }

      expect(isApiError(response)).toBe(false)
    })

    it('returns false for null', () => {
      expect(isApiError(null)).toBe(false)
    })

    it('returns false for non-object', () => {
      expect(isApiError('error')).toBe(false)
    })

    it('returns false when error is not an object', () => {
      const response = { error: 'error string' }

      expect(isApiError(response)).toBe(false)
    })
  })
})
