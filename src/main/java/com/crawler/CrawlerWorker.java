package com.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runnable worker that performs the actual crawling.
 *
 * Each instance runs on a thread in the ExecutorService pool and loops:
 *  1. Take the next URL from the URLManager (blocks briefly if empty)
 *  2. Download and parse the page with JSoup
 *  3. Extract links and the page title
 *  4. Schedule discovered links for the next depth
 *  5. Report the result via a callback
 *  6. Repeat until all work is done or shutdown is requested
 */
public class CrawlerWorker implements Runnable {

    private static final int CONNECT_TIMEOUT_MS = 10_000; // connect + read
    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; JavaWebCrawler/1.0; Educational Project)";

    private final URLManager urlManager;
    private final Consumer<CrawlResult> resultCallback;
    private final int maxDepth;

    /** Cooperative shutdown flag; volatile for cross-thread visibility. */
    private volatile boolean running = true;

    public CrawlerWorker(URLManager urlManager,
                         Consumer<CrawlResult> resultCallback,
                         int maxDepth) {
        this.urlManager = urlManager;
        this.resultCallback = resultCallback;
        this.maxDepth = maxDepth;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            URLManager.URLDepthPair urlPair = urlManager.getNextUrl();

            if (urlPair == null) {
                // Nothing available right now. Exit only if truly complete.
                if (urlManager.isComplete()) {
                    break;
                }
                continue;
            }

            try {
                CrawlResult result = crawlPage(urlPair.getUrl(), urlPair.getDepth());

                // Schedule children BEFORE marking this task done (in finally),
                // so the outstanding counter never hits zero prematurely.
                if (result.isSuccess() && urlPair.getDepth() < maxDepth) {
                    for (String link : result.getLinks()) {
                        urlManager.scheduleUrl(link, urlPair.getDepth() + 1);
                    }
                }

                if (resultCallback != null) {
                    resultCallback.accept(result);
                }
            } finally {
                // Always release the unit of work, even on unexpected error.
                urlManager.taskCompleted();
            }
        }
    }

    /**
     * Fetches and parses a single page.
     *
     * @param url   the URL to crawl
     * @param depth current depth level
     * @return a CrawlResult with page data or error info
     */
    private CrawlResult crawlPage(String url, int depth) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(CONNECT_TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .get();

            String title = document.title();
            if (title == null || title.isBlank()) {
                title = "[No Title]";
            }

            List<String> links = extractLinks(document);
            return CrawlResult.success(url, title, links, depth);

        } catch (IOException e) {
            return CrawlResult.failure(url, depth, "IO Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return CrawlResult.failure(url, depth, "Invalid URL: " + e.getMessage());
        } catch (Exception e) {
            return CrawlResult.failure(url, depth,
                    "Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Extracts absolute, crawlable HTTP(S) links from the document.
     * JSoup resolves relative URLs against the document base automatically.
     */
    private List<String> extractLinks(Document document) {
        List<String> links = new ArrayList<>();
        Elements anchorTags = document.select("a[href]");

        for (Element anchor : anchorTags) {
            String absoluteUrl = anchor.absUrl("href");
            if (isValidCrawlableUrl(absoluteUrl)) {
                links.add(absoluteUrl);
            }
        }
        return links;
    }

    /**
     * Filters out non-HTTP URLs and common binary/non-HTML file types.
     */
    private boolean isValidCrawlableUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        String lowerUrl = url.toLowerCase();
        String[] excludedExtensions = {
                ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".svg", ".webp",   // images
                ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",  // documents
                ".zip", ".rar", ".tar", ".gz", ".7z",                       // archives
                ".mp3", ".mp4", ".avi", ".mov", ".wmv", ".flv",             // media
                ".css", ".js", ".json", ".xml",                             // code/data
                ".exe", ".dmg", ".iso"                                      // binaries
        };
        for (String ext : excludedExtensions) {
            if (lowerUrl.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }

    /** Requests cooperative shutdown; the worker stops after its current task. */
    public void shutdown() {
        this.running = false;
    }
}
