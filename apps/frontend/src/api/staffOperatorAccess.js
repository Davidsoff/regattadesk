import { ApiError } from './client.js'
import { normalizeApiError } from './errors.js'

function getHeader(headers, name) {
  if (!headers) {
    return undefined
  }

  if (typeof headers.get === 'function') {
    return headers.get(name) ?? headers.get(name.toLowerCase()) ?? undefined
  }

  if (typeof headers === 'object') {
    const headerKey = Object.keys(headers).find((key) => key.toLowerCase() === name.toLowerCase())
    return headerKey ? headers[headerKey] : undefined
  }

  return undefined
}

function parseFilename(contentDisposition, fallbackFilename) {
  if (typeof contentDisposition !== 'string') {
    return fallbackFilename
  }

  const filenameStarRegex = /filename\*\s*=\s*([^;]+)/i
  const filenameRegex = /filename\s*=\s*([^;]+)/i
  const filenameStarMatch = filenameStarRegex.exec(contentDisposition)
  const filenameMatch = filenameRegex.exec(contentDisposition)
  let rawFilename = filenameStarMatch?.[1] ?? filenameMatch?.[1]

  if (!rawFilename) {
    return fallbackFilename
  }

  rawFilename = rawFilename.trim().replaceAll(/^["']|["']$/g, '')

  if (/^utf-8''/i.test(rawFilename)) {
    const normalizedFilename = rawFilename.slice(7)
    try {
      return decodeURIComponent(normalizedFilename)
    } catch {
      return normalizedFilename
    }
  }

  return rawFilename
}

function normalizePdfResponse(result, tokenId, headers) {
  const fallbackFilename = `operator-token-${tokenId}.pdf`
  const contentDisposition = getHeader(headers, 'Content-Disposition')
  const contentType = getHeader(headers, 'Content-Type') ?? 'application/pdf'

  if (result instanceof Blob) {
    return {
      blob: result,
      filename: parseFilename(contentDisposition, fallbackFilename),
      contentType: result.type || contentType,
      size: result.size ?? 0,
    }
  }

  if (result && typeof result === 'object' && !Array.isArray(result)) {
    let blob = null
    if (result.blob instanceof Blob) {
      blob = result.blob
    } else if (result.data instanceof Blob) {
      blob = result.data
    }

    return {
      ...result,
      blob,
      filename: result.filename ?? parseFilename(contentDisposition, fallbackFilename),
      contentType: result.contentType ?? blob?.type ?? contentType,
      size: result.size ?? blob?.size ?? 0,
    }
  }

  return {
    blob: null,
    filename: fallbackFilename,
    contentType,
    size: 0,
  }
}

function normalizeStationHandoffResponse(result) {
  if (!result || typeof result !== 'object' || Array.isArray(result)) {
    return result
  }

  return {
    ...result,
    requestingDeviceId: result.requestingDeviceId ?? result.requesting_device_id ?? null,
    expiresAt: result.expiresAt ?? result.expires_at ?? null,
    createdAt: result.createdAt ?? result.created_at ?? null,
    completedAt: result.completedAt ?? result.completed_at ?? null,
  }
}

function normalizeStationHandoffListResponse(result) {
  let handoffs = []

  if (Array.isArray(result?.data)) {
    handoffs = result.data
  } else if (Array.isArray(result)) {
    handoffs = result
  }

  return handoffs.map(normalizeStationHandoffResponse)
}

export function createStaffOperatorAccessApi(client) {
  return {
    async listTokens(regattaId) {
      return client.get(`/regattas/${regattaId}/operator/tokens`)
    },

    async createToken(regattaId, payload) {
      return client.post(`/regattas/${regattaId}/operator/tokens`, payload)
    },

    async revokeToken(regattaId, tokenId) {
      return client.post(`/regattas/${regattaId}/operator/tokens/${tokenId}/revoke`, {})
    },

    async exportTokenPdf(regattaId, tokenId) {
      if (client.generatedClient && typeof client.generatedClient.request === 'function') {
        const result = await client.generatedClient.request({
          method: 'GET',
          url: `/regattas/${regattaId}/operator/tokens/${tokenId}/export_pdf`,
          parseAs: 'blob',
          responseStyle: 'fields',
        })

        if (result?.error) {
          throw new ApiError(normalizeApiError(result.error), result.response?.status ?? 0)
        }

        return normalizePdfResponse(result?.data, tokenId, result?.response?.headers)
      }

      const result = await client.get(`/regattas/${regattaId}/operator/tokens/${tokenId}/export_pdf`)
      return normalizePdfResponse(result, tokenId)
    },

    async listPendingHandoffs(regattaId, filters = {}) {
      const query = {}

      if (typeof filters.station === 'string' && filters.station.trim()) {
        query.station = filters.station.trim()
      }

      if (typeof filters.token_id === 'string' && filters.token_id.trim()) {
        query.token_id = filters.token_id.trim()
      }

      const result = await client.get(`/regattas/${regattaId}/operator/station_handoffs`, { query })
      return normalizeStationHandoffListResponse(result)
    },

    async getStationHandoff(regattaId, handoffId) {
      const result = await client.get(`/regattas/${regattaId}/operator/station_handoffs/${handoffId}`)
      return normalizeStationHandoffResponse(result)
    },

    async adminRevealPin(regattaId, handoffId) {
      const result = await client.post(
        `/regattas/${regattaId}/operator/station_handoffs/${handoffId}/admin_reveal_pin`,
        {}
      )
      return normalizeStationHandoffResponse(result)
    },
  }
}
