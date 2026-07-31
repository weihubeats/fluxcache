<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{
  configured: number
  enabled: number
  online: number
  offline: number
}>()

function useCountUp(target: () => number, ms = 1100) {
  const display = ref(0)
  let raf = 0
  function run() {
    cancelAnimationFrame(raf)
    const to = target()
    const t0 = performance.now()
    const step = (t: number) => {
      const p = Math.min((t - t0) / ms, 1)
      const e = 1 - Math.pow(1 - p, 3)
      display.value = Math.round(to * e)
      if (p < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }
  onMounted(run)
  watch(target, run)
  return display
}

const configured = useCountUp(() => props.configured)
const enabled = useCountUp(() => props.enabled)
const online = useCountUp(() => props.online)
const offline = useCountUp(() => props.offline)
const onlinePct = () => (props.enabled ? Math.round((props.online / props.enabled) * 100) : 0)
</script>

<template>
  <div class="bento">
    <div class="card c-total">
      <div class="corner"></div>
      <div class="top">
        <span class="lab">已配置服务</span>
        <span class="ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14a9 3 0 0 0 18 0V5"/><path d="M3 12a9 3 0 0 0 18 0"/></svg>
        </span>
      </div>
      <div class="num">{{ configured }}</div>
      <div class="meta"><span class="flat">{{ enabled }} 个启用</span></div>
      <svg class="spark" viewBox="0 0 108 38" preserveAspectRatio="none">
        <path d="M0 30 L18 26 L36 28 L54 18 L72 20 L90 9 L108 6" stroke="var(--teal)"/>
      </svg>
    </div>

    <div class="card c-on">
      <div class="corner"></div>
      <div class="top">
        <span class="lab">在线</span>
        <span class="ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><path d="M22 4 12 14.01l-3-3"/></svg>
        </span>
      </div>
      <div class="num">{{ online }}<small>/ {{ enabled }}</small></div>
      <div class="meta"><span class="up">{{ onlinePct() }}%</span> 节点全部可达</div>
      <svg class="ring" viewBox="0 0 42 42">
        <circle class="bg" cx="21" cy="21" r="18"/>
        <circle class="fg" cx="21" cy="21" r="18" :style="{ strokeDashoffset: `${113 - (113 * onlinePct()) / 100}` }"/>
      </svg>
    </div>

    <div class="card c-up">
      <div class="corner"></div>
      <div class="top">
        <span class="lab">启用中</span>
        <span class="ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2 3 14h7l-1 8 10-12h-7z"/></svg>
        </span>
      </div>
      <div class="num">{{ enabled }}</div>
      <div class="meta"><span class="flat">已加载配置</span></div>
      <svg class="spark" viewBox="0 0 108 38" preserveAspectRatio="none">
        <path d="M0 22 L18 24 L36 16 L54 18 L72 12 L90 14 L108 8" stroke="var(--sky)"/>
      </svg>
    </div>

    <div class="card c-off" :class="{ warn: offline > 0 }">
      <div class="top">
        <span class="lab">离线</span>
        <span class="ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M4.9 4.9l14.2 14.2"/></svg>
        </span>
      </div>
      <div class="num">{{ offline }}</div>
      <div class="meta"><span class="flat">{{ offline > 0 ? '存在异常' : '无异常' }}</span></div>
    </div>
  </div>
</template>

<style scoped>
.bento {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.card {
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 18px 18px 16px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(160deg, var(--surface), var(--bg-2));
  transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1), border-color 0.3s, box-shadow 0.3s;
}

.card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: var(--radius);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
  pointer-events: none;
}

.card:hover {
  transform: translateY(-3px);
  border-color: var(--line-3);
  box-shadow: var(--shadow);
}

.corner {
  position: absolute;
  top: 0;
  right: 0;
  width: 90px;
  height: 90px;
  border-radius: 0 0 0 90px;
  opacity: 0.5;
  filter: blur(2px);
}

.c-total {
  grid-column: span 4;
}
.c-on {
  grid-column: span 3;
}
.c-up {
  grid-column: span 3;
}
.c-off {
  grid-column: span 2;
}

.c-total .ico {
  color: var(--teal);
  background: var(--teal-d);
}
.c-total .corner {
  background: radial-gradient(circle at top right, rgba(51, 216, 194, 0.18), transparent 70%);
}
.c-on .ico {
  color: var(--teal);
  background: var(--teal-d);
}
.c-on .corner {
  background: radial-gradient(circle at top right, rgba(51, 216, 194, 0.16), transparent 70%);
}
.c-up .ico {
  color: var(--sky);
  background: rgba(90, 169, 240, 0.12);
}
.c-up .corner {
  background: radial-gradient(circle at top right, rgba(90, 169, 240, 0.16), transparent 70%);
}
.c-off .ico {
  color: var(--text-faint);
  background: rgba(255, 255, 255, 0.04);
}
.c-off .num {
  color: var(--text-faint);
}
.c-off.warn .num,
.c-off.warn .meta .flat {
  color: var(--rose);
}

.top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.lab {
  font-family: var(--mono);
  font-size: 10.5px;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: var(--text-faint);
}

.ico {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-2);
}

.ico svg {
  width: 17px;
  height: 17px;
}

.num {
  font-family: var(--display);
  font-weight: 800;
  font-size: 46px;
  line-height: 1;
  margin: 14px 0 4px;
  letter-spacing: -1px;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}

.num small {
  font-size: 18px;
  color: var(--text-faint);
  font-weight: 600;
  margin-left: 3px;
}

.meta {
  font-size: 12px;
  color: var(--text-dim);
  display: flex;
  align-items: center;
  gap: 7px;
}

.meta .up {
  color: var(--teal);
  font-family: var(--mono);
  font-weight: 600;
}

.meta .flat {
  color: var(--text-faint);
  font-family: var(--mono);
}

.spark {
  position: absolute;
  right: 14px;
  bottom: 14px;
  width: 108px;
  height: 38px;
  opacity: 0.9;
}

.spark path {
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 300;
  stroke-dashoffset: 300;
  animation: draw 1.8s 0.3s ease forwards;
}

@keyframes draw {
  to {
    stroke-dashoffset: 0;
  }
}

.ring {
  width: 42px;
  height: 42px;
  position: absolute;
  right: 16px;
  bottom: 16px;
}

.ring circle {
  fill: none;
  stroke-width: 4;
  stroke-linecap: round;
  transform: rotate(-90deg);
  transform-origin: center;
}

.ring .bg {
  stroke: rgba(255, 255, 255, 0.07);
}

.ring .fg {
  stroke: var(--teal);
  stroke-dasharray: 113;
  transition: stroke-dashoffset 1.4s cubic-bezier(0.2, 0.8, 0.2, 1);
}

@media (max-width: 1080px) {
  .c-total,
  .c-on,
  .c-up,
  .c-off {
    grid-column: span 6;
  }
}

@media (max-width: 620px) {
  .c-total,
  .c-on,
  .c-up,
  .c-off {
    grid-column: span 12;
  }
}
</style>
