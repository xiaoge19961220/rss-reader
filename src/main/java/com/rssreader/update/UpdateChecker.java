package com.rssreader.update;

import com.rssreader.AppConfig;

import javax.swing.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Release 版本检测与自动升级。
 */
public class UpdateChecker {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern TAG_RE = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_RE = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSETS_RE = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern DL_URL_RE = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");

    /** 后台检查更新 */
    public static void checkAsync(JFrame parent, Callback cb) {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                try {
                    return checkSync();
                } catch (Exception e) {
                    System.err.println("[更新检查] 异常: " + e.getMessage());
                    return null;
                }
            }
            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) {
                        System.out.println("[更新检查] 发现新版本: " + info.version());
                        cb.onUpdateAvailable(info);
                    } else {
                        System.out.println("[更新检查] 当前已是最新版本");
                    }
                } catch (Exception e) {
                    System.err.println("[更新检查] 失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    /** 同步检查 */
    private static UpdateInfo checkSync() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AppConfig.getUpdateApiUrl()))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RSS-Reader-UpdateChecker/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("[更新检查] API 响应: HTTP " + resp.statusCode());

        if (resp.statusCode() == 403 || resp.statusCode() == 429) {
            System.err.println("[更新检查] GitHub API 限流，请稍后再试");
            return null;
        }
        if (resp.statusCode() != 200) return null;

        String json = resp.body();
        String tag = extract(TAG_RE, json);
        if (tag == null) {
            System.err.println("[更新检查] 无法解析 tag_name");
            return null;
        }
        String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;

        if (!isNewer(latestVersion, AppConfig.VERSION)) {
            System.out.println("[更新检查] 最新版 " + latestVersion + " <= 当前版 " + AppConfig.VERSION);
            return null;
        }

        String name = extract(NAME_RE, json);
        String body = extractBody(json);
        String downloadUrl = extractDownloadUrl(json);

        if (downloadUrl == null) {
            System.err.println("[更新检查] 找不到下载链接");
            return null;
        }
        return new UpdateInfo(latestVersion,
                name != null ? name : latestVersion,
                body != null ? body : "",
                downloadUrl);
    }

    /** 下载新版本 JAR 并替换 */
    public static Path downloadAndReplace(UpdateInfo info, JFrame parent) throws Exception {
        Path currentJar = Path.of(UpdateChecker.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path newJar = currentJar.resolveSibling("rss-reader-" + info.version + ".jar.tmp");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(info.downloadUrl))
                .timeout(Duration.ofMinutes(5))
                .GET().build();
        HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(newJar));
        if (resp.statusCode() != 200) throw new RuntimeException("下载失败: HTTP " + resp.statusCode());

        Path finalJar = currentJar.resolveSibling("rss-reader-" + info.version + ".jar");
        Files.move(newJar, finalJar, StandardCopyOption.REPLACE_EXISTING);
        return finalJar;
    }

    // ---- 工具 ----

    private static String extract(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** 提取 body 字段 — 容错处理 */
    private static String extractBody(String json) {
        try {
            // 找到 "body": 的位置，然后逐字符解析转义
            int start = json.indexOf("\"body\"");
            if (start < 0) return "";
            int colon = json.indexOf(':', start);
            if (colon < 0) return "";
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) return "";

            StringBuilder sb = new StringBuilder();
            int i = quote + 1;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> { sb.append('\\'); sb.append(next); }
                    }
                    i += 2;
                } else if (c == '"') {
                    break;  // 字符串结束
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 assets 数组中找到 .jar 下载链接 */
    private static String extractDownloadUrl(String json) {
        Matcher am = ASSETS_RE.matcher(json);
        if (!am.find()) return null;
        String assets = am.group(1);
        // 优先 jar，其次 zip
        Matcher jm = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.jar)\"").matcher(assets);
        if (jm.find()) return jm.group(1);
        Matcher zm = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.zip)\"").matcher(assets);
        if (zm.find()) return zm.group(1);
        Matcher dm = DL_URL_RE.matcher(assets);
        return dm.find() ? dm.group(1) : null;
    }

    /** 语义版本比较 */
    static boolean isNewer(String latest, String current) {
        try {
            int[] l = parseVersion(latest);
            int[] c = parseVersion(current);
            for (int i = 0; i < Math.min(l.length, c.length); i++) {
                if (l[i] > c[i]) return true;
                if (l[i] < c[i]) return false;
            }
            return l.length > c.length;
        } catch (Exception e) {
            return !latest.equals(current);
        }
    }

    private static int[] parseVersion(String v) {
        String[] parts = v.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) nums[i] = Integer.parseInt(parts[i]);
        return nums;
    }

    // ---- 类型 ----

    public record UpdateInfo(String version, String name, String body, String downloadUrl) {}

    @FunctionalInterface
    public interface Callback {
        void onUpdateAvailable(UpdateInfo info);
    }
}
