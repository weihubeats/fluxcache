import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'FluxCache',
  description: '轻量级多级缓存框架 · 基于 Spring Boot 的 Caffeine + Redis 多级缓存解决方案',
  lang: 'zh-CN',
  base: '/fluxcache/',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['meta', { name: 'theme-color', content: '#e95b3c' }],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/flux-col-logo.svg' }]
  ],

  themeConfig: {
    logo: '/flux-col-logo.svg',
    nav: [
      { text: '首页', link: '/' },
      { text: '指南', link: '/guide/getting-started' },
      { text: '性能', link: '/guide/benchmark' },
      {
        text: 'GitHub',
        link: 'https://github.com/weihubeats/fluxcache'
      }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/weihubeats/fluxcache' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: '指南',
          items: [
            { text: '快速开始', link: '/guide/getting-started' },
            { text: '全局配置', link: '/guide/configuration' },
            { text: '性能基准', link: '/guide/benchmark' },
            { text: '可观测性', link: '/guide/observability' }
          ]
        }
      ]
    },
    footer: {
      message: 'Apache License 2.0',
      copyright: 'Copyright © 2024-2026 weihubeats'
    },

    search: {
      provider: 'local'
    }
  }
})