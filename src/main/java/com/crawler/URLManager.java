package com.crawler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe manager for URL scheduling and deduplication.
 *
 * Coordinates URL distribution among crawler worker threads:
 *  - ConcurrentHashMap-backed Set for O(1), thread-safe visited lookups
 *  - BlockingQueue for the producer/consumer hand-off between threads
 *  - AtomicInteger task counter to detect when all work is done
 *
 * Acts as a thread-safe mediator between the controller and the workers.
 */
public class URLManager {

    /**
     * Thread-safe set of visited URLs.
     * newKeySet() returns a concurrent Set backed by ConcurrentHashMap.
     */
    private final ConcurrentHashMap.KeySetView<String, Boolean> visitedUrls;

    /**
     * Thread-safe queue of URLs pending crawl.
     * LinkedBlockingQueue gives blocking poll() for waiting workers and
     * thread-safe offer() for producers, with FIFO (breadth-first) ordering.
     */
    private final BlockingQueue<URLDepthPair> urlQueue;

    /**
     * Number of URLs that have been scheduled but not yet fully processed.
     * A unit of "outstanding work" is counted from the moment a URL is
     * enqueued until the worker that handled it (and scheduled its children)
     * calls taskCompleted(). This is the key to race-free termination.
     */
    private final AtomicInteger outstanding;

    /** Maximum crawl depth (0 = seed only). */
    private final int maxDepth;

    /** Immutable pairing of a URL with the depth at which it was found. */
    public static final class URLDepthPair {
        private final String url;
        private final int depth;

        public URLDepthPair(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }

        public String getUrl() {
            return url;
        }

        public int getDepth() {
            return depth;
        }
    }

    /**
     * @param maxDepth maximum depth to crawl (0 = seed only)
     */
    public URLManager(int maxDepth) {
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.urlQueue = new LinkedBlockingQueue<>();
        this.outstanding = new AtomicInteger(0);
        this.maxDepth = maxDepth;
    }

    /**
     * Schedules a URL for crawling if it is new and within depth limits.
     *
     * Thread-safety: visitedUrls.add() is atomic and returns false if the
     * element already existed, so two threads cannot both schedule the same
     * URL. The outstanding counter is incremented BEFORE the URL is enqueued
     * so that a fast consumer cannot observe an empty queue + zero counter
     * (a false "complete") in between.
     *
     * @param url   the URL to schedule
     * @param depth the depth level of this URL
     * @return true if scheduled, false if duplicate or beyond max depth
     */
    public boolean scheduleUrl(String url, int depth) {
        if (depth > maxDepth) {
            return false;
        }

        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl.isEmpty()) {
            return false;
        }

        // Atomic check-and-add — the core dedup mechanism.
        if (visitedUrls.add(normalizedUrl)) {
            outstanding.incrementAndGet();         // count work BEFORE publishing
            urlQueue.offer(new URLDepthPair(normalizedUrl, depth));
            return true;
        }
        return false;
    }

    /**
     * Retrieves the next URL, blocking up to the timeout if none available.
     * The timeout lets workers periodically re-check the completion state.
     *
     * @return the next pair, or null on timeout / interruption
     */
    public URLDepthPair getNextUrl() {
        try {
            return urlQueue.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Marks one unit of work complete. Must be called exactly once per URL
     * that was successfully dequeued, AFTER any child links were scheduled.
     */
    public void taskCompleted() {
        outstanding.decrementAndGet();
    }

    /**
     * Crawling is complete only when there is no outstanding work.
     * Because outstanding is incremented before enqueue and decremented after
     * children are scheduled, "outstanding == 0" reliably means no URL is
     * queued and no worker is mid-flight able to produce more URLs.
     */
    public boolean isComplete() {
        return outstanding.get() == 0;
    }

    public int getVisitedCount() {
        return visitedUrls.size();
    }

    public boolean isVisited(String url) {
        return visitedUrls.contains(normalizeUrl(url));
    }

    /**
     * Normalizes a URL for consistent dedup: lowercase, trimmed, fragment
     * stripped, no trailing slash.
     */
    private String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.toLowerCase().trim();

        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex >= 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** Approximate queue size, for monitoring only. */
    public int getQueueSize() {
        return urlQueue.size();
    }

    /** Outstanding work count, for monitoring only. */
    public int getOutstandingCount() {
        return outstanding.get();
    }
}
