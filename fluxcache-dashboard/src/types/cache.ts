export type FluxCacheLevel = 'NULL' | 'FirstCacheable' | 'SecondaryCacheable' | string

export type FluxCacheType = 'CAFFEINE' | 'REDIS_R_MAP' | 'REDIS_BUCKET' | string

export interface FluxCacheConfig {
  ttl?: number
  initSize?: number
  unit?: string
  maxSize?: number
  cacheType?: FluxCacheType
}

export interface FluxCacheOperation {
  methodName?: string
  cacheName: string
  key?: string | null
  fluxCacheLevel?: FluxCacheLevel
  firstCacheConfig?: FluxCacheConfig | null
  secondaryCacheConfig?: FluxCacheConfig | null
  allowCacheNull?: boolean
}

export interface FluxCacheOperationVO {
  namespace: string
  cacheOperations: FluxCacheOperation[]
}

export interface FluxCacheStaticsVO {
  startTime: number
  endTime: number
  hit: number
  miss: number
  putCount: number
  evictCount: number
  requestCount: number
  maxLoadTime: number
  hitRate: number
}

export interface FluxCacheAllStaticsVO {
  startTime: number
  cacheName: string
  windows: FluxCacheStaticsVO[]
  totalHit: number
  totalMiss: number
  totalPut: number
  totalEvict: number
  totalRequest: number
  maxLoadTimeOverall: number
  overallHitRate: number
}

export interface FluxCacheValueVO {
  flag: boolean
  value: unknown
}

export interface FluxCacheStaticsSummaryItem {
  cacheName: string
  overallHitRate: number
  totalRequest: number
  totalHit: number
  totalMiss: number
  maxLoadTimeOverall: number
}

export interface FluxCacheStaticsSummaryVO {
  namespace: string
  items: FluxCacheStaticsSummaryItem[]
}

/** Row in multi-service overview table */
export interface CacheOverviewRow extends FluxCacheOperation {
  rowKey: string
  serviceId: string
  serviceName: string
  namespace: string
  hitRate?: number
  totalRequest?: number
  online: boolean
  error?: string
}
