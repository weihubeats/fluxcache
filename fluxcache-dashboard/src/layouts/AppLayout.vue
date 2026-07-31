<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useServiceStore } from '@/stores/connection'

const route = useRoute()
const router = useRouter()
const store = useServiceStore()
const onlineCount = computed(
  () => store.enabledServices.filter((s) => store.getRuntime(s.id).status === 'online').length,
)
const cacheTotal = computed(() =>
  store.enabledServices.reduce((sum, s) => sum + (store.getRuntime(s.id).cacheCount || 0), 0),
)

const now = ref('--:--:--')
let timer: ReturnType<typeof setInterval> | null = null
function tick() {
  now.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}
onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
})
onUnmounted(() => timer && clearInterval(timer))

const selectedKey = computed(() => (route.name === 'settings' ? 'settings' : 'caches'))

const navItems = [
  {
    key: 'caches',
    label: '缓存总览',
    icon: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
  },
  {
    key: 'settings',
    label: '服务管理',
    icon: '<path d="M22 3H2l8 9.46V19l4 2v-8.54z"/>',
  },
]

function go(key: string) {
  if (key === 'settings') router.push({ name: 'settings' })
  else router.push({ name: 'caches' })
}

const onlinePct = computed(() => {
  const n = store.enabledServices.length
  return n ? Math.round((onlineCount.value / n) * 100) : 0
})
const enabledPct = computed(() => {
  const n = store.services.length
  return n ? Math.round((store.enabledServices.length / n) * 100) : 0
})
</script>

<template>
  <div class="shell">
    <!-- ===== SIDEBAR ===== -->
    <aside class="side">
      <div class="brand">
        <div class="logo">FC</div>
        <div>
          <div class="name">Flux<b>Cache</b></div>
          <div class="tag">CONSOLE v2.4</div>
        </div>
      </div>

      <div class="nav-label">监控</div>
      <nav class="nav">
        <a
          v-for="item in navItems"
          :key="item.key"
          :class="{ active: selectedKey === item.key }"
          @click.prevent="go(item.key)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" v-html="item.icon" />
          {{ item.label }}
        </a>
      </nav>

      <div class="side-foot">
        <div class="syscard">
          <h4><span class="dot"></span> 集群健康</h4>
          <div class="meter">
            <div class="row"><span>服务在线</span><b>{{ onlinePct }}%</b></div>
            <div class="bar"><i :style="{ width: `${onlinePct}%` }"></i></div>
          </div>
          <div class="meter">
            <div class="row"><span>服务启用</span><b>{{ enabledPct }}%</b></div>
            <div class="bar amber"><i :style="{ width: `${enabledPct}%` }"></i></div>
          </div>
        </div>
      </div>
    </aside>

    <!-- ===== MAIN ===== -->
    <div class="main">
      <header class="topbar">
        <div class="crumb">监控 / <b>{{ (route.meta.title as string) || 'Dashboard' }}</b></div>
        <div class="spacer"></div>
        <div class="pill svc"><span class="d"></span> 服务 <b>{{ store.services.length }}</b></div>
        <div class="pill ok"><span class="d"></span> 在线 <b>{{ onlineCount }} / {{ store.enabledServices.length }}</b></div>
        <div class="pill cache"><span class="d"></span> 策略 <b>{{ cacheTotal }}</b></div>
        <div class="clock">{{ now }}</div>
        <div class="avatar">AD</div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 248px 1fr;
  min-height: 100vh;
}

/* ===== sidebar ===== */
.side {
  border-right: 1px solid var(--line);
  background: linear-gradient(180deg, rgba(17, 24, 39, 0.7), rgba(10, 14, 22, 0.4));
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 6px 8px 18px;
}

.logo {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  font-family: var(--display);
  font-weight: 800;
  font-size: 15px;
  color: #04110f;
  background: linear-gradient(140deg, var(--teal), #7af0d8);
  box-shadow: 0 8px 22px -8px rgba(51, 216, 194, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.5);
  position: relative;
}

.logo::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 11px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
}

.name {
  font-family: var(--display);
  font-weight: 700;
  font-size: 18px;
  letter-spacing: 0.2px;
  color: var(--text);
}

.name b {
  color: var(--teal);
}

.tag {
  font-family: var(--mono);
  font-size: 9.5px;
  color: var(--text-faint);
  letter-spacing: 1px;
}

.nav-label {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--text-faint);
  text-transform: uppercase;
  padding: 14px 10px 8px;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav a {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-dim);
  text-decoration: none;
  font-weight: 600;
  font-size: 13.5px;
  position: relative;
  cursor: pointer;
  transition: 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.nav a svg {
  width: 18px;
  height: 18px;
  flex: none;
  opacity: 0.8;
}

.nav a::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  border-radius: 3px;
  background: var(--teal);
  transition: 0.25s;
}

.nav a:hover {
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
}

.nav a.active {
  background: linear-gradient(90deg, rgba(51, 216, 194, 0.14), rgba(51, 216, 194, 0.02));
  color: #fff;
}

.nav a.active::before {
  height: 60%;
}

.nav a.active svg {
  opacity: 1;
  color: var(--teal);
}

.side-foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.syscard {
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 13px;
  background: rgba(255, 255, 255, 0.02);
}

.syscard h4 {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 1.5px;
  color: var(--text-faint);
  text-transform: uppercase;
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 11px;
  font-weight: 600;
}

.syscard h4 .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--teal);
  animation: pulse 2s infinite;
}

.meter {
  margin-bottom: 10px;
}

.meter:last-child {
  margin-bottom: 0;
}

.meter .row {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-dim);
  margin-bottom: 5px;
}

.meter .row b {
  font-family: var(--mono);
  color: var(--text);
}

.bar {
  height: 5px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(90deg, var(--teal), var(--sky));
  transition: width 1.4s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.bar.amber i {
  background: linear-gradient(90deg, var(--amber), #ffd27a);
}

/* ===== topbar ===== */
.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 30px;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 30;
  background: rgba(10, 14, 22, 0.72);
  backdrop-filter: blur(10px);
}

.crumb {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--text-faint);
  letter-spacing: 0.5px;
}

.crumb b {
  color: var(--text-dim);
  font-weight: 500;
}

.spacer {
  flex: 1;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--mono);
  font-size: 11.5px;
  padding: 6px 11px;
  border-radius: 30px;
  border: 1px solid var(--line-2);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-dim);
  white-space: nowrap;
}

.pill .d {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.pill.ok .d {
  background: var(--teal);
  animation: pulse 2s infinite;
}

.pill.svc .d {
  background: var(--sky);
}

.pill.cache .d {
  background: var(--violet);
}

.pill b {
  color: var(--text);
}

.clock {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--text-dim);
  min-width: 62px;
  text-align: right;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3a4a6b, #22304a);
  display: grid;
  place-items: center;
  font-family: var(--display);
  font-weight: 700;
  font-size: 13px;
  color: #cfe0ff;
  border: 1px solid var(--line-2);
}

/* ===== content ===== */
.content {
  padding: 26px 30px 60px;
  max-width: 1480px;
  width: 100%;
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(51, 216, 194, 0.5);
  }
  70% {
    box-shadow: 0 0 0 7px rgba(51, 216, 194, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(51, 216, 194, 0);
  }
}

@media (max-width: 1080px) {
  .shell {
    grid-template-columns: 1fr;
  }
  .side {
    display: none;
  }
}

@media (max-width: 620px) {
  .content {
    padding: 18px 16px 50px;
  }
  .topbar {
    padding: 12px 16px;
  }
  .pill.cache,
  .clock {
    display: none;
  }
}
</style>
