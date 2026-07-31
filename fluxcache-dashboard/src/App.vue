<script setup lang="ts">
import { RouterView } from 'vue-router'
import { theme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const themeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#33d8c2',
    colorInfo: '#33d8c2',
    colorSuccess: '#52d9a8',
    colorWarning: '#f4b13e',
    colorError: '#ff6b6b',
    colorBgBase: '#0a0e16',
    colorBgContainer: '#111827',
    colorBgElevated: '#151d2e',
    colorBgLayout: '#0a0e16',
    colorBorder: 'rgba(255,255,255,0.10)',
    colorBorderSecondary: 'rgba(255,255,255,0.06)',
    colorText: '#eaf0fb',
    colorTextSecondary: '#9aa7c0',
    colorTextTertiary: '#5e6c87',
    colorTextQuaternary: 'rgba(234,240,251,0.28)',
    borderRadius: 10,
    borderRadiusLG: 16,
    fontSize: 13,
    fontFamily:
      "'Manrope', system-ui, -apple-system, sans-serif",
    controlOutlineWidth: 0,
  },
  components: {
    Table: {
      headerBg: 'rgba(255,255,255,0.02)',
      headerColor: '#5e6c87',
      headerSplitColor: 'transparent',
      rowHoverBg: 'rgba(255,255,255,0.03)',
      borderColor: 'rgba(255,255,255,0.06)',
      cellPaddingBlock: 13,
    },
    Drawer: { colorBgElevated: '#0e1420' },
    Modal: { contentBg: '#0e1420', headerBg: 'transparent' },
  },
}
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <RouterView />
  </a-config-provider>
</template>
