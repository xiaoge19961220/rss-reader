package com.rssreader.ui;

import com.rssreader.model.Article;
import com.rssreader.model.Feed;
import com.rssreader.service.FeedService;
import com.rssreader.util.FeedStorage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * RSS 阅读器主窗口。
 * 包含订阅列表、文章表格和内容阅读面板。
 */
public class MainFrame extends JFrame {

    // Services
    private final FeedService feedService;

    // Data
    private final List<Feed> feeds;
    private final DefaultListModel<Feed> feedListModel;

    // UI Components
    private JList<Feed> feedList;
    private JTable articleTable;
    private ArticleTableModel articleTableModel;
    private JEditorPane contentPane;
    private JLabel statusLabel;
    private JButton addButton;
    private JButton removeButton;
    private JButton refreshAllButton;
    private JButton refreshFeedButton;
    private JLabel unreadLabel;

    // Currently selected feed and article
    private Feed selectedFeed;
    private Article selectedArticle;

    public MainFrame() {
        feedService = new FeedService();
        feeds = new ArrayList<>();
        feedListModel = new DefaultListModel<>();

        initUI();
        loadFeeds();
        refreshAllFeeds();

        setTitle("RSS 阅读器");
        setSize(1100, 750);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    // ==================== UI Initialization ====================

    private void initUI() {
        setJMenuBar(createMenuBar());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createToolBar(), BorderLayout.NORTH);

        // 主分割面板: 订阅列表 | 文章+内容
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setLeftComponent(createFeedPanel());
        mainSplitPane.setRightComponent(createArticlePanel());
        mainSplitPane.setDividerLocation(260);
        mainSplitPane.setDividerSize(1);
        mainSplitPane.setBorder(null);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);

        // Status bar
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem addFeedItem = new JMenuItem("添加订阅...", KeyEvent.VK_A);
        addFeedItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        addFeedItem.addActionListener(e -> showAddFeedDialog());
        fileMenu.add(addFeedItem);

        JMenuItem importOpmlItem = new JMenuItem("导入 OPML...", KeyEvent.VK_I);
        importOpmlItem.addActionListener(e -> importOpml());
        fileMenu.add(importOpmlItem);

        JMenuItem exportOpmlItem = new JMenuItem("导出 OPML...", KeyEvent.VK_E);
        exportOpmlItem.addActionListener(e -> exportOpml());
        fileMenu.add(exportOpmlItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出", KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        // 订阅菜单
        JMenu feedsMenu = new JMenu("订阅");
        feedsMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem refreshAllItem = new JMenuItem("刷新全部", KeyEvent.VK_R);
        refreshAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refreshAllItem.addActionListener(e -> refreshAllFeeds());
        feedsMenu.add(refreshAllItem);

        JMenuItem refreshFeedItem = new JMenuItem("刷新选中订阅", KeyEvent.VK_S);
        refreshFeedItem.addActionListener(e -> refreshSelectedFeed());
        feedsMenu.add(refreshFeedItem);

        feedsMenu.addSeparator();

        JMenuItem markAllReadItem = new JMenuItem("全部标记已读", KeyEvent.VK_M);
        markAllReadItem.addActionListener(e -> markAllAsRead());
        feedsMenu.add(markAllReadItem);

        menuBar.add(feedsMenu);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("关于 RSS Reader", KeyEvent.VK_A);
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        return menuBar;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(new EmptyBorder(6, 8, 6, 8));
        toolBar.setBackground(new Color(250, 251, 252));

        addButton = new JButton("＋ 添加订阅");
        addButton.setToolTipText("添加新的 RSS 订阅源");
        addButton.addActionListener(e -> showAddFeedDialog());
        styleToolbarButton(addButton);
        toolBar.add(addButton);

        removeButton = new JButton("✕ 删除");
        removeButton.setToolTipText("删除选中的订阅");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeSelectedFeed());
        styleToolbarButton(removeButton);
        toolBar.add(removeButton);

        toolBar.addSeparator();

        refreshAllButton = new JButton("↻ 刷新全部");
        refreshAllButton.setToolTipText("刷新所有订阅");
        refreshAllButton.addActionListener(e -> refreshAllFeeds());
        styleToolbarButton(refreshAllButton);
        toolBar.add(refreshAllButton);

