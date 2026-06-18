# Multi-Threaded Web Crawler

A concurrent web crawler built in Java 17 that demonstrates multithreading, thread-safe data structures, and web scraping using JSoup. The crawler processes webpages in parallel, prevents duplicate crawling, and generates HTML and CSV reports for visualization.

## Features

* Multi-threaded crawling using ExecutorService
* Thread-safe URL scheduling with BlockingQueue
* Duplicate URL prevention using ConcurrentHashMap
* Configurable crawl depth
* Graceful shutdown mechanism
* Crawl statistics and performance metrics
* HTML report generation
* CSV report export
* JSoup-based HTML parsing and link extraction

## Technologies Used

* Java 17
* Maven
* JSoup
* ExecutorService
* ConcurrentHashMap
* BlockingQueue
* AtomicInteger

## Requirements

* Java 17+
* Maven 3.6+

## Project Structure

multi-threaded-web-crawler/

├── pom.xml

├── README.md

├── .gitignore

├── src/main/java/com/crawler/

│ ├── Main.java

│ ├── WebCrawler.java

│ ├── CrawlerWorker.java

│ ├── URLManager.java

│ ├── CrawlResult.java

│ └── ReportWriter.java

## Quick Start

Compile the project:

```bash
mvn clean compile
```

Run the crawler:

```bash
mvn exec:java
```

Build executable JAR:

```bash
mvn clean package
java -jar target/multi-threaded-web-crawler-1.0.0-jar-with-dependencies.jar
```

## Configuration

Edit Main.java:

```java
final int threadCount = 4;
final int maxDepth = 2;

final String[] seedUrls = {
    "https://books.toscrape.com"
};
```

## Architecture

Main.java
↓
WebCrawler
↓
ExecutorService (Thread Pool)
↓
CrawlerWorker × N
↓
URLManager
├── BlockingQueue
├── ConcurrentHashMap
└── AtomicInteger
↓
JSoup
↓
ReportWriter
├── HTML Report
└── CSV Report

## Sample Output

* 586 URLs processed
* 586 successful crawls
* 0 failures
* 8.1 pages/second

## Thread-Safety Mechanisms

| Mechanism         | Purpose                       |
| ----------------- | ----------------------------- |
| ConcurrentHashMap | Prevent duplicate crawling    |
| BlockingQueue     | Thread-safe URL scheduling    |
| AtomicInteger     | Track outstanding crawl tasks |
| volatile flag     | Worker shutdown visibility    |

## Future Improvements

* Domain filtering
* Robots.txt support
* Database storage
* Spring Boot web dashboard
* Real-time crawl monitoring

## License

MIT License – Educational Use
