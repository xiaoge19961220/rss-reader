# 📰 自由·RSS阅读器

基于 Java Swing 的桌面 RSS 订阅阅读器，支持 RSS 2.0 / Atom 格式，树形文章浏览，HTML 正文渲染。

## ✨ 功能

- 📡 **RSS/Atom 订阅** — 添加、删除、刷新订阅源
- 🌳 **树形浏览** — 订阅源 → 文章层级展示，一目了然
- 📖 **HTML 渲染** — 正文内嵌样式阅读，支持图片/代码块/引用
- 📂 **OPML 导入/导出** — 一键迁移订阅列表
- 🌐 **浏览器打开** — 双击文章或右键在浏览器中阅读原文
- 🔄 **自动更新** — 启动时检测 GitHub Release，一键升级到最新版
- 🎨 **中文界面** — 完整汉化，PingFang 字体，macOS 原生风格

## 📸 界面

```
┌──────────────────────────────────────────────────────┐
│  文件  订阅  帮助                                      │
├──────────────────────────────────────────────────────┤
│  [＋添加订阅] [✕删除] | [↻刷新全部] [↻刷新订阅]        │
├────────────────┬─────────────────────────────────────┤
│ 📰 36氪  25    │                                     │
│   • 清华、中科院团队联合华西...   │  文章标题            │
│   • AI 大模型最新进展...        │  ───────────────────  │
│ 📰 阮一峰  12  │  订阅源  作者  发布时间              │
│   • 科技爱好者周刊...           │  ───────────────────  │
│                │  正文内容 (HTML 渲染)                 │
│                │                                     │
├────────────────┴─────────────────────────────────────┤
│  5 个订阅 | 128 篇文章                                 │
└──────────────────────────────────────────────────────┘
```

## 📦 下载安装

### 方式一：内嵌 Java（推荐普通用户）

下载 `rss-reader-portable-{version}.zip`，解压后：

| 系统 | 操作 |
|------|------|
| macOS / Linux | 双击 `run.sh` 或终端 `./run.sh` |
| Windows | 双击 `run.bat` |

> 无需安装 Java，包内已含精简 JRE (~55MB)。

### 方式二：纯 JAR（需要 Java 17+）

下载 `rss-reader-{version}.jar`：

```bash
java -jar rss-reader-1.0.0.jar
```

## 🛠 开发

```bash
# 克隆
git clone https://github.com/YOUR_USER/rss-reader.git
cd rss-reader

# 编译
mvn clean package -DskipTests

# 运行
java -jar target/rss-reader-1.0.0.jar

# 双包构建（标准 JAR + 内嵌 JRE 包）
./build-all.sh
```

### 技术栈

| 层 | 技术 |
|----|------|
| UI | Java Swing (系统原生 Look & Feel) |
| RSS | Rome 2.1.0 |
| 构建 | Maven + Shade (Fat JAR) |
| CI/CD | GitHub Actions |
| 更新 | GitHub Releases API |
| 最低 JDK | 17 |

### 项目结构

```
src/main/java/com/rssreader/
├── RssReaderApp.java        # 入口
├── model/
│   ├── Feed.java            # 订阅源模型
│   └── Article.java         # 文章模型
├── service/
│   └── FeedService.java     # RSS 抓取/解析
├── ui/
│   ├── MainFrame.java       # 主窗口
│   ├── AddFeedDialog.java   # 添加订阅对话框
│   ├── ArticleTableModel.java
│   └── UpdateDialog.java    # 升级对话框
├── update/
│   └── UpdateChecker.java   # 版本检测/自动升级
└── util/
    └── FeedStorage.java     # 持久化
```

## 🔄 自动更新

应用启动时自动检查 GitHub Release 是否有新版本：

1. 后台请求 GitHub API 获取最新 release
2. 比对本地版本号
3. 有新版本 → 弹出更新对话框，显示更新内容
4. 用户确认 → 后台下载新 JAR，替换后提示重启

## 📄 协议

MIT License — 详见 [LICENSE](LICENSE)
