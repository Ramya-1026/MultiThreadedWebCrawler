package com.crawler;

/**
 * Entry point for the Multi-Threaded Web Crawler.
 */
public class Main {

    public static void main(String[] args) {

        // Number of worker threads
        final int threadCount = 4;

        // Maximum crawl depth
        final int maxDepth = 2;

        // Seed URLs
        final String[] seedUrls = {
                "https://books.toscrape.com"
        };

        WebCrawler crawler = new WebCrawler(threadCount, maxDepth);

        crawler.crawl(seedUrls);

        System.out.println("\nAccessing results programmatically:");
        System.out.println("Total results available: " + crawler.getResults().size());

        crawler.getResults().stream()
                .filter(CrawlResult::isSuccess)
                .limit(5)
                .forEach(result ->
                        System.out.printf(
                                " - %s (%d links)%n",
                                result.getTitle(),
                                result.getLinkCount()
                        )
                );
    }
}