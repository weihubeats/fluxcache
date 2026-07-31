<script setup lang="ts">
import { computed, h } from 'vue'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudServerOutlined,
  ApiOutlined,
} from '@ant-design/icons-vue'

const props = defineProps<{
  configured: number
  enabled: number
  online: number
  offline: number
}>()

const cards = computed(() => [
  {
    key: 'configured',
    label: '已配置服务',
    value: props.configured,
    tone: 'slate' as const,
    icon: () => h(CloudServerOutlined),
  },
  {
    key: 'enabled',
    label: '启用中',
    value: props.enabled,
    tone: 'blue' as const,
    icon: () => h(ApiOutlined),
  },
  {
    key: 'online',
    label: '在线',
    value: props.online,
    tone: 'green' as const,
    icon: () => h(CheckCircleOutlined),
  },
  {
    key: 'offline',
    label: '离线',
    value: props.offline,
    tone: props.offline > 0 ? ('red' as const) : ('muted' as const),
    icon: () => h(CloseCircleOutlined),
    warn: props.offline > 0,
  },
])
</script>

<template>
  <a-row :gutter="[16, 16]" class="kpi-row">
    <a-col v-for="card in cards" :key="card.key" :xs="12" :md="6">
      <div class="kpi-card" :class="[`tone-${card.tone}`, { warn: card.warn }]">
        <div class="kpi-icon" :class="`tone-${card.tone}`">
          <component :is="card.icon" />
        </div>
        <div class="kpi-body">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value }}</div>
        </div>
      </div>
    </a-col>
  </a-row>
</template>

<style scoped>
.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  min-height: 84px;
}

.kpi-card.warn {
  background: #fff1f0;
  border-color: #ffccc7;
}

.kpi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  font-size: 18px;
  flex-shrink: 0;
}

.kpi-icon.tone-slate {
  color: #595959;
  background: #f5f5f5;
}
.kpi-icon.tone-blue {
  color: #1677ff;
  background: #e6f4ff;
}
.kpi-icon.tone-green {
  color: #389e0d;
  background: #f6ffed;
}
.kpi-icon.tone-red {
  color: #cf1322;
  background: #fff1f0;
}
.kpi-icon.tone-muted {
  color: #8c8c8c;
  background: #fafafa;
}

.kpi-label {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  line-height: 1.2;
}

.kpi-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: #1f1f1f;
}

.kpi-card.tone-green .kpi-value {
  color: #389e0d;
}
.kpi-card.tone-red .kpi-value,
.kpi-card.warn .kpi-value {
  color: #cf1322;
}
.kpi-card.tone-blue .kpi-value {
  color: #1677ff;
}
</style>
