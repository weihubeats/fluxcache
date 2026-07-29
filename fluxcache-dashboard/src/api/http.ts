import axios, { type AxiosInstance } from 'axios'
import { message } from 'ant-design-vue'
import { buildApiRoot, type ServiceEndpoint } from '@/stores/connection'

declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const clients = new Map<string, AxiosInstance>()

function createClient(): AxiosInstance {
  const client = axios.create({ timeout: 15000 })
  client.interceptors.response.use(
    (res) => res,
    (err) => {
      if (!err.config?.silent) {
        message.error(formatRequestError(err))
      }
      return Promise.reject(err)
    },
  )
  return client
}

function formatRequestError(err: {
  message?: string
  code?: string
  response?: { status?: number; data?: { message?: string } }
  config?: { baseURL?: string; url?: string }
}): string {
  const backendMsg = err.response?.data?.message
  if (typeof backendMsg === 'string' && backendMsg) return backendMsg

  // Browser CORS / refused connection: no HTTP response → axios "Network Error"
  if (!err.response && (err.message === 'Network Error' || err.code === 'ERR_NETWORK')) {
    const target = `${err.config?.baseURL || ''}${err.config?.url || ''}`
    return (
      `网络错误（多为跨域 CORS 或服务不可达）` +
      (target ? `：${target}` : '') +
      `。本地可将服务 Base URL 留空走 Vite 代理，或在业务侧 / 网关放行 Dashboard 源。`
    )
  }

  return err.message || 'Request failed'
}

export function getHttpForService(service: ServiceEndpoint): AxiosInstance {
  let client = clients.get(service.id)
  if (!client) {
    client = createClient()
    clients.set(service.id, client)
  }
  client.defaults.baseURL = buildApiRoot(service)
  return client
}

export function resetHttpClient(serviceId?: string) {
  if (serviceId) clients.delete(serviceId)
  else clients.clear()
}
