package com.crawler;
import java.util.Collections;
import java.util.List;
/**
 * Immutable data class representing the result of crawling a single URL.
 *
 * Encapsulates:
 *  - The URL that was crawled
 *  - The page title extracted from HTML
 *  - All hyperlinks discovered on the page
 *  - The depth level at which this page was found
 *  - Success/failure status of the crawl attempt
 *  - Error message if the crawl failed
 */
public final class CrawlResult {
    private final String url;
    private final String title;
    private final List<String> links;
    private final int depth;
    private final boolean success;
    private final String errorMessage;
    /** Private constructor — use the static factory methods. */
    private CrawlResult(String url, String title, List<String> links,
                        int depth, boolean success, String errorMessage) {
        this.url = url;
        this.title = title;
        // Defensive immutable copy.
        this.links = (links != null) ? List.copyOf(links) : Collections.emptyList();
        this.depth = depth;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    /**
     * Factory method for a successful crawl result.
     *
     * @param url   the crawled URL
     * @param title the page title
     * @param links discovered hyperlinks
     * @param depth the crawl depth level
     * @return a CrawlResult marked as successful
     */
    public static CrawlResult success(String url, String title,
                                      List<String> links, int depth) {
        return new CrawlResult(url, title, links, depth, true, null);
    }
    /**
     * Factory method for a failed crawl result.
     *
     * @param url          the URL that failed
     * @param depth        the depth at which it failed
     * @param errorMessage description of the failure
     * @return a CrawlResult marked as failed
     */
    public static CrawlResult failure(String url, int depth, String errorMessage) {
        return new CrawlResult(url, null, Collections.emptyList(),
                depth, false, errorMessage);
    }
    // --- Getters only; no setters keeps the object immutable. ---
    public String getUrl() {
        return url;
    }
    public String getTitle() {
        return title;
    }
    /** Returns an immutable view of discovered links. */
    public List<String> getLinks() {
        return links;
    }
    public int getDepth() {
        return depth;
    }
    public boolean isSuccess() {
        return success;
    }
    public String getErrorMessage() {
        return errorMessage;
    }
    /** Number of links discovered on this page. */
    public int getLinkCount() {
        return links.size();
    }
    @Override
    public String toString() {
        if (success) {
            return String.format("CrawlResult[url=%s, title=%s, links=%d, depth=%d]",
                    url, title, links.size(), depth);
        }
        return String.format("CrawlResult[url=%s, FAILED: %s, depth=%d]",
                url, errorMessage, depth);
    }
}