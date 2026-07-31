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
  max-width: 150px;
  padding: 1px 7px;
  border-radius: 5px;
  background: rgba(157, 140, 255, 0.08);
  border: 1px solid rgba(157, 140, 255, 0.18);
  color: #c3b6ff;
  font-size: 11.5px;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.none-text {
  color: rgba(234, 240, 251, 0.25);
}
</style>