        refreshFeedButton = new JButton("↻ 刷新订阅");
        refreshFeedButton.setToolTipText("刷新选中的订阅");
        refreshFeedButton.setEnabled(false);
        refreshFeedButton.addActionListener(e -> refreshSelectedFeed());
        styleToolbarButton(refreshFeedButton);
        toolBar.add(refreshFeedButton);

        toolBar.addSeparator();

        unreadLabel = new JLabel("  暂无订阅  ");
        unreadLabel.setForeground(new Color(140, 140, 145));
        unreadLabel.setFont(unreadLabel.getFont().deriveFont(Font.PLAIN, 12));
        toolBar.add(unreadLabel);

        return toolBar;
    }

    private void styleToolbarButton(JButton button) {
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 215), 1),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        button.setBackground(Color.WHITE);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private JPanel createFeedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 224)),
                "订阅列表", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("PingFang SC", Font.BOLD, 13), new Color(60, 60, 65)));

        feedList = new JList<>(feedListModel);
        feedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        feedList.setFixedCellHeight(36);
        feedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBorder(new EmptyBorder(6, 12, 6, 12));
                if (value instanceof Feed feed) {
                    String unreadBadge = feed.getUnreadCount() > 0
                            ? "  <span style='background:#3b82f6;color:#fff;padding:1px 7px;border-radius:10px;font-size:11px;'>" + feed.getUnreadCount() + "</span>"
                            : "";
                    setText("<html><div style='padding:2px 0;'>" + escapeHtml(feed.getName()) + unreadBadge + "</div></html>");
                    if (feed.getUnreadCount() > 0 && !isSelected) {
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                    setIcon(null);
                }
                return this;
            }
        });

        feedList.addListSelectionListener(this::onFeedSelectionChanged);
        feedList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = feedList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        refreshFeed(feeds.get(index));
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(feedList);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createArticlePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 文章表格
        articleTableModel = new ArticleTableModel();
        articleTable = new JTable(articleTableModel);
        articleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        articleTable.setAutoCreateRowSorter(true);
        articleTable.getTableHeader().setReorderingAllowed(false);
        articleTable.setRowHeight(28);
        articleTable.setShowGrid(false);
        articleTable.setIntercellSpacing(new Dimension(0, 0));
        articleTable.setFillsViewportHeight(true);

        // Set column widths
        TableColumnModel columnModel = articleTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(500); // Title
        columnModel.getColumn(1).setPreferredWidth(140); // Date
        columnModel.getColumn(2).setPreferredWidth(150); // Feed

        articleTable.getSelectionModel().addListSelectionListener(this::onArticleSelectionChanged);

        // Double-click to open in browser
        articleTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedArticleInBrowser();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(articleTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 224)),
                "文章列表", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("PingFang SC", Font.BOLD, 13), new Color(60, 60, 65)));

        // 文章内容面板
        contentPane = new JEditorPane();
        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        // 欢迎页 HTML
        String welcomeHtml = """
                <html>
                <head><style>
                body { font-family: "PingFang SC", -apple-system, sans-serif; margin: 0;
                       background: linear-gradient(135deg, #f8fafc 0%, #e8ecf1 100%); }
                .welcome-card { max-width: 420px; margin: 80px auto; padding: 40px;
                       background: white; border-radius: 16px; box-shadow: 0 4px 24px rgba(0,0,0,0.06);
                       text-align: center; }
                .welcome-icon { font-size: 48px; margin-bottom: 16px; }
                .welcome-title { font-size: 20px; color: #1a1a2e; font-weight: 600; margin: 0 0 8px 0; }
                .welcome-sub { font-size: 13px; color: #888; line-height: 1.8; margin: 0; }
                .welcome-hint { margin-top: 24px; padding: 14px 18px; background: #f0f4ff;
                       border-radius: 10px; font-size: 12px; color: #5b7cfa; }
                .welcome-hint b { color: #3b5fd9; }
                </style></head>
                <body>
                <div class="welcome-card">
                <div class="welcome-icon">📰</div>
                <h2 class="welcome-title">RSS 阅读器</h2>
                <p class="welcome-sub">选择左侧订阅源查看文章<br>点击文章即可阅读正文内容</p>
                <div class="welcome-hint">
                按 <b>⌘N</b> 或点击 <b>＋ 添加订阅</b> 来添加你的第一个订阅源
                </div>
                </div>
                </body>
                </html>
                """;
        contentPane.setText(welcomeHtml);

        JScrollPane contentScrollPane = new JScrollPane(contentPane);
        contentScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 224)),
                "文章内容", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("PingFang SC", Font.BOLD, 13), new Color(60, 60, 65)));

        // 分割面板: 文章列表 | 文章内容
        JSplitPane rightSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplitPane.setTopComponent(tableScrollPane);
        rightSplitPane.setBottomComponent(contentScrollPane);
        rightSplitPane.setDividerLocation(360);
        rightSplitPane.setDividerSize(1);
        rightSplitPane.setBorder(null);

        panel.add(rightSplitPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 224)));
        statusBar.setBackground(new Color(248, 249, 250));

        statusLabel = new JLabel(" 就绪");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12));
        statusLabel.setForeground(new Color(120, 120, 125));
        statusLabel.setBorder(new EmptyBorder(3, 8, 3, 5));
        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    // ==================== Feed Management ====================

    private void loadFeeds() {
        try {
            List<Feed> savedFeeds = FeedStorage.loadFeeds();
            feeds.clear();
            feedListModel.clear();
            for (Feed feed : savedFeeds) {
                feeds.add(feed);
                feedListModel.addElement(feed);
            }
            if (!feeds.isEmpty()) {
                setStatus("已加载 " + feeds.size() + " 个订阅");
                updateUnreadLabel();
            }
        } catch (IOException e) {
            setStatus("加载订阅失败: " + e.getMessage());
        }
    }

    private void saveFeeds() {
        try {
            FeedStorage.saveFeeds(feeds);
        } catch (IOException e) {
            setStatus("保存订阅失败: " + e.getMessage());
        }
    }

    private void showAddFeedDialog() {
        String[] result = AddFeedDialog.showDialog(this);
        if (result != null) {
            String name = result[0];
            String url = result[1];

            // Check for duplicate
            for (Feed existing : feeds) {
                if (existing.getUrl().equals(url)) {
                    JOptionPane.showMessageDialog(this,
                            "该订阅源已经存在:\n" + existing.getName(),
                            "重复订阅", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            Feed newFeed = new Feed(name, url);
            feeds.add(newFeed);
            feedListModel.addElement(newFeed);
            saveFeeds();
            updateUnreadLabel();

            // Select and refresh the new feed
            feedList.setSelectedValue(newFeed, true);
            refreshFeed(newFeed);
        }
    }

    private void removeSelectedFeed() {
        int selectedIndex = feedList.getSelectedIndex();
        if (selectedIndex < 0) {
            return;
        }

        Feed feed = feeds.get(selectedIndex);
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除订阅 \"" + feed.getName() + "\" 吗?",
                "删除订阅",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            feeds.remove(selectedIndex);
            feedListModel.remove(selectedIndex);
            articleTableModel.clear();
            selectedFeed = null;
            saveFeeds();
            updateUnreadLabel();
            setStatus("已删除订阅: " + feed.getName());
        }
    }

    // ==================== Feed Refresh ====================

    private void refreshAllFeeds() {
        if (feeds.isEmpty()) {
            setStatus("暂无订阅可刷新，请先添加订阅!");
            return;
        }

        refreshAllButton.setEnabled(false);
        setStatus("正在刷新全部订阅...");

        SwingWorker<List<Feed>, Feed> worker = new SwingWorker<>() {
            private int fetched = 0;

            @Override
            protected List<Feed> doInBackground() {
                List<Feed> updatedFeeds = new ArrayList<>();
                for (Feed feed : feeds) {
                    try {
                        Feed updated = feedService.fetchFeed(feed.getUrl(), feed.getName());
                        updatedFeeds.add(updated);
                        publish(updated);
                    } catch (Exception e) {
                        Feed errorFeed = new Feed(feed.getName(), feed.getUrl());
                        errorFeed.setDescription("Error: " + e.getMessage());
                        updatedFeeds.add(errorFeed);
                    }
                    fetched++;
                    setProgress((int) ((fetched / (double) feeds.size()) * 100));
                }
                return updatedFeeds;
            }

            @Override
            protected void process(List<Feed> chunks) {
                for (Feed updated : chunks) {
                    // Update the feeds list
                    for (int i = 0; i < feeds.size(); i++) {
                        if (feeds.get(i).getUrl().equals(updated.getUrl())) {
                            feeds.get(i).setArticles(updated.getArticles());
                            feeds.get(i).setDescription(updated.getDescription());
                            feeds.get(i).setLastUpdated(updated.getLastUpdated());
                            feeds.get(i).setUnreadCount(updated.getArticleCount());
                            break;
                        }
                    }
                    feedList.repaint();
                    updateUnreadLabel();
                }
            }

            @Override
            protected void done() {
                refreshAllButton.setEnabled(true);
                try {
                    List<Feed> result = get();
                    int totalArticles = result.stream().mapToInt(Feed::getArticleCount).sum();
                    setStatus("刷新完成: " + result.size() + " 个订阅, 共 " + totalArticles + " 篇文章");

                    // Refresh the currently displayed articles if a feed is selected
                    if (selectedFeed != null) {
                        for (Feed f : feeds) {
                            if (f.getUrl().equals(selectedFeed.getUrl())) {
                                selectedFeed = f;
                                articleTableModel.setArticles(f.getArticles());
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    setStatus("刷新订阅出错: " + e.getMessage());
                }
                updateUnreadLabel();
            }
        };
        worker.execute();
    }

    private void refreshSelectedFeed() {
        Feed feed = feedList.getSelectedValue();
        if (feed != null) {
            refreshFeed(feed);
        }
    }

    private void refreshFeed(Feed feed) {
        refreshFeedButton.setEnabled(false);
        refreshAllButton.setEnabled(false);
        setStatus("正在刷新: " + feed.getName() + "...");

        SwingWorker<Feed, Void> worker = new SwingWorker<>() {
            @Override
            protected Feed doInBackground() throws Exception {
                return feedService.fetchFeed(feed.getUrl(), feed.getName());
            }

            @Override
            protected void done() {
                refreshFeedButton.setEnabled(true);
                refreshAllButton.setEnabled(true);
                try {
                    Feed updated = get();
                    // Update the feed in the list
                    for (int i = 0; i < feeds.size(); i++) {
                        if (feeds.get(i).getUrl().equals(updated.getUrl())) {
                            feeds.get(i).setArticles(updated.getArticles());
                            feeds.get(i).setDescription(updated.getDescription());
                            feeds.get(i).setLastUpdated(updated.getLastUpdated());
                            feeds.get(i).setUnreadCount(updated.getArticleCount());
                            break;
                        }
                    }

                    // Update the table if this feed is currently selected
                    if (selectedFeed != null && selectedFeed.getUrl().equals(updated.getUrl())) {
                        selectedFeed.setArticles(updated.getArticles());
                        articleTableModel.setArticles(updated.getArticles());
                    }

                    feedList.repaint();
                    updateUnreadLabel();
                    setStatus("刷新完成: " + feed.getName() + " - " + updated.getArticleCount() + " 篇文章");
                } catch (InterruptedException | ExecutionException e) {
                    setStatus("刷新订阅出错: " + e.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "无法获取订阅:\n" + feed.getUrl() + "\n\n" + e.getCause().getMessage(),
                            "订阅错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ==================== Event Handlers ====================

    private void onFeedSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }

        int selectedIndex = feedList.getSelectedIndex();
        boolean hasSelection = selectedIndex >= 0;

        removeButton.setEnabled(hasSelection);
        refreshFeedButton.setEnabled(hasSelection);

        if (hasSelection) {
            selectedFeed = feeds.get(selectedIndex);
            articleTableModel.setArticles(selectedFeed.getArticles());
            setStatus("订阅: " + selectedFeed.getName() + " (" + selectedFeed.getArticleCount() + " 篇文章)");

            // 清空内容面板提示
            contentPane.setText("<html><body style='font-family:\"PingFang SC\",sans-serif;padding:30px;text-align:center;color:#999;'><p style='margin-top:60px;'>← 选择一篇文章来阅读</p></body></html>");
        } else {
            selectedFeed = null;
            articleTableModel.clear();
        }
    }

    private void onArticleSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }

        int selectedRow = articleTable.getSelectedRow();
        if (selectedRow >= 0) {
            // Convert view index to model index (accounts for sorting)
            int modelRow = articleTable.convertRowIndexToModel(selectedRow);
            selectedArticle = articleTableModel.getArticleAt(modelRow);

            if (selectedArticle != null) {
                selectedArticle.markAsRead();
                displayArticleContent(selectedArticle);
                updateUnreadLabel();
            }
        }
    }

    // ==================== Content Display ====================

    private void displayArticleContent(Article article) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: \"PingFang SC\", -apple-system, \"Segoe UI\", sans-serif; ");
        html.append("  padding: 28px 32px; line-height: 1.8; color: #2c2c2e; max-width: 780px; ");
        html.append("  margin: 0 auto; background: #fff; }");
        html.append("h1 { font-size: 22px; color: #1a1a2e; margin-bottom: 6px; font-weight: 700; ");
        html.append("  letter-spacing: -0.3px; }");
        html.append("h2 { font-size: 17px; color: #333; margin: 24px 0 10px; }");
        html.append("h3 { font-size: 15px; color: #444; margin: 18px 0 8px; }");
        html.append(".meta { display: flex; flex-wrap: wrap; gap: 6px 18px; color: #999; font-size: 12px; ");
        html.append("  margin-bottom: 24px; padding-bottom: 16px; ");
        html.append("  border-bottom: 1px solid #f0f0f0; }");
        html.append(".meta-item { display: inline-flex; align-items: center; gap: 4px; }");
        html.append(".meta-label { color: #bbb; }");
        html.append("a { color: #3b5fd9; text-decoration: none; }");
        html.append("a:hover { text-decoration: underline; }");
        html.append("img, video { max-width: 100%; height: auto; border-radius: 8px; margin: 12px 0; }");
        html.append("pre { background: #f6f8fa; padding: 16px; border-radius: 8px; overflow-x: auto; ");
        html.append("  font-size: 13px; line-height: 1.5; border: 1px solid #e8eaed; }");
        html.append("code { background: #f1f3f5; padding: 2px 6px; border-radius: 4px; font-size: 13px; }");
        html.append("pre code { background: none; padding: 0; }");
        html.append("blockquote { border-left: 3px solid #667eea; margin: 16px 0; padding: 10px 18px; ");
        html.append("  background: #f8f9ff; color: #555; border-radius: 0 8px 8px 0; }");
        html.append("p { margin: 10px 0; }");
        html.append("ul, ol { padding-left: 24px; }");
        html.append("li { margin: 4px 0; }");
        html.append(".origin-link { display: inline-block; margin-top: 28px; padding: 10px 20px; ");
        html.append("  background: #f0f4ff; border-radius: 10px; font-size: 13px; color: #3b5fd9; ");
        html.append("  text-decoration: none; font-weight: 500; }");
        html.append(".origin-link:hover { background: #e0e8ff; }");
        html.append("</style></head><body>");

        html.append("<h1>").append(escapeHtml(article.getTitle())).append("</h1>");

        html.append("<div class='meta'>");
        if (article.getFeedName() != null && !article.getFeedName().isEmpty()) {
            html.append("<span class='meta-item'><span class='meta-label'>订阅源</span> ")
                    .append(escapeHtml(article.getFeedName())).append("</span>");
        }
        if (article.getAuthor() != null && !article.getAuthor().isEmpty()) {
            html.append("<span class='meta-item'><span class='meta-label'>作者</span> ")
                    .append(escapeHtml(article.getAuthor())).append("</span>");
        }
        if (article.getPubDate() != null) {
            html.append("<span class='meta-item'><span class='meta-label'>发布</span> ")
                    .append(article.getPubDate().toString()).append("</span>");
        }
        html.append("</div>");

        // 文章内容
        String content = article.getContent();
        if (content != null && !content.isBlank()) {
            html.append(content);
        } else if (article.getDescription() != null && !article.getDescription().isBlank()) {
            html.append(article.getDescription());
        } else {
            html.append("<p><em>暂无内容</em></p>");
        }

        // 原文链接
        if (article.getLink() != null && !article.getLink().isEmpty()) {
            html.append("<a class='origin-link' href='").append(article.getLink())
                    .append("'>📎 阅读原文 →</a>");
        }

        html.append("</body></html>");

        contentPane.setText(html.toString());
        contentPane.setCaretPosition(0);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ==================== Actions ====================

    private void openSelectedArticleInBrowser() {
        if (selectedArticle == null) {
            return;
        }

        String link = selectedArticle.getLink();
        if (link == null || link.isEmpty()) {
            setStatus("该文章没有可用的链接");
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(link));
                setStatus("已在浏览器中打开: " + link);
            } else {
                setStatus("当前平台不支持打开浏览器");
            }
        } catch (Exception e) {
            setStatus("无法打开浏览器: " + e.getMessage());
        }
    }

    private void markAllAsRead() {
        for (Feed feed : feeds) {
            for (Article article : feed.getArticles()) {
                article.setRead(true);
            }
            feed.setUnreadCount(0);
        }
        feedList.repaint();
        updateUnreadLabel();
        setStatus("已将所有文章标记为已读");
    }

    private void importOpml() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导入 OPML 文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "OPML 文件 (*.opml, *.xml)", "opml", "xml"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                List<Feed> importedFeeds = parseOpml(chooser.getSelectedFile().getAbsolutePath());
                for (Feed feed : importedFeeds) {
                    if (!feeds.contains(feed)) {
                        feeds.add(feed);
                        feedListModel.addElement(feed);
                    }
                }
                saveFeeds();
                updateUnreadLabel();
                setStatus("从 OPML 导入了 " + importedFeeds.size() + " 个订阅");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "导入 OPML 出错: " + e.getMessage(),
                        "导入错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private List<Feed> parseOpml(String filePath) throws Exception {
        List<Feed> result = new ArrayList<>();
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(filePath);

        org.w3c.dom.NodeList outlines = doc.getElementsByTagName("outline");
        for (int i = 0; i < outlines.getLength(); i++) {
            org.w3c.dom.Element outline = (org.w3c.dom.Element) outlines.item(i);
            String xmlUrl = outline.getAttribute("xmlUrl");
            String title = outline.getAttribute("title");
            if (xmlUrl != null && !xmlUrl.isEmpty()) {
                if (title == null || title.isEmpty()) {
                    title = outline.getAttribute("text");
                }
                if (title == null || title.isEmpty()) {
                    title = xmlUrl;
                }
                result.add(new Feed(title, xmlUrl));
            }
        }
        return result;
    }

    private void exportOpml() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出 OPML 文件");
        chooser.setSelectedFile(new java.io.File("rss-feeds.opml"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "OPML 文件 (*.opml)", "opml"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String path = chooser.getSelectedFile().getAbsolutePath();
                if (!path.endsWith(".opml")) {
                    path += ".opml";
                }
                writeOpml(path);
                setStatus("已导出 " + feeds.size() + " 个订阅到 OPML");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "导出 OPML 出错: " + e.getMessage(),
                        "导出错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void writeOpml(String filePath) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<opml version=\"2.0\">\n");
        xml.append("  <head>\n");
        xml.append("    <title>RSS Reader Subscriptions</title>\n");
        xml.append("  </head>\n");
        xml.append("  <body>\n");
        for (Feed feed : feeds) {
            xml.append("    <outline text=\"").append(escapeXml(feed.getName()))
                    .append("\" title=\"").append(escapeXml(feed.getName()))
                    .append("\" type=\"rss\" xmlUrl=\"").append(escapeXml(feed.getUrl())).append("\" />\n");
        }
        xml.append("  </body>\n");
        xml.append("</opml>\n");

        java.nio.file.Files.writeString(java.nio.file.Paths.get(filePath), xml.toString());
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                """
                        RSS 阅读器 v1.0

                        基于 Java Swing 的桌面 RSS 订阅阅读器。

                        • 添加和管理 RSS/Atom 订阅源
                        • HTML 渲染阅读文章正文
                        • 导入/导出 OPML 文件
                        • 在浏览器中打开原文

                        技术栈: Java 17 + Swing + Rome RSS 库
                        """,
                "关于 RSS 阅读器",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== Helpers ====================

    private void setStatus(String message) {
        statusLabel.setText(" " + message);
    }

    private void updateUnreadLabel() {
        int totalFeeds = feeds.size();
        int totalArticles = feeds.stream().mapToInt(Feed::getArticleCount).sum();
        long unreadFeeds = feeds.stream().filter(f -> f.getUnreadCount() > 0).count();

        if (totalFeeds == 0) {
            unreadLabel.setText("  暂无订阅  ");
        } else {
            unreadLabel.setText(String.format("  %d 个订阅 | %d 篇文章 | %d 个有未读  ",
                    totalFeeds, totalArticles, unreadFeeds));
        }
    }
}
