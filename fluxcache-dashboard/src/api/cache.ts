import { getHttpForService } from './http'
import type { ServiceEndpoint } from '@/stores/connection'
import type {
  FluxCacheAllStaticsVO,
  FluxCacheOperationVO,
  FluxCacheStaticsSummaryVO,
  FluxCacheValueVO,
} from '@/types/cache'

export async function fetchAllCaches(service: ServiceEndpoint): Promise<FluxCacheOperationVO> {
  const { data } = await getHttpForService(service).get<FluxCacheOperationVO>('/all/caches')
  return data
}

export async function fetchAllStatics(
  service: ServiceEndpoint,
  cacheName: string,
): Promise<FluxCacheAllStaticsVO> {
  const { data } = await getHttpForService(service).get<FluxCacheAllStaticsVO>('/getAllStatics', {
    params: { cacheName },
  })
  return data
}

export async function fetchStaticsSummary(
  service: ServiceEndpoint,
): Promise<FluxCacheStaticsSummaryVO | null> {
  try {
    const { data } = await getHttpForService(service).get<FluxCacheStaticsSummaryVO>(
      '/statics/summary',
      { silent: true },
    )
    return data
  } catch {
    return null
  }
}

export async function fetchCacheValue(
  service: ServiceEndpoint,
  cacheName: string,
  key: string,
): Promise<FluxCacheValueVO> {
  const { data } = await getHttpForService(service).get<FluxCacheValueVO>('/getValue', {
    params: { cacheName, key },
  })
  return data
}

export async function evictCache(
  service: ServiceEndpoint,
  cacheName: string,
  keys: string[],
): Promise<boolean> {
  const { data } = await getHttpForService(service).post<boolean>('/evict', null, {
    params: { cacheName, keys },
  })
  return data
}

export async function clearCache(service: ServiceEndpoint, cacheName: string): Promise<boolean> {
  const { data } = await getHttpForService(service).post<boolean>('/clear', null, {
    params: { cacheName },
  })
  return data
}
