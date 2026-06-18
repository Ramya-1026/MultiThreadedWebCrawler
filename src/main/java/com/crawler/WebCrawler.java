package com.crawler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main controller (Facade) for the multi-threaded crawler.
 *
 * Responsibilities:
 *  - Owns the ExecutorService fixed thread pool
 *  - Coordinates workers through the shared URLManager
 *  - Aggregates results
 *  - Handles graceful shutdown
 *
 * All shared state is held in thread-safe types (ConcurrentHashMap,
 * BlockingQueue, AtomicInteger, synchronizedList).
 */
public class WebCrawler {

    private static final int DEFAULT_THREAD_COUNT = 5;
    private static final int DEFAULT_MAX_DEPTH = 2;
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final String OUTPUT_DIR = "output";

    private final int threadCount;
    private final int maxDepth;
    private final URLManager urlManager;
    private final List<CrawlResult> results;
    private final AtomicInteger successCount;
    private final AtomicInteger failureCount;

    private ExecutorService executorService;
    private List<CrawlerWorker> workers;
    private volatile boolean isRunning;

    /** Crawler with defaults: 5 threads, max depth 2. */
    public WebCrawler() {
        this(DEFAULT_THREAD_COUNT, DEFAULT_MAX_DEPTH);
    }

    /**
     * @param threadCount number of concurrent worker threads (>= 1)
     * @param maxDepth    maximum crawl depth (>= 0; 0 = seed only)
     */
    public WebCrawler(int threadCount, int maxDepth) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be at least 1");
        }
        if (maxDepth < 0) {
            throw new IllegalArgumentException("Max depth cannot be negative");
        }
        this.threadCount = threadCount;
        this.maxDepth = maxDepth;
        this.urlManager = new URLManager(maxDepth);
        this.results = Collections.synchronizedList(new ArrayList<>());
        this.successCount = new AtomicInteger(0);
        this.failureCount = new AtomicInteger(0);
        this.isRunning = false;
    }

    /**
     * Crawls starting from one or more seed URLs. Blocks until complete.
     */
    public void crawl(String... seedUrls) {
        if (seedUrls == null || seedUrls.length == 0) {
            throw new IllegalArgumentException("At least one seed URL required");
        }

        isRunning = true;
        long startTime = System.currentTimeMillis();

        System.out.println("=".repeat(60));
        System.out.println("MULTI-THREADED WEB CRAWLER");
        System.out.println("=".repeat(60));
        System.out.printf("Threads: %d | Max Depth: %d%n", threadCount, maxDepth);
        System.out.println("-".repeat(60));

        // Fixed thread pool: bounded resource usage, threads reused across tasks.
        executorService = Executors.newFixedThreadPool(threadCount);
        workers = new ArrayList<>();

        for (String seedUrl : seedUrls) {
            if (urlManager.scheduleUrl(seedUrl, 0)) {
                System.out.println("Seed URL queued: " + seedUrl);
            }
        }

        for (int i = 0; i < threadCount; i++) {
            CrawlerWorker worker = new CrawlerWorker(
                    urlManager, this::handleResult, maxDepth);
            workers.add(worker);
            executorService.submit(worker);
        }

        System.out.println("-".repeat(60));
        System.out.println("Crawling started with " + threadCount + " worker threads...\n");

        waitForCompletion();
        shutdown();

        printSummary(System.currentTimeMillis() - startTime);
        try {
    ReportWriter writer = new ReportWriter(OUTPUT_DIR);
    java.nio.file.Path htmlReport = writer.write(getResults());

    System.out.println("\nReports written to ./" + OUTPUT_DIR + "/");
    System.out.println("Open this in your browser:");
    System.out.println("  " + htmlReport.toAbsolutePath());

} catch (java.io.IOException e) {
    System.out.println("\nCould not write reports: " + e.getMessage());
}
    }

    /** Worker callback; thread-safe via synchronized list + atomic counters. */
    private void handleResult(CrawlResult result) {
        results.add(result);
        if (result.isSuccess()) {
            successCount.incrementAndGet();
            System.out.printf("[Depth %d] [OK]   %s%n",
                    result.getDepth(), truncate(result.getUrl(), 70));
            System.out.printf("            Title: %s | Links: %d%n",
                    truncate(result.getTitle(), 40), result.getLinkCount());
        } else {
            failureCount.incrementAndGet();
            System.out.printf("[Depth %d] [FAIL] %s%n",
                    result.getDepth(), truncate(result.getUrl(), 70));
            System.out.printf("            Error: %s%n", result.getErrorMessage());
        }
    }

    /** Polls the manager until all outstanding work is finished. */
    private void waitForCompletion() {
        while (isRunning) {
            if (urlManager.isComplete()) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Graceful shutdown: signal workers, stop accepting tasks, await
     * termination, then force-stop if the timeout is exceeded.
     */
    private void shutdown() {
        isRunning = false;
        for (CrawlerWorker worker : workers) {
            worker.shutdown();
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(
                    SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("\nForcing shutdown after timeout...");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void printSummary(long durationMs) {
        double seconds = durationMs / 1000.0;
        System.out.println("\n" + "=".repeat(60));
        System.out.println("CRAWL SUMMARY");
        System.out.println("=".repeat(60));
        System.out.printf("Total URLs processed: %d%n", results.size());
        System.out.printf("Successful crawls:    %d%n", successCount.get());
        System.out.printf("Failed crawls:        %d%n", failureCount.get());
        System.out.printf("Unique URLs visited:  %d%n", urlManager.getVisitedCount());
        System.out.printf("Total time:           %.2f seconds%n", seconds);
        if (seconds > 0) {
            System.out.printf("Average rate:         %.2f pages/second%n",
                    results.size() / seconds);
        }
        System.out.println("=".repeat(60));
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "[null]";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /** Immutable snapshot of results. */
    public List<CrawlResult> getResults() {
        synchronized (results) {
            return Collections.unmodifiableList(new ArrayList<>(results));
        }
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public int getFailureCount() {
        return failureCount.get();
    }
}
