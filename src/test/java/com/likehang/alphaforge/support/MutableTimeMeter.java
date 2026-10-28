package com.likehang.alphaforge.support;

import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class MutableTimeMeter implements TimeMeter {

    private final AtomicLong time = new AtomicLong();

    public void advance(Duration duration) { time.addAndGet(duration.toNanos()); }

    @Override
    public long currentTimeNanos() { return time.get(); }

    @Override
    public boolean isWallClockBased() { return false; }
}
