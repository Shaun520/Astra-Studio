package com.example.astrastudioopenai.service.tools.web;

import com.example.astrastudioopenai.service.tools.ToolExecutionException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Component
public class WebScraperTool {
    private static final Logger logger = LoggerFactory.getLogger(WebScraperTool.class);

    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 2;
    private static final long MIN_REQUEST_INTERVAL_MS = 1000;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
    };

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private int userAgentIndex = 0;

    @Tool("抓取指定URL的网页内容，返回纯文本和结构化数据")
    public ScrapedContent scrape(
            @P("要抓取的网页URL（必须以http://或https://开头）") String url,
            @P("CSS选择器，用于提取特定内容（可选）") String selector) throws ToolExecutionException {

        validateUrl(url);
        enforceRateLimit();

        logger.info("Starting web scraping: url={}, selector={}", url, selector);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                ScrapedContent content = performScraping(url, selector);
                logger.info("Web scraping completed: title='{}', textLength={}",
                        content.getTitle(), content.getText().length());
                return content;
            } catch (Exception e) {
                lastException = e;
                logger.warn("Scraping attempt {}/{} failed for {}: {}",
                        attempt, MAX_RETRIES + 1, url, e.getMessage());

                if (attempt <= MAX_RETRIES && isRecoverableError(e)) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ToolExecutionException("scrape_webpage", "抓取被中断", ie);
                    }
                }
            }
        }

        throw new ToolExecutionException("scrape_webpage",
                "网页抓取失败，已重试" + (MAX_RETRIES + 1) + "次: " + lastException.getMessage(),
                lastException);
    }

    private void validateUrl(String url) throws ToolExecutionException {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }

        if (!URL_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException("无效的URL格式: " + url);
        }

        try {
            URL validatedUrl = new URL(url);
            String protocol = validatedUrl.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                throw new IllegalArgumentException("只支持HTTP/HTTPS协议: " + protocol);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL格式错误: " + e.getMessage());
        }
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long lastRequest = lastRequestTime.get();
        long elapsed = now - lastRequest;

        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try {
                TimeUnit.MILLISECONDS.sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    private boolean isRecoverableError(Exception e) {
        String message = e.getMessage();
        if (message == null)
            return false;

        return message.contains("403") ||
                message.contains("429") ||
                message.contains("timeout") ||
                message.contains("Connection refused");
    }

    private ScrapedContent performScraping(String url, String selector) throws Exception {
        Document document = Jsoup.connect(url)
                .userAgent(getNextUserAgent())
                .timeout(DEFAULT_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get();

        int statusCode = document.connection().response().statusCode();
        if (statusCode == 403 || statusCode == 401) {
            throw new RuntimeException("Access denied (" + statusCode + "). Website may block automated requests.");
        }

        String title = document.title();

        String text;
        if (selector != null && !selector.trim().isEmpty()) {
            text = extractWithSelector(document, selector);
        } else {
            text = extractMainContent(document);
        }

        Map<String, String> metadata = extractMetadata(document);
        List<String> links = extractLinks(document);

        return new ScrapedContent(title, text, metadata, links);
    }

    private String extractWithSelector(Document document, String selector) {
        Elements elements = document.select(selector);
        if (elements.isEmpty()) {
            logger.warn("CSS selector '{}' matched no elements", selector);
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Element element : elements) {
            sb.append(element.text()).append("\n");
        }
        return sb.toString().trim();
    }

    private String extractMainContent(Document document) {
        document.select("script, style, nav, header, footer, aside, .advertisement").remove();

        Element body = document.body();
        return body.text().trim();
    }

    private Map<String, String> extractMetadata(Document document) {
        Map<String, String> metadata = new LinkedHashMap<>();

        Element descriptionMeta = document.selectFirst("meta[name=description]");
        if (descriptionMeta != null) {
            metadata.put("description", descriptionMeta.attr("content"));
        }

        Element keywordsMeta = document.selectFirst("meta[name=keywords]");
        if (keywordsMeta != null) {
            metadata.put("keywords", keywordsMeta.attr("content"));
        }

        Element authorMeta = document.selectFirst("meta[name=author]");
        if (authorMeta != null) {
            metadata.put("author", authorMeta.attr("content"));
        }

        Element ogTitle = document.selectFirst("meta[property=og:title]");
        if (ogTitle != null) {
            metadata.put("ogTitle", ogTitle.attr("content"));
        }

        Element publishTime = document.selectFirst("meta[property=article:published_time]");
        if (publishTime != null) {
            metadata.put("publishDate", publishTime.attr("content"));
        }

        return metadata;
    }

    private List<String> extractLinks(Document document) {
        List<String> links = new ArrayList<>();
        Elements anchors = document.select("a[href]");

        for (Element anchor : anchors) {
            String href = anchor.absUrl("href");
            if (!href.isEmpty() && !href.startsWith("#") && !href.startsWith("javascript:")) {
                links.add(href);
            }
        }

        return links.subList(0, Math.min(links.size(), 50));
    }

    private synchronized String getNextUserAgent() {
        String userAgent = USER_AGENTS[userAgentIndex % USER_AGENTS.length];
        userAgentIndex++;
        return userAgent;
    }
}
