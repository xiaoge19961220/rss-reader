package com.rssreader.util;

import com.rssreader.model.Feed;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of feed subscriptions to a file.
 * Uses a simple pipe-delimited format: name|url
 */
public class FeedStorage {

    private static final String STORAGE_DIR = System.getProperty("user.home") + "/.rss-reader";
    private static final String FEEDS_FILE = STORAGE_DIR + "/feeds.txt";

    /**
     * Ensures the storage directory exists.
     */
    public static void ensureStorageDirectory() throws IOException {
        Path dir = Paths.get(STORAGE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    /**
     * Saves a list of feeds to the storage file.
     */
    public static void saveFeeds(List<Feed> feeds) throws IOException {
        ensureStorageDirectory();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(FEEDS_FILE), StandardCharsets.UTF_8))) {
            for (Feed feed : feeds) {
                String name = feed.getName() != null ? feed.getName().replace("|", "\\|") : "";
                String url = feed.getUrl() != null ? feed.getUrl().replace("|", "\\|") : "";
                writer.write(name + "|" + url);
                writer.newLine();
            }
        }
    }

    /**
     * Loads feeds from the storage file.
     * Returns an empty list if no feeds file exists.
     */
    public static List<Feed> loadFeeds() throws IOException {
        List<Feed> feeds = new ArrayList<>();
        Path path = Paths.get(FEEDS_FILE);

        if (!Files.exists(path)) {
            return feeds;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(FEEDS_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Handle escaped pipe characters
                String[] parts = line.split("(?<!\\\\)\\|");
                if (parts.length >= 2) {
                    String name = parts[0].replace("\\|", "|").trim();
                    String url = parts[1].replace("\\|", "|").trim();
                    if (!url.isEmpty()) {
                        if (name.isEmpty()) {
                            name = url;
                        }
                        feeds.add(new Feed(name, url));
                    }
                }
            }
        }
        return feeds;
    }

    /**
     * Returns the path to the feeds storage file.
     */
    public static String getFeedsFilePath() {
        return FEEDS_FILE;
    }
}
