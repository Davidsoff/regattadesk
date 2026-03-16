/**
 * JSON utility helpers.
 *
 * Stateless pure utilities for JSON parsing with safe fallback semantics.
 * Lives in utils/ alongside other stateless helpers (storage.js, locale.js).
 */

/**
 * Parse a JSON string, returning fallback on any parse error.
 * @param {string} text - The string to parse
 * @param {*} fallback - Value to return on parse error (default null)
 * @returns {*} Parsed value or fallback
 */
export function safeJsonParse(text, fallback = null) {
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}
