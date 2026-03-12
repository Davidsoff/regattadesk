/**
 * Shared JSDoc type definitions for RegattaDesk API response shapes.
 *
 * These typedefs document the response objects returned by the API modules
 * (draw.js, operator.js, finance.js). They are consumed via @type and
 * @returns annotations — no runtime code is needed.
 *
 * @module api/types
 */

/**
 * A draw revision returned after generate/publish/unpublish operations.
 * @typedef {object} DrawRevision
 * @property {string}  regatta_id    - Regatta UUID
 * @property {number}  draw_revision - Monotonically increasing revision counter
 * @property {string}  status        - 'draft' | 'published'
 * @property {string}  updated_at    - ISO 8601 timestamp
 */

/**
 * A scheduling block within a regatta.
 * @typedef {object} Block
 * @property {string}  id                      - Block UUID
 * @property {string}  regatta_id              - Regatta UUID
 * @property {string}  name                    - Human-readable block name
 * @property {string}  start_time              - ISO 8601 start time
 * @property {number}  event_interval_seconds  - Gap between events
 * @property {number}  crew_interval_seconds   - Gap between crews within an event
 * @property {number}  display_order           - Sort order
 * @property {string}  created_at              - ISO 8601 creation time
 */

/**
 * A bib number allocation pool.
 * @typedef {object} BibPool
 * @property {string}   id               - Pool UUID
 * @property {string}   regatta_id       - Regatta UUID
 * @property {string|null} block_id      - Associated block UUID, or null for overflow pools
 * @property {string}   name             - Pool name
 * @property {string}   allocation_mode  - 'range' | 'explicit_list'
 * @property {number}   [start_bib]      - Range start (range mode only)
 * @property {number}   [end_bib]        - Range end (range mode only)
 * @property {number[]} [bib_numbers]    - Explicit bib list (explicit_list mode only)
 * @property {number}   priority         - Pool priority; lower value wins
 * @property {boolean}  is_overflow      - True if this is the overflow pool
 */

/**
 * A capture session for a finish-line operator.
 * @typedef {object} CaptureSession
 * @property {string}  id             - Session UUID
 * @property {string}  regatta_id     - Regatta UUID
 * @property {string}  station        - Operator station identifier
 * @property {string}  state          - 'open' | 'closed'
 * @property {string}  [block_id]     - Associated block UUID, if any
 * @property {string}  [session_type] - Session type discriminator
 * @property {string}  created_at     - ISO 8601 creation time
 * @property {string}  [closed_at]    - ISO 8601 close time
 */

/**
 * A timing marker within a capture session.
 * @typedef {object} Marker
 * @property {string}  id                  - Marker UUID
 * @property {string}  regatta_id          - Regatta UUID
 * @property {string}  capture_session_id  - Parent session UUID
 * @property {number}  frame_offset        - Video frame offset
 * @property {number}  timestamp_ms        - Wall-clock timestamp (ms since epoch)
 * @property {string}  tile_id             - Tile identifier for the image crop
 * @property {number}  tile_x              - Tile x coordinate
 * @property {number}  tile_y              - Tile y coordinate
 * @property {boolean} is_linked           - True when associated with an entry
 * @property {string|null} entry_id        - Linked entry ID, or null
 * @property {boolean} is_approved         - True when the marker is locked/approved
 */
