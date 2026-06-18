# Multi-Threaded Web Crawler

A concurrent web crawler in **Java 17** demonstrating multithreading with
`ExecutorService`, `ConcurrentHashMap`, and `BlockingQueue`.

## Features
- Fixed-thread-pool architecture via `ExecutorService`
- Thread-safe URL management (`ConcurrentHashMap` + `BlockingQueue`)
- Configurable crawl depth
- Race-free duplicate prevention and termination
- Graceful shutdown
- Crawl statistics

## Requirements
- Java 17+
- Maven 3.6+

## Project Structure
```
multi-threaded-web-crawler/
├── pom.xml
├── README.md
├── .gitignore
└── src/main/java/com/crawler/
    ├── Main.java            # Entry point
    ├── WebCrawler.java      # Controller / facade
    ├── CrawlerWorker.java   # Runnable crawl worker
    ├── URLManager.java      # Thread-safe scheduling + dedup
    └── CrawlResult.java     # Immutable result object
```

## Quick Start
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.crawler.Main"

# Or a self-contained JAR:
mvn clean package
java -jar target/multi-threaded-web-crawler-1.0.0-jar-with-dependencies.jar
```

## Configuration
Edit `Main.java`:
```java
final int threadCount = 4;
final int maxDepth = 2;
final String[] seedUrls = {
    "https://books.toscrape.com"
};
```

## Architecture
```
WebCrawler (Facade)
    │  schedules seeds, owns the pool
    ▼
ExecutorService (fixed pool) ── runs ──> CrawlerWorker × N
    │                                         │
    └──────────── share ─────────────> URLManager
                                          ├── BlockingQueue  (pending URLs)
                                          ├── ConcurrentHashMap set (visited)
                                          └── AtomicInteger   (outstanding work)
                                                  │
                                          workers fetch via JSoup
```

## Thread-Safety Mechanisms
| Mechanism | Role |
|-----------|------|
| `ConcurrentHashMap` set | Atomic check-and-add for dedup |
| `BlockingQueue` | Producer/consumer URL hand-off |
| `AtomicInteger` | Lock-free counters + termination |
| `volatile` flag | Visible cooperative shutdown signal |

## License
MIT — educational use.
