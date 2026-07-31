<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  elKey?: string | null
}>()

const text = computed(() => {
  const v = props.elKey
  if (v == null || v === '') return null
  return v
})
</script>

<template>
  <span v-if="!text" class="none-text">—</span>
  <a-tooltip v-else :title="text">
    <code class="el-chip mono">{{ text }}</code>
  </a-tooltip>
</template>

<style scoped>
.el-chip {
  display: inline-block;
  max-width: 160px;
  padding: 1px 7px;
  border-radius: 4px;
  background: #f5f5f5;
  color: #595959;
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.none-text {
  color: rgba(0, 0, 0, 0.25);
}
</style>
