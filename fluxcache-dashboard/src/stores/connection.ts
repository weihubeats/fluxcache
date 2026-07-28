import { defineStore } from 'pinia'

const STORAGE_KEY = 'fluxcache-dashboard-services'
const LEGACY_KEY = 'fluxcache-dashboard-connection'

export interface ServiceEndpoint {
  id: string
  /** Display / logical name, e.g. pay-service */
  name: string
  /** Absolute API origin, e.g. http://pay.example:8080. Empty = same-origin / Vite proxy */
  baseUrl: string
  /** Matches flux.cache.prefix */
  prefix: string
  enabled: boolean
}

export type ServiceRuntimeStatus = 'unknown' | 'online' | 'offline'

export interface ServiceRuntime {
  status: ServiceRuntimeStatus
  namespace: string
  lastError: string
  cacheCount: number
}

const DEFAULT_PREFIX = '/cache/manager/v1'

function uid(): string {
  return `svc_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
}

export function buildApiRoot(service: Pick<ServiceEndpoint, 'baseUrl' | 'prefix'>): string {
  const base = (service.baseUrl || '').replace(/\/$/, '')
  const prefix = service.prefix.startsWith('/') ? service.prefix : `/${service.prefix}`
  return `${base}${prefix.replace(/\/$/, '')}`
}

function defaultServices(): ServiceEndpoint[] {
  const envBase = (import.meta.env.VITE_API_BASE as string) || ''
  return [
    {
      id: uid(),
      name: 'local-example',
      baseUrl: envBase,
      prefix: DEFAULT_PREFIX,
      enabled: true,
    },
  ]
}

function migrateLegacy(): ServiceEndpoint[] | null {
  try {
    const raw = localStorage.getItem(LEGACY_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as { baseUrl?: string; prefix?: string }
    localStorage.removeItem(LEGACY_KEY)
    return [
      {
        id: uid(),
        name: 'default',
        baseUrl: parsed.baseUrl ?? '',
        prefix: parsed.prefix || DEFAULT_PREFIX,
        enabled: true,
      },
    ]
  } catch {
    return null
  }
}

function loadServices(): ServiceEndpoint[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as ServiceEndpoint[]
      if (Array.isArray(parsed) && parsed.length) {
        return parsed.map((s) => ({
          id: s.id || uid(),
          name: s.name || 'unnamed',
          baseUrl: s.baseUrl ?? '',
          prefix: s.prefix || DEFAULT_PREFIX,
          enabled: s.enabled !== false,
        }))
      }
    }
  } catch {
    /* fall through */
  }
  return migrateLegacy() || defaultServices()
}

function emptyRuntime(): ServiceRuntime {
  return { status: 'unknown', namespace: '', lastError: '', cacheCount: 0 }
}

export const useServiceStore = defineStore('services', {
  state: () => ({
    services: loadServices() as ServiceEndpoint[],
    runtimes: {} as Record<string, ServiceRuntime>,
  }),
  getters: {
    enabledServices(state): ServiceEndpoint[] {
      return state.services.filter((s) => s.enabled)
    },
    onlineCount(state): number {
      return Object.values(state.runtimes).filter((r) => r.status === 'online').length
    },
    serviceById(state) {
      return (id: string) => state.services.find((s) => s.id === id)
    },
  },
  actions: {
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify(
          this.services.map(({ id, name, baseUrl, prefix, enabled }) => ({
            id,
            name,
            baseUrl,
            prefix,
            enabled,
          })),
        ),
      )
    },
    getRuntime(id: string): ServiceRuntime {
      if (!this.runtimes[id]) {
        this.runtimes[id] = emptyRuntime()
      }
      return this.runtimes[id]
    },
    setRuntime(id: string, patch: Partial<ServiceRuntime>) {
      this.runtimes[id] = { ...this.getRuntime(id), ...patch }
    },
    addService(input: Omit<ServiceEndpoint, 'id'>): ServiceEndpoint {
      const svc: ServiceEndpoint = { ...input, id: uid(), prefix: input.prefix || DEFAULT_PREFIX }
      this.services.push(svc)
      this.persist()
      return svc
    },
    updateService(id: string, patch: Partial<Omit<ServiceEndpoint, 'id'>>) {
      const idx = this.services.findIndex((s) => s.id === id)
      if (idx < 0) return
      this.services[idx] = {
        ...this.services[idx],
        ...patch,
        prefix: (patch.prefix ?? this.services[idx].prefix) || DEFAULT_PREFIX,
      }
      this.persist()
    },
    removeService(id: string) {
      this.services = this.services.filter((s) => s.id !== id)
      delete this.runtimes[id]
      this.persist()
    },
  },
})

/** @deprecated use useServiceStore */
export const useConnectionStore = useServiceStore
