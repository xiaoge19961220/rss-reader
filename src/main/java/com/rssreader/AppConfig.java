package com.rssreader;

/**
 * 应用全局配置。
 * 发布前请修改 GITHUB_OWNER 和 GITHUB_REPO。
 */
public final class AppConfig {

    private AppConfig() {}

    /** 当前版本号，与 git tag 保持一致 (不带 v 前缀) */
    public static final String VERSION = "1.0.2";

    /** GitHub 仓库所有者 */
    public static final String GITHUB_OWNER = "xiaoge1996122";

    /** GitHub 仓库名 */
    public static final String GITHUB_REPO = "rss-reader";

    /** 应用名称 */
    public static final String APP_NAME = "自由·RSS阅读器";

    /** GitHub Releases API URL */
    public static String getUpdateApiUrl() {
        return "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    }
}
