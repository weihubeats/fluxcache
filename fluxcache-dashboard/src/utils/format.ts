import dayjs from 'dayjs'

export function formatHitRate(rate: number | undefined | null): string {
  if (rate == null || Number.isNaN(rate)) return '-'
  return `${(rate * 100).toFixed(2)}%`
}

export function formatTime(ms: number | undefined | null): string {
  if (ms == null || ms <= 0) return '-'
  return dayjs(ms).format('MM-DD HH:mm')
}

export function formatDuration(ms: number | undefined | null): string {
  if (ms == null || ms < 0) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

export function formatCacheConfig(
  cfg?: { ttl?: number; unit?: string; cacheType?: string; maxSize?: number } | null,
): string {
  if (!cfg) return '-'
  const parts: string[] = []
  if (cfg.cacheType) parts.push(cfg.cacheType)
  if (cfg.ttl != null) parts.push(`ttl=${cfg.ttl}${cfg.unit ? ` ${cfg.unit}` : ''}`)
  if (cfg.maxSize != null && cfg.maxSize > 0) parts.push(`max=${cfg.maxSize}`)
  return parts.length ? parts.join(', ') : '-'
}

export function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
