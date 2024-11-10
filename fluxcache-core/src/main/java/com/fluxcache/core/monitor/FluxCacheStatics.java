package com.fluxcache.core.monitor;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;

/**
 * @author : wh
 * @date : 2024/9/22 16:25
 * @description:
 */
@Data
public class FluxCacheStatics {

    private LinkedList<FluxCacheInfo> fluxCacheInfos;

    private Long startTime;

    private static final int MAX_COUNT = 48;

    private AtomicInteger capacity;

    /**
     * 30分钟
     */
    private static final int HALF_HOUR = 1800000;

    public FluxCacheStatics() {
        this.fluxCacheInfos = new LinkedList<>();
        this.startTime = System.currentTimeMillis();
        this.capacity = new AtomicInteger(0);
    }

    public boolean isEmpty() {
        return capacity.get() == 0;
    }

    private boolean isFull() {
        return capacity.get() == MAX_COUNT;
    }

    public void createNewNode() {
        synchronized (this) {
            FluxCacheInfo lastNode = this.fluxCacheInfos.getLast();
            if (lastNode.getStartTime().get() + HALF_HOUR < System.currentTimeMillis()) {
                FluxCacheInfo info = new FluxCacheInfo();
                if (!isFull()) {
                    this.capacity.incrementAndGet();
                    lastNode.setEndTime(new AtomicLong(lastNode.getStartTime().get() + HALF_HOUR));
                } else {
                    this.fluxCacheInfos.removeFirst();
                }
                info.setStartTime(new AtomicLong(lastNode.getStartTime().get() + HALF_HOUR));
                info.setEndTime(new AtomicLong(lastNode.getEndTime().get() + HALF_HOUR));
                this.fluxCacheInfos.add(info);
            }
        }
    }

    public void init() {
        if (this.capacity.get() == 0) {
            synchronized (this) {
                if (this.capacity.get() == 0) {
                    this.capacity.compareAndSet(0, 1);
                    FluxCacheInfo info = new FluxCacheInfo();
                    info.setStartTime(new AtomicLong(this.getStartTime()));
                    this.fluxCacheInfos.add(info);
                }
            }
        }
    }

    public void incrementHit(long count, long loadTime) {
        isNewNode();
        if (this.capacity.get() > 0) {
            FluxCacheInfo lastNode = this.fluxCacheInfos.getLast();
            lastNode.getHit().add(count);
            lastNode.getRequestCount().add(count);
            lastNode.getMaxLoadTime().compareAndSet(lastNode.getMaxLoadTime().get(), Math.max(lastNode.getMaxLoadTime().get(), loadTime));
        }
    }

    public void incrementMissing(long count, long loadTime) {
        isNewNode();
        if (this.capacity.get() > 0) {
            FluxCacheInfo lastNode = this.fluxCacheInfos.getLast();
            lastNode.getFail().add(count);
            lastNode.getRequestCount().add(count);
            lastNode.getMaxLoadTime().compareAndSet(lastNode.getMaxLoadTime().get(), Math.max(lastNode.getMaxLoadTime().get(), loadTime));
        }
    }

    public void incrementEvict(long count, long loadTime) {
        isNewNode();
        if (this.capacity.get() > 0) {
            this.fluxCacheInfos.getLast().getEvictCount().add(count);
        }
    }

    public void isNewNode() {
        while (this.fluxCacheInfos.getLast().getStartTime().get() + HALF_HOUR < System.currentTimeMillis()) {
            createNewNode();
        }
    }

    public void incrementPut(long count, long loadTime) {
        isNewNode();
        if (this.capacity.get() > 0) {
            this.fluxCacheInfos.getLast().getPutCount().add(count);
        }
    }
}
