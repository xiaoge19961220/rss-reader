package com.rssreader.service;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.rssreader.model.Article;
import com.rssreader.model.Feed;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Service for fetching and parsing RSS/Atom feeds using the Rome library.
 */
public class FeedService {

    private static final int TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "RSS-Reader/1.0";

    /**
     * Fetches and parses a feed from the given URL.
     *
     * @param feedUrl the URL of the RSS/Atom feed
     * @return Feed object populated with articles, or empty if the fetch fails
     * @throws Exception if the feed cannot be fetched or parsed
     */
    public Feed fetchFeed(String feedUrl) throws Exception {
        return fetchFeed(feedUrl, null);
    }

    /**
     * Fetches and parses a feed, preserving the existing feed name if provided.
     */
    public Feed fetchFeed(String feedUrl, String existingName) throws Exception {
        URL url = URI.create(feedUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);

        SyndFeedInput input = new SyndFeedInput();
        SyndFeed syndFeed = input.build(new XmlReader(connection));

        Feed feed = new Feed();
        feed.setUrl(feedUrl);
        feed.setName(existingName != null ? existingName : syndFeed.getTitle());
        feed.setDescription(syndFeed.getDescription() != null ? syndFeed.getDescription() : "");
        feed.setLastUpdated(LocalDateTime.now());

        List<Article> articles = new ArrayList<>();
        for (SyndEntry entry : syndFeed.getEntries()) {
            Article article = new Article();
            article.setTitle(entry.getTitle() != null ? entry.getTitle().trim() : "(No title)");
            article.setLink(entry.getLink() != null ? entry.getLink().trim() : "");
            article.setAuthor(entry.getAuthor() != null ? entry.getAuthor().trim() : "");

            // Extract description/content
            if (entry.getDescription() != null) {
                article.setDescription(entry.getDescription().getValue());
            }

            // Get full content if available
            List<SyndContent> contents = entry.getContents();
            if (contents != null && !contents.isEmpty()) {
                article.setContent(contents.get(0).getValue());
            }

            // Try to get content from foreign markup if not already set
            if (article.getContent() == null && entry.getForeignMarkup() != null) {
                // Use description as content if no dedicated content available
                article.setContent(article.getDescription());
            }

            // Parse publication date
            Date pubDate = entry.getPublishedDate();
            if (pubDate == null) {
                pubDate = entry.getUpdatedDate();
            }
            if (pubDate != null) {
                Instant instant = pubDate.toInstant();
                article.setPubDate(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
            }

            article.setFeedName(feed.getName());
            articles.add(article);
        }

        feed.setArticles(articles);
        return feed;
    }

    /**
     * Try to discover a feed URL from a website URL.
     * Returns the original URL if no feed is auto-discovered.
     */
    public Optional<String> discoverFeedUrl(String websiteUrl) {
        // For now, return the URL as-is. Rome can auto-detect feed types.
        // A more sophisticated implementation could parse HTML for <link> tags.
        return Optional.of(websiteUrl);
    }
}
