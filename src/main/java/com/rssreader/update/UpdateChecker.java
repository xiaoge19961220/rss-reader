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

    // 从 JSON 中提取字段（不引入 JSON 库）
    private static final Pattern TAG_RE   = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME_RE  = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BODY_RE  = Pattern.compile("\"body\"\\s*:\\s*\"([^\"]*?)\"");
    private static final Pattern URL_RE   = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ASSETS_RE = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);

    /**
     * 后台检查更新，有新版时回调。
     */
    public static void checkAsync(JFrame parent, Callback cb) {
        new SwingWorker<UpdateInfo, Void>() {
            @Override
            protected UpdateInfo doInBackground() {
                return checkSync();
            }
            @Override
            protected void done() {
                try {
                    UpdateInfo info = get();
                    if (info != null) cb.onUpdateAvailable(info);
                } catch (Exception ignored) {
                    // 网络错误等，静默忽略
                }
            }
        }.execute();
    }

    /**
     * 同步检查 GitHub Release。
     * @return null 表示无更新或检查失败
     */
    private static UpdateInfo checkSync() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AppConfig.getUpdateApiUrl()))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            String json = resp.body();
            String tag = extract(TAG_RE, json);
            if (tag == null) return null;
            String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;

            // 比较版本
            if (!isNewer(latestVersion, AppConfig.VERSION)) return null;

            String name = extract(NAME_RE, json);
            String body = extractBody(json);
            String downloadUrl = extractDownloadUrl(json);

            if (downloadUrl == null) return null;
            return new UpdateInfo(latestVersion, name != null ? name : latestVersion,
                    body != null ? body : "", downloadUrl);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 下载并替换 JAR，返回新文件路径。
     */
    public static Path downloadAndReplace(UpdateInfo info, JFrame parent) throws Exception {
        Path currentJar = Path.of(UpdateChecker.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path newJar = currentJar.resolveSibling("rss-reader-" + info.version + ".jar.tmp");

        // 下载
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(info.downloadUrl))
                .timeout(Duration.ofMinutes(5))
                .GET().build();
        HttpResponse<Path> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofFile(newJar));
        if (resp.statusCode() != 200) throw new RuntimeException("下载失败: HTTP " + resp.statusCode());

        // 替换
        Path finalJar = currentJar.resolveSibling("rss-reader-" + info.version + ".jar");
        Files.move(newJar, finalJar, StandardCopyOption.REPLACE_EXISTING);
        return finalJar;
    }

    // ---- 工具 ----

    private static String extract(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** 提取 release body（处理转义） */
    private static String extractBody(String json) {
        // 查找 "body": "..." 字段，直到下一个顶级字段
        Matcher m = Pattern.compile("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL)
                .matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return "";
    }

    /** 从 assets 中找到 .jar 下载链接 */
    private static String extractDownloadUrl(String json) {
        Matcher am = ASSETS_RE.matcher(json);
        if (!am.find()) return null;
        String assets = am.group(1);
        // 优先 jar，其次 zip
        Matcher um = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.jar)\"").matcher(assets);
        if (um.find()) return um.group(1);
        um = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*\\.zip)\"").matcher(assets);
        if (um.find()) return um.group(1);
        um = URL_RE.matcher(assets);
        return um.find() ? um.group(1) : null;
    }

    /** 简单语义版本比较 */
    private static boolean isNewer(String latest, String current) {
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
