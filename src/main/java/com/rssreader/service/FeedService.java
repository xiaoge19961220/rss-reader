package com.rssreader.service;

import com.rometools.rome.feed.module.DCModule;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.rssreader.model.Article;
import com.rssreader.model.Feed;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RSS/Atom 订阅抓取与解析服务。
 */
public class FeedService {

    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "RSS-Reader/1.0";

    // 预编译正则，复用
    private static final Pattern PUB_DATE_RE =
            Pattern.compile("<pubDate>(.*?)</pubDate>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // 兼容非标准日期格式（按常见度排序，减少解析尝试次数）
    private static final DateTimeFormatter[] FALLBACK_FMTS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss  Z"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_DATE_TIME,
    };

    public Feed fetchFeed(String feedUrl) throws Exception {
        return fetchFeed(feedUrl, null);
    }

    public Feed fetchFeed(String feedUrl, String existingName) throws Exception {
        URL url = URI.create(feedUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);

        byte[] raw = conn.getInputStream().readAllBytes();
        String xml = new String(raw, StandardCharsets.UTF_8);
        List<String> rawPubDates = extractRawPubDates(xml);

        SyndFeed synd = new SyndFeedInput().build(
                new XmlReader(new ByteArrayInputStream(raw)));

        Feed feed = new Feed();
        feed.setUrl(feedUrl);
        feed.setName(existingName != null ? existingName : synd.getTitle());
        feed.setDescription(synd.getDescription() != null ? synd.getDescription() : "");
        feed.setLastUpdated(LocalDateTime.now());

        List<SyndEntry> entries = synd.getEntries();
        List<Article> articles = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            SyndEntry e = entries.get(i);
            Article a = new Article();
            a.setTitle(e.getTitle() != null ? e.getTitle().trim() : "(无标题)");
            a.setLink(e.getLink() != null ? e.getLink().trim() : "");
            a.setAuthor(e.getAuthor() != null ? e.getAuthor().trim() : "");

            if (e.getDescription() != null) a.setDescription(e.getDescription().getValue());
            List<SyndContent> contents = e.getContents();
            if (contents != null && !contents.isEmpty()) a.setContent(contents.get(0).getValue());
            if (a.getContent() == null) a.setContent(a.getDescription());

            a.setPubDate(toLDT(resolvePubDate(e, i, rawPubDates)));
            a.setFeedName(feed.getName());
            articles.add(a);
        }
        feed.setArticles(articles);
        return feed;
    }

    // ---- 日期解析 ----

    private Date resolvePubDate(SyndEntry e, int idx, List<String> raw) {
        Date d = e.getPublishedDate();
        if (d == null) d = e.getUpdatedDate();
        if (d == null) {
            DCModule dc = (DCModule) e.getModule(DCModule.URI);
            if (dc != null) d = dc.getDate();
        }
        if (d == null && idx < raw.size()) d = parseFlexibly(raw.get(idx));
        return d;
    }

    private List<String> extractRawPubDates(String xml) {
        List<String> list = new ArrayList<>();
        Matcher m = PUB_DATE_RE.matcher(xml);
        while (m.find()) list.add(m.group(1).trim());
        return list;
    }

    private Date parseFlexibly(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.replaceAll("\\s+", " ").trim();
        for (DateTimeFormatter fmt : FALLBACK_FMTS) {
            try { return Date.from(ZonedDateTime.parse(s, fmt).toInstant()); } catch (DateTimeParseException ignored) {}
            try { return Date.from(LocalDateTime.parse(s, fmt).atZone(ZoneId.systemDefault()).toInstant()); } catch (DateTimeParseException ignored) {}
        }
        System.err.println("[RSS] 无法解析日期: " + s);
        return null;
    }

    private static LocalDateTime toLDT(Date d) {
        if (d == null) return null;
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }

    public Optional<String> discoverFeedUrl(String websiteUrl) {
        return Optional.of(websiteUrl);
    }
}
