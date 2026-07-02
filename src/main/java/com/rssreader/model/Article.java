package com.rssreader.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * RSS/Atom 文章数据模型。
 */
public class Article {

    private String title;
    private String link;
    private String description;
    private String content;
    private String author;
    private LocalDateTime pubDate;
    private String feedName;

    public Article() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** 优先返回正文，无正文时回退到摘要。 */
    public String getContent() {
        return (content != null && !content.isBlank()) ? content : description;
    }
    public void setContent(String content) { this.content = content; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public LocalDateTime getPubDate() { return pubDate; }
    public void setPubDate(LocalDateTime pubDate) { this.pubDate = pubDate; }

    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Article a)) return false;
        return Objects.equals(link, a.link) && Objects.equals(title, a.title);
    }

    @Override
    public int hashCode() { return Objects.hash(link, title); }

    @Override
    public String toString() { return title; }
}
