package com.fluxcache.core.monitor;

import com.fluxcache.core.properties.FluxCacheProperties.HotKeyConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 热 Key 自动探测器。
 *
 * <p>核心读路径为 {@link #record(String, String, boolean, long)}，同步、O(1)、无分配：
 * 先写入该 key 的分片滑动窗口（{@link HotKeyWindow}），再做一次廉价的窗口判定。
 * 判定规则：窗口读 QPS ≥ {@code hotQpsThreshold} 且 窗口未命中 ≥ {@code hotMissThreshold}，
 * 且需连续 {@code confirmTicks} 个分片被判定为热才对外宣布（防冷启动/瞬时尖峰误报）。</p>
 *
 * <p>对外通知受 {@code notifyIntervalMs} 冷却节流；热度消退后恢复通知。
 * 统计表容量受限（{@code maxHotKeyCapacity}），超限按创建顺序 FIFO 淘汰（O(1)），
 * 不影响缓存链路。</p>
 *
 * @author : wh
 */
@Slf4j
public class FluxHotKeyDetector {

    private static final String KEY_SEPARATOR = "::hot::";

    private static final int DEFAULT_WINDOW_SECONDS = 60;

    private static final int DEFAULT_SLOT_SECONDS = 10;

    private static final long DEFAULT_MAX_CAPACITY = 200_000L;

    private final HotKeyConfig config;

    private final HotKeyClock clock;

    private final int slotCount;

    private final long slotLengthMillis;

    private final ConcurrentMap<String, HotKeyEntry> table = new ConcurrentHashMap<>();

    private final ConcurrentLinkedDeque<HotKeyEntry> insertOrder = new ConcurrentLinkedDeque<>();

    private final List<FluxHotKeyListener> listeners = new CopyOnWriteArrayList<>();

    public FluxHotKeyDetector(HotKeyConfig config) {
        this(config, System::currentTimeMillis);
    }

    public FluxHotKeyDetector(HotKeyConfig config, HotKeyClock clock) {
        this.config = config;
        this.clock = clock;
        int windowSeconds = Math.max(1, config != null && config.getWindowSeconds() > 0 ? config.getWindowSeconds() : DEFAULT_WINDOW_SECONDS);
        int slotSeconds = Math.max(1, config != null && config.getSlotSeconds() > 0 ? config.getSlotSeconds() : DEFAULT_SLOT_SECONDS);
        this.slotCount = Math.max(1, (windowSeconds + slotSeconds - 1) / slotSeconds);
        this.slotLengthMillis = (long) slotSeconds * 1000L;
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    /**
     * 触发一次容量收敛（超限 FIFO 淘汰）。读写路径在写入时自动执行，一般无需外部调用。
     */
    public void cleanUp() {
        evictOverflow();
    }

    public HotKeyConfig getConfig() {
        return config;
    }

    public void addFluxHotKeyListener(FluxHotKeyListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 上报一次读写事件（读路径接入点，来自 {@link DefaultFluxCacheMonitor}）。
     *
     * @param cacheName 缓存名
     * @param key       key
     * @param miss      是否未命中
     * @param count     事件数，通常为 1
     */
    public void record(String cacheName, String key, boolean miss, long count) {
        if (!isEnabled() || cacheName == null || key == null || count <= 0L) {
            return;
        }
        long now = clock.millis();
        HotKeyEntry entry = getOrCreate(buildKey(cacheName, key), cacheName, key);
        entry.window.record(miss, count, now);
        evaluate(entry, now);
    }

    private HotKeyEntry getOrCreate(String compositeKey, String cacheName, String key) {
        HotKeyEntry existing = table.get(compositeKey);
        if (existing != null) {
            return existing;
        }
        HotKeyEntry created = new HotKeyEntry();
        created.compositeKey = compositeKey;
        created.cacheName = cacheName;
        created.key = key;
        created.window = new HotKeyWindow(slotCount, slotLengthMillis);
        HotKeyEntry raced = table.putIfAbsent(compositeKey, created);
        created = raced != null ? raced : created;
        if (raced == null) {
            insertOrder.addLast(created);
            evictOverflow();
        }
        return created;
    }

    /**
     * 容量收敛：按创建先后（FIFO）淘汰，保证 O(1) 热点路径代价。
     */
    private void evictOverflow() {
        long capacity = config != null ? Math.max(1L, config.getMaxHotKeyCapacity()) : DEFAULT_MAX_CAPACITY;
        while (table.size() > capacity) {
            HotKeyEntry victim = insertOrder.pollFirst();
            if (victim == null) {
                break;
            }
            table.remove(victim.compositeKey, victim);
        }
    }

    /**
     * 当前被统计追踪的 key 数量（含不再热但仍在统计窗口内的 key）。
     *
     * @return 数量
     */
    public int getTrackedCount() {
        return table.size();
    }

    /**
     * 判断某 key 是否仍在统计追踪中（未被容量淘汰）。
     *
     * @param cacheName 缓存名
     * @param key       key
     * @return true 表示仍被追踪
     */
    public boolean isTracked(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return false;
        }
        return table.containsKey(buildKey(cacheName, key));
    }

    /**
     * 判断是否为热 key。
     *
     * @param cacheName 缓存名
     * @param key       key
     * @return true 表示当前处于热状态
     */
    public boolean isHotKey(String cacheName, String key) {
        if (cacheName == null || key == null) {
            return false;
        }
        HotKeyEntry entry = table.get(buildKey(cacheName, key));
        return entry != null && entry.hot;
    }

    /**
     * 当前所有热 key 快照（按最近活跃时间倒序，供 Dashboard / 告警消费）。
     *
     * @return 热 key 集合
     */
    public List<FluxHotKeySnapshot> getHotKeysSnapshot() {
        List<FluxHotKeySnapshot> result = new ArrayList<>();
        long now = clock.millis();
        table.forEach((name, entry) -> {
            synchronized (entry) {
                if (entry.hot) {
                    result.add(buildSnapshot(entry, now));
                }
            }
        });
        result.sort(Comparator.comparingLong(FluxHotKeySnapshot::getLastActiveTime).reversed());
        return result;
    }

    /**
     * 二次确认复杂判定的评估入口（每次 {@link #record} 同步调用）。
     *
     * <p>状态机维护：{@code thisTickHot}（当前分片是否热）、{@code consecutiveHotTicks}（上一分片及以前连续热的数量）。
     * 冷启动 / 跨窗口空闲时中断连续计数，避免瞬时尖峰或历史热度误报。</p>
     */
    private void evaluate(HotKeyEntry entry, long now) {
        FluxHotKeySnapshot notification = null;
        boolean recovered = false;
        synchronized (entry) {
            long tick = now / slotLengthMillis;
            boolean nowHot = isHot(entry, now);
            if (tick != entry.lastEvalTick) {
                if (entry.lastEvalTick != Long.MIN_VALUE && tick - entry.lastEvalTick > 1L) {
                    // 跨分片空闲：中断历史连续热度
                    entry.consecutiveHotTicks = 0;
                } else if (entry.thisTickHot) {
                    entry.consecutiveHotTicks++;
                } else {
                    entry.consecutiveHotTicks = 0;
                }
                entry.lastEvalTick = tick;
            }
            entry.thisTickHot = nowHot;

            // 需当前分片仍热，且历史连续热分片累计达到 confirmTicks - 1
            int confirm = Math.max(1, config.getConfirmTicks());
            boolean confirmed = nowHot && entry.consecutiveHotTicks >= confirm - 1;

            if (confirmed) {
                boolean wasHot = entry.hot;
                entry.hot = true;
                if (entry.hotSince == 0L) {
                    entry.hotSince = now;
                }
                boolean shouldNotify = !wasHot || now - entry.lastNotifyTime >= config.getNotifyIntervalMs();
                if (shouldNotify) {
                    entry.lastNotifyTime = now;
                    notification = buildSnapshot(entry, now);
                }
            } else if (entry.hot) {
                entry.hot = false;
                entry.hotSince = 0L;
                entry.lastNotifyTime = 0L;
                recovered = true;
                notification = buildSnapshot(entry, now);
            }
        }
        // listener callbacks run OUTSIDE the entry lock: a slow listener must not stall the read path
        if (notification != null) {
            if (recovered) {
                fireRecovered(notification);
            } else {
                fireDetected(notification);
            }
        }
    }

    private boolean isHot(HotKeyEntry entry, long now) {
        long[] counters = entry.window.snapshot(now);
        long total = counters[0] + counters[1];
        double windowSeconds = (double) windowMillis() / 1000.0;
        double qps = total / windowSeconds;
        return qps >= config.getHotQpsThreshold()
                && counters[1] >= config.getHotMissThreshold();
    }

    private long windowMillis() {
        return (long) slotCount * slotLengthMillis;
    }

    private FluxHotKeySnapshot buildSnapshot(HotKeyEntry entry, long now) {
        long[] counters = entry.window.snapshot(now);
        long hit = counters[0];
        long miss = counters[1];
        long total = hit + miss;
        double windowSeconds = (double) windowMillis() / 1000.0;
        double qps = windowSeconds <= 0 ? 0.0 : (double) total / windowSeconds;
        double hitRate = total == 0 ? 0.0 : (double) hit / total;
        return FluxHotKeySnapshot.builder()
                .cacheName(entry.cacheName)
                .key(entry.key)
                .hitCount(hit)
                .missCount(miss)
                .qps(qps)
                .hitRate(hitRate)
                .hot(entry.hot)
                .hotSince(entry.hotSince)
                .lastActiveTime(now)
                .build();
    }

    private void fireDetected(FluxHotKeySnapshot snapshot) {
        if (log.isDebugEnabled()) {
            log.debug("[FluxCache] hot key detected cache={} key={} qps={} hitRate={}",
                    snapshot.getCacheName(), snapshot.getKey(), snapshot.getQps(), snapshot.getHitRate());
        }
        log.info("[FluxCache] HOT KEY detected cache={} key={} qps={} miss={}",
                snapshot.getCacheName(), snapshot.getKey(), snapshot.getQps(), snapshot.getMissCount());
        for (FluxHotKeyListener listener : listeners) {
            try {
                listener.onHotKeyDetected(snapshot);
            } catch (Throwable t) {
                log.warn("[Hotkey] listener onHotKeyDetected error listener={}", listener, t);
            }
        }
    }

    private void fireRecovered(FluxHotKeySnapshot snapshot) {
        if (log.isDebugEnabled()) {
            log.debug("[Hotkey] hot key recovered cache={} key={}", snapshot.getCacheName(), snapshot.getKey());
        }
        for (FluxHotKeyListener listener : listeners) {
            try {
                listener.onHotKeyRecovered(snapshot);
            } catch (Throwable t) {
                log.warn("[Hotkey] listener onHotKeyRecovered error listener={}", listener, t);
            }
        }
    }

    private String buildKey(String cacheName, String key) {
        return cacheName + KEY_SEPARATOR + key;
    }

    /**
     * 单个 key 的统计 + 状态。
     */
    private static final class HotKeyEntry {

        String compositeKey;

        String cacheName;

        String key;

        HotKeyWindow window;

        /**
         * 上一次判定所在 tick
         */
        long lastEvalTick = Long.MIN_VALUE;

        /**
         * 当前分片是否判定为热（最近一次评估结果）
         */
        boolean thisTickHot;

        /**
         * 连续判定为热的 tick 数
         */
        int consecutiveHotTicks;

        /**
         * 当前是否热
         */
        boolean hot;

        /**
         * 进入热状态时间戳
         */
        long hotSince;

        /**
         * 上次对外通知时间戳（冷却节流）
         */
        long lastNotifyTime;
    }
}