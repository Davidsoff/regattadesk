import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createApiClient } from '../../api/client.js'
import { createOperatorApi } from '../../api/operator.js'
import { jsonResponse, getRequest } from '../utils/testHelpers.js'

describe('Operator API - Capture Sessions', () => {
  let fetchMock
  let operatorApi

  beforeEach(() => {
    vi.restoreAllMocks()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    
    const client = createApiClient()
    operatorApi = createOperatorApi(client, {
      getOperatorToken: () => 'test-token'
    })
  })

  describe('createCaptureSession', () => {
    it('sends POST request with station and device metadata', async () => {
      const sessionData = {
        id: 'session-1',
        regatta_id: 'regatta-1',
        station: 'finish-line',
        session_type: 'finish',
        state: 'open'
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(201, sessionData))
      
      const result = await operatorApi.createCaptureSession('regatta-1', {
        station: 'finish-line',
        device_id: 'device-1',
        session_type: 'finish',
        fps: 30
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/capture_sessions')
      expect(request.method).toBe('POST')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      await expect(request.json()).resolves.toEqual({
        station: 'finish-line',
        device_id: 'device-1',
        session_type: 'finish',
        fps: 30
      })
      expect(result).toEqual(sessionData)
    })
  })

  describe('listCaptureSessions', () => {
    it('sends GET request with optional filters', async () => {
      const sessions = [
        { id: 'session-1', station: 'finish-line' },
        { id: 'session-2', station: 'start-line' }
      ]
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, sessions))
      
      const result = await operatorApi.listCaptureSessions('regatta-1', {
        station: 'finish-line',
        state: 'open'
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/capture_sessions')
      expect(request.url).toContain('station=finish-line')
      expect(request.url).toContain('state=open')
      expect(request.method).toBe('GET')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      expect(result).toEqual(sessions)
    })
  })

  describe('closeCaptureSession', () => {
    it('sends POST request to close endpoint', async () => {
      const closedSession = {
        id: 'session-1',
        state: 'closed',
        closed_at: '2026-02-27T15:00:00Z'
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, closedSession))
      
      const result = await operatorApi.closeCaptureSession('regatta-1', 'session-1', {
        close_reason: 'capture_complete'
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/capture_sessions/session-1/close')
      expect(request.method).toBe('POST')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      await expect(request.json()).resolves.toEqual({
        close_reason: 'capture_complete'
      })
      expect(result).toEqual(closedSession)
    })
  })
})

describe('Operator API - Markers', () => {
  let fetchMock
  let operatorApi

  beforeEach(() => {
    vi.restoreAllMocks()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    
    const client = createApiClient()
    operatorApi = createOperatorApi(client, {
      getOperatorToken: () => 'test-token'
    })
  })

  describe('createMarker', () => {
    it('sends POST request with frame and timestamp data', async () => {
      const markerData = {
        id: 'marker-1',
        capture_session_id: 'session-1',
        frame_offset: 12345,
        timestamp_ms: 1609459200000,
        is_linked: false,
        is_approved: false
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(201, markerData))
      
      const result = await operatorApi.createMarker('regatta-1', {
        capture_session_id: 'session-1',
        frame_offset: 12345,
        timestamp_ms: 1609459200000,
        tile_id: 'tile-1',
        tile_x: 5,
        tile_y: 3
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers')
      expect(request.method).toBe('POST')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      await expect(request.json()).resolves.toEqual({
        capture_session_id: 'session-1',
        frame_offset: 12345,
        timestamp_ms: 1609459200000,
        tile_id: 'tile-1',
        tile_x: 5,
        tile_y: 3
      })
      expect(result).toEqual(markerData)
    })
  })

  describe('updateMarker', () => {
    it('sends PATCH request with updated fields', async () => {
      const updatedMarker = {
        id: 'marker-1',
        frame_offset: 12346,
        timestamp_ms: 1609459201000,
        is_approved: false
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, updatedMarker))
      
      const result = await operatorApi.updateMarker('regatta-1', 'marker-1', {
        frame_offset: 12346,
        timestamp_ms: 1609459201000
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers/marker-1')
      expect(request.method).toBe('PATCH')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      await expect(request.json()).resolves.toEqual({
        frame_offset: 12346,
        timestamp_ms: 1609459201000
      })
      expect(result).toEqual(updatedMarker)
    })
  })

  describe('deleteMarker', () => {
    it('sends DELETE request', async () => {
      fetchMock.mockResolvedValueOnce(jsonResponse(204, null))
      
      await operatorApi.deleteMarker('regatta-1', 'marker-1')
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers/marker-1')
      expect(request.method).toBe('DELETE')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
    })
  })

  describe('linkMarker', () => {
    it('sends POST request to link marker to entry', async () => {
      const linkedMarker = {
        id: 'marker-1',
        entry_id: 'entry-1',
        is_linked: true,
        is_approved: false
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, linkedMarker))
      
      const result = await operatorApi.linkMarker('regatta-1', 'marker-1', {
        entry_id: 'entry-1'
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers/marker-1/link')
      expect(request.method).toBe('POST')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      await expect(request.json()).resolves.toEqual({
        entry_id: 'entry-1'
      })
      expect(result).toEqual(linkedMarker)
    })
  })

  describe('unlinkMarker', () => {
    it('sends POST request to unlink marker from entry', async () => {
      const unlinkedMarker = {
        id: 'marker-1',
        entry_id: null,
        is_linked: false,
        is_approved: false
      }
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, unlinkedMarker))
      
      const result = await operatorApi.unlinkMarker('regatta-1', 'marker-1')
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers/marker-1/unlink')
      expect(request.method).toBe('POST')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      expect(result).toEqual(unlinkedMarker)
    })
  })

  describe('listMarkers', () => {
    it('sends GET request with capture session filter', async () => {
      const markers = [
        { id: 'marker-1', is_linked: false },
        { id: 'marker-2', is_linked: true }
      ]
      
      fetchMock.mockResolvedValueOnce(jsonResponse(200, markers))
      
      const result = await operatorApi.listMarkers('regatta-1', {
        capture_session_id: 'session-1'
      })
      
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const request = getRequest(fetchMock)
      expect(request.url).toContain('/api/v1/regattas/regatta-1/operator/markers')
      expect(request.url).toContain('capture_session_id=session-1')
      expect(request.method).toBe('GET')
      expect(request.headers.get('x_operator_token')).toBe('test-token')
      expect(result).toEqual(markers)
    })
  })
})
