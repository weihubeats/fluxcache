import dayjs from 'dayjs'
import type { FluxCacheConfig, FluxCacheLevel, FluxCacheType } from '@/types/cache'

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

export interface LevelDisplay {
  label: string
  color: string
  raw: string
}

export function formatCacheLevel(level?: FluxCacheLevel | null): LevelDisplay {
  const raw = level || ''
  if (raw === 'FirstCacheable') {
    return { label: 'L1 单级', color: 'blue', raw }
  }
  if (raw === 'SecondaryCacheable') {
    return { label: 'L1+L2 双级', color: 'purple', raw }
  }
  if (!raw || raw === 'NULL') {
    return { label: '未配置', color: 'default', raw: raw || '-' }
  }
  return { label: raw, color: 'default', raw }
}

/**
 * Shorten Java method signatures / FQCNs for table display.
 * Examples:
 * - "com.foo.UserService.getUser" -> "UserService.getUser"
 * - "public java.util.List com.foo.UserService.getUser(java.lang.String)" -> "UserService.getUser(...)"
 * - "no_method" -> "no_method"
 */
export function shortMethodName(methodName?: string | null): { short: string; full: string } {
  const full = (methodName || '').trim()
  if (!full || full === 'no_method') {
    return { short: full || '-', full: full || '-' }
  }

  // Strip leading modifiers / return type: "... Type com.pkg.Class.method(args)"
  const withArgs = full.match(
    /(?:^|\s)([\w.$]+)\.([\w$]+)\s*\((.*)\)\s*$/,
  )
  if (withArgs) {
    const fqcn = withArgs[1]
    const method = withArgs[2]
    const args = withArgs[3].trim()
    const simpleClass = fqcn.split('.').pop() || fqcn
    const shortArgs = args ? '(...)' : '()'
    return { short: `${simpleClass}.${method}${shortArgs}`, full }
  }

  // FQCN.method without args
  const dotted = full.match(/^([\w.$]+)\.([\w$]+)$/)
  if (dotted) {
    const simpleClass = dotted[1].split('.').pop() || dotted[1]
    return { short: `${simpleClass}.${dotted[2]}`, full }
  }

  // Already short or unknown shape — truncate visually long strings
  if (full.length > 48) {
    return { short: `${full.slice(0, 45)}…`, full }
  }
  return { short: full, full }
}

export type CacheTypeKind = 'caffeine' | 'redis' | 'other' | 'none'

export interface CacheLayerDisplay {
  kind: CacheTypeKind
  typeLabel: string
  ttlLabel: string | null
  maxSizeLabel: string | null
}

const UNIT_SHORT: Record<string, string> = {
  NANOSECONDS: 'ns',
  MICROSECONDS: 'μs',
  MILLISECONDS: 'ms',
  SECONDS: 's',
  MINUTES: 'm',
  HOURS: 'h',
  DAYS: 'd',
}

export function formatTtlShort(ttl?: number | null, unit?: string | null): string | null {
  if (ttl == null) return null
  const u = (unit || '').toUpperCase()
  const suffix = UNIT_SHORT[u] || (unit ? String(unit).charAt(0).toLowerCase() : '')
  return `${ttl}${suffix}`
}

export function parseCacheLayer(cfg?: FluxCacheConfig | null): CacheLayerDisplay {
  if (!cfg || (!cfg.cacheType && cfg.ttl == null)) {
    return { kind: 'none', typeLabel: '未配置', ttlLabel: null, maxSizeLabel: null }
  }
  const type = (cfg.cacheType || '') as FluxCacheType
  let kind: CacheTypeKind = 'other'
  let typeLabel = type || 'Unknown'
  if (type === 'CAFFEINE') {
    kind = 'caffeine'
    typeLabel = 'Caffeine'
  } else if (type.startsWith('REDIS')) {
    kind = 'redis'
    typeLabel = type === 'REDIS' ? 'Redis' : type === 'REDIS_MAP' ? 'Redis Map' : 'Redis'
  }
  const ttlLabel = formatTtlShort(cfg.ttl, cfg.unit)
  const maxSizeLabel = cfg.maxSize != null && cfg.maxSize > 0 ? `max ${cfg.maxSize}` : null
  return { kind, typeLabel, ttlLabel, maxSizeLabel }
}
