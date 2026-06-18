package com.crawler;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes crawl results to disk in two human-friendly formats:
 *  - CSV  (open in Excel / Google Sheets / any spreadsheet)
 *  - HTML (open in any web browser — this is the "see it" output)
 *
 * Both files are written into an output directory, time-stamped so each
 * run produces its own pair of reports.
 */
public final class ReportWriter {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final Path outputDir;

    /**
     * @param outputDir folder to write reports into (created if missing)
     */
    public ReportWriter(String outputDir) {
        this.outputDir = Path.of(outputDir);
    }

    /**
     * Writes both a CSV and an HTML report for the given results.
     *
     * @param results the crawl results to export
     * @return the path to the generated HTML file (the one you open)
     * @throws IOException if the files cannot be written
     */
    public Path write(List<CrawlResult> results) throws IOException {
        Files.createDirectories(outputDir);
        String stamp = LocalDateTime.now().format(STAMP);

        Path csvPath = outputDir.resolve("crawl-report_" + stamp + ".csv");
        Path htmlPath = outputDir.resolve("crawl-report_" + stamp + ".html");

        writeCsv(csvPath, results);
        writeHtml(htmlPath, results);

        return htmlPath;
    }

    /** Writes a comma-separated file with one row per crawled URL. */
    private void writeCsv(Path path, List<CrawlResult> results) throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Header row
            w.write("Depth,Status,URL,Title,LinkCount,Error\n");
            for (CrawlResult r : results) {
                w.write(String.valueOf(r.getDepth()));
                w.write(",");
                w.write(r.isSuccess() ? "OK" : "FAIL");
                w.write(",");
                w.write(csv(r.getUrl()));
                w.write(",");
                w.write(csv(r.getTitle()));
                w.write(",");
                w.write(String.valueOf(r.getLinkCount()));
                w.write(",");
                w.write(csv(r.getErrorMessage()));
                w.write("\n");
            }
        }
    }

    /** Writes a styled, self-contained HTML table you can open in a browser. */
    private void writeHtml(Path path, List<CrawlResult> results) throws IOException {
        long ok = results.stream().filter(CrawlResult::isSuccess).count();
        long fail = results.size() - ok;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"en\"><head><meta charset=\"UTF-8\">\n")
            .append("<title>Crawl Report</title>\n")
            .append("<style>\n")
            .append("  body{font-family:system-ui,Arial,sans-serif;margin:24px;color:#1a1a1a;}\n")
            .append("  h1{margin-bottom:4px;}\n")
            .append("  .meta{color:#555;margin-bottom:16px;}\n")
            .append("  table{border-collapse:collapse;width:100%;font-size:14px;}\n")
            .append("  th,td{border:1px solid #ddd;padding:8px 10px;text-align:left;}\n")
            .append("  th{background:#222;color:#fff;position:sticky;top:0;}\n")
            .append("  tr:nth-child(even){background:#f7f7f7;}\n")
            .append("  .ok{color:#0a7d28;font-weight:600;}\n")
            .append("  .fail{color:#c0291c;font-weight:600;}\n")
            .append("  a{color:#1456c4;text-decoration:none;}\n")
            .append("  a:hover{text-decoration:underline;}\n")
            .append("</style></head><body>\n");

        html.append("<h1>Crawl Report</h1>\n")
            .append("<div class=\"meta\">Generated ")
            .append(esc(LocalDateTime.now().toString()))
            .append(" &middot; Total: ").append(results.size())
            .append(" &middot; OK: ").append(ok)
            .append(" &middot; Failed: ").append(fail)
            .append("</div>\n");

        html.append("<table>\n<thead><tr>")
            .append("<th>#</th><th>Depth</th><th>Status</th><th>URL</th>")
            .append("<th>Title</th><th>Links</th><th>Error</th>")
            .append("</tr></thead>\n<tbody>\n");

        int i = 1;
        for (CrawlResult r : results) {
            html.append("<tr>")
                .append("<td>").append(i++).append("</td>")
                .append("<td>").append(r.getDepth()).append("</td>");

            if (r.isSuccess()) {
                html.append("<td class=\"ok\">OK</td>");
            } else {
                html.append("<td class=\"fail\">FAIL</td>");
            }

            String url = r.getUrl() == null ? "" : r.getUrl();
            html.append("<td><a href=\"").append(esc(url))
                .append("\" target=\"_blank\" rel=\"noopener\">")
                .append(esc(url)).append("</a></td>")
                .append("<td>").append(esc(r.getTitle())).append("</td>")
                .append("<td>").append(r.getLinkCount()).append("</td>")
                .append("<td>").append(esc(r.getErrorMessage())).append("</td>")
                .append("</tr>\n");
        }

        html.append("</tbody></table>\n</body></html>\n");

        Files.writeString(path, html.toString(), StandardCharsets.UTF_8);
    }

    /** Escapes a value for safe inclusion in a CSV cell. */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        // Quote if the value contains comma, quote, or newline; double inner quotes.
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Escapes a value for safe inclusion in HTML text. */
    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
