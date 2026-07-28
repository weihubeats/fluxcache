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
        const msg = err.response?.data?.message || err.message || 'Request failed'
        message.error(typeof msg === 'string' ? msg : 'Request failed')
      }
      return Promise.reject(err)
    },
  )
  return client
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
