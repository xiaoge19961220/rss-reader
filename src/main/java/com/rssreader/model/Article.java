package com.rssreader.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a single article/entry from an RSS/Atom feed.
 */
public class Article {

    private String title;
    private String link;
    private String description;
    private String content;
    private String author;
    private LocalDateTime pubDate;
    private String feedName;
    private boolean read;

    public Article() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the best available content: full content if available,
     * otherwise falls back to description.
     */
    public String getContent() {
        if (content != null && !content.isBlank()) {
            return content;
        }
        return description;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public LocalDateTime getPubDate() {
        return pubDate;
    }

    public void setPubDate(LocalDateTime pubDate) {
        this.pubDate = pubDate;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(String feedName) {
        this.feedName = feedName;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public void markAsRead() {
        this.read = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Article article = (Article) o;
        return Objects.equals(link, article.link) && Objects.equals(title, article.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(link, title);
    }

    @Override
    public String toString() {
        return title;
    }
}
