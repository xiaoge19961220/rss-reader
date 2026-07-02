package com.rssreader.ui;

import com.rssreader.model.Article;
import com.rssreader.model.Feed;
import com.rssreader.service.FeedService;
import com.rssreader.util.FeedStorage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * RSS 阅读器主窗口 — 树形订阅 + 内容阅读面板。
 */
public class MainFrame extends JFrame {

    private static final int MAX_TITLE_LEN = 30;

    // 共享静态不可变对象，减少分配
    private static final Color COLOR_BORDER   = new Color(220, 220, 224);
    private static final Color COLOR_TOOLBAR  = new Color(250, 251, 252);
    private static final Color COLOR_BTN_BG   = Color.WHITE;
    private static final Color COLOR_BTN_BDR  = new Color(210, 210, 215);
    private static final Color COLOR_TITLE_FG = new Color(60, 60, 65);
    private static final Color COLOR_STATUSBAR = new Color(248, 249, 250);
    private static final Color COLOR_STATUS   = new Color(120, 120, 125);
    private static final Color COLOR_UNREAD   = new Color(140, 140, 145);
    private static final Font  FONT_TITLE     = new Font("PingFang SC", Font.BOLD, 13);
    private static final Font  FONT_TREE      = new Font("PingFang SC", Font.PLAIN, 13);

    private final FeedService feedService;

    private final List<Feed> feeds = new ArrayList<>();
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private JTree feedTree;
    private JEditorPane contentPane;
    private JLabel statusLabel;
    private JButton removeButton;
    private JButton refreshAllButton;
    private JButton refreshFeedButton;
    private JLabel statsLabel;

    private Feed selectedFeed;
    private Article selectedArticle;

    public MainFrame() {
        feedService = new FeedService();
        rootNode = new DefaultMutableTreeNode("root");
        initUI();
        loadFeeds();
        refreshAllFeeds();

        setTitle("自由·RSS阅读器");
        setSize(1100, 750);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    // ==================== UI ====================

    private void initUI() {
        setJMenuBar(createMenuBar());
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createToolBar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setLeftComponent(createTreePanel());
        split.setRightComponent(createContentPanel());
        split.setDividerLocation(300);
        split.setDividerSize(1);
        split.setBorder(null);
        mainPanel.add(split, BorderLayout.CENTER);
        mainPanel.add(createStatusBar(), BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("文件"); file.setMnemonic(KeyEvent.VK_F);
        JMenuItem add = new JMenuItem("添加订阅...", KeyEvent.VK_A);
        add.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        add.addActionListener(e -> showAddFeedDialog());
        file.add(add);
        JMenuItem imp = new JMenuItem("导入 OPML...", KeyEvent.VK_I); imp.addActionListener(e -> importOpml());
        file.add(imp);
        JMenuItem exp = new JMenuItem("导出 OPML...", KeyEvent.VK_E); exp.addActionListener(e -> exportOpml());
        file.add(exp);
        file.addSeparator();
        JMenuItem exit = new JMenuItem("退出", KeyEvent.VK_X);
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        exit.addActionListener(e -> System.exit(0));
        file.add(exit);
        mb.add(file);

        JMenu feedsMenu = new JMenu("订阅"); feedsMenu.setMnemonic(KeyEvent.VK_E);
        JMenuItem ra = new JMenuItem("刷新全部", KeyEvent.VK_R);
        ra.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        ra.addActionListener(e -> refreshAllFeeds());
        feedsMenu.add(ra);
        JMenuItem rf = new JMenuItem("刷新选中订阅", KeyEvent.VK_S); rf.addActionListener(e -> refreshSelectedFeed());
        feedsMenu.add(rf);
        mb.add(feedsMenu);

        JMenu help = new JMenu("帮助"); help.setMnemonic(KeyEvent.VK_H);
        JMenuItem about = new JMenuItem("关于 RSS Reader", KeyEvent.VK_A); about.addActionListener(e -> showAboutDialog());
        help.add(about);
        mb.add(help);
        return mb;
    }

    private JToolBar createToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(new EmptyBorder(6, 8, 6, 8));
        tb.setBackground(COLOR_TOOLBAR);

        JButton addBtn = new JButton("＋ 添加订阅");
        addBtn.setToolTipText("添加新的 RSS 订阅源");
        addBtn.addActionListener(e -> showAddFeedDialog());
        styleBtn(addBtn); tb.add(addBtn);

        removeButton = new JButton("✕ 删除");
        removeButton.setToolTipText("删除选中的订阅");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> removeSelectedFeed());
        styleBtn(removeButton); tb.add(removeButton);

        tb.addSeparator();

        refreshAllButton = new JButton("↻ 刷新全部");
        refreshAllButton.setToolTipText("刷新所有订阅");
        refreshAllButton.addActionListener(e -> refreshAllFeeds());
        styleBtn(refreshAllButton); tb.add(refreshAllButton);

        refreshFeedButton = new JButton("↻ 刷新订阅");
        refreshFeedButton.setToolTipText("刷新选中的订阅");
        refreshFeedButton.setEnabled(false);
        refreshFeedButton.addActionListener(e -> refreshSelectedFeed());
        styleBtn(refreshFeedButton); tb.add(refreshFeedButton);

        tb.addSeparator();

        statsLabel = new JLabel("  暂无订阅  ");
        statsLabel.setForeground(COLOR_UNREAD);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.PLAIN, 12));
        tb.add(statsLabel);
        return tb;
    }

    private static void styleBtn(JButton b) {
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BTN_BDR, 1),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        b.setBackground(COLOR_BTN_BG);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // ---- 左侧树 ----

    private JPanel createTreePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                "订阅列表", TitledBorder.LEFT, TitledBorder.TOP, FONT_TITLE, COLOR_TITLE_FG));

        treeModel = new DefaultTreeModel(rootNode);
        feedTree = new JTree(treeModel);
        feedTree.setRootVisible(false);
        feedTree.setShowsRootHandles(true);
        feedTree.setRowHeight(30);
        feedTree.setFont(FONT_TREE);
        feedTree.setCellRenderer(new FeedTreeRenderer());

        feedTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) feedTree.getLastSelectedPathComponent();
            if (n == null) return;
            Object o = n.getUserObject();
            if (o instanceof Feed f) {
                selectedFeed = f; selectedArticle = null;
                removeButton.setEnabled(true); refreshFeedButton.setEnabled(true);
                setStatus("订阅: " + f.getName() + " (" + f.getArticleCount() + " 篇文章)");
                contentPane.setText(welcomeHtml());
            } else if (o instanceof Article a) {
                selectedArticle = a; selectedFeed = null;
                removeButton.setEnabled(false); refreshFeedButton.setEnabled(true);
                displayArticleContent(a);
                setStatus("文章: " + a.getTitle());
            } else {
                removeButton.setEnabled(false); refreshFeedButton.setEnabled(false);
            }
        });

        feedTree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                TreePath path = feedTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                DefaultMutableTreeNode n = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (n == null) return;
                if (n.getUserObject() instanceof Article a) openInBrowser(a);
                else if (n.getUserObject() instanceof Feed f) refreshFeed(f);
            }
        });

        // 右键菜单
        feedTree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { if (e.isPopupTrigger()) popup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(e); }
            private void popup(MouseEvent e) {
                TreePath path = feedTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                JPopupMenu m = new JPopupMenu();
                if (o instanceof Feed f) {
                    JMenuItem ri = new JMenuItem("刷新 " + f.getName()); ri.addActionListener(ev -> refreshFeed(f));
                    m.add(ri);
                    m.addSeparator();
                    JMenuItem di = new JMenuItem("删除 " + f.getName()); di.addActionListener(ev -> removeFeed(f));
                    m.add(di);
                } else if (o instanceof Article a) {
                    JMenuItem oi = new JMenuItem("在浏览器中打开"); oi.addActionListener(ev -> openInBrowser(a));
                    m.add(oi);
                }
                m.show(feedTree, e.getX(), e.getY());
            }
        });

        p.add(new JScrollPane(feedTree) {{ setBorder(null); }}, BorderLayout.CENTER);
        return p;
    }

    // ---- 渲染器（极简） ----

    private static class FeedTreeRenderer extends DefaultTreeCellRenderer {
        private static final Color FEED_FG = new Color(50, 55, 65);
        private static final Color ARTICLE_FG = new Color(50, 50, 55);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setBorder(new EmptyBorder(2, 6, 2, 6));
            setIcon(null);
            Object o = ((DefaultMutableTreeNode) value).getUserObject();

            if (o instanceof Feed f) {
                setFont(getFont().deriveFont(Font.BOLD, 13));
                setForeground(sel ? Color.WHITE : FEED_FG);
                setText("<html>📰 " + MainFrame.escHtml(f.getName()) + "  <font color='#aaa'>" + f.getArticleCount() + "</font></html>");
            } else if (o instanceof Article a) {
                setFont(getFont().deriveFont(Font.PLAIN, 12));
                setForeground(sel ? Color.WHITE : ARTICLE_FG);
                String t = a.getTitle();
                if (t.length() > MAX_TITLE_LEN) t = t.substring(0, MAX_TITLE_LEN) + "...";
                setText("<html>• " + MainFrame.escHtml(t) + "</html>");
            }
            return this;
        }
    }

    // ---- 右侧内容 ----

    private JPanel createContentPanel() {
        JPanel p = new JPanel(new BorderLayout());
        contentPane = new JEditorPane();
        contentPane.setContentType("text/html");
        contentPane.setEditable(false);
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        contentPane.setText(welcomeHtml());
        JScrollPane sp = new JScrollPane(contentPane);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                "文章内容", TitledBorder.LEFT, TitledBorder.TOP, FONT_TITLE, COLOR_TITLE_FG));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private String welcomeHtml() {
        return "<html><head><style>" +
                "body{font-family:\"PingFang SC\",sans-serif;margin:0;background:linear-gradient(135deg,#f8fafc,#e8ecf1)}" +
                ".card{max-width:400px;margin:80px auto;padding:40px;background:#fff;border-radius:16px;" +
                "box-shadow:0 4px 24px rgba(0,0,0,.06);text-align:center}" +
                ".icon{font-size:48px;margin-bottom:16px}.title{font-size:20px;color:#1a1a2e;font-weight:600}" +
                ".sub{font-size:13px;color:#888;line-height:1.8}" +
                ".hint{margin-top:24px;padding:14px 18px;background:#f0f4ff;border-radius:10px;font-size:12px;color:#5b7cfa}" +
                ".hint b{color:#3b5fd9}</style></head><body>" +
                "<div class=card><div class=icon>📰</div><h2 class=title>RSS 阅读器</h2>" +
                "<p class=sub>展开左侧订阅源<br>点击文章即可阅读</p>" +
                "<div class=hint>按 <b>⌘N</b> 或点击 <b>＋ 添加订阅</b> 开始</div></div></body></html>";
    }

    private JPanel createStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, COLOR_BORDER));
        p.setBackground(COLOR_STATUSBAR);
        statusLabel = new JLabel(" 就绪");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12));
        statusLabel.setForeground(COLOR_STATUS);
        statusLabel.setBorder(new EmptyBorder(3, 8, 3, 5));
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    // ==================== 树管理 ====================

    private void rebuildTree() {
        rootNode.removeAllChildren();
        for (Feed f : feeds) {
            DefaultMutableTreeNode fn = new DefaultMutableTreeNode(f);
            for (Article a : f.getArticles()) fn.add(new DefaultMutableTreeNode(a));
            rootNode.add(fn);
        }
        treeModel.reload();
        for (int i = 0; i < feedTree.getRowCount(); i++) feedTree.expandRow(i);
    }

    // ==================== 订阅 CRUD ====================

    private void loadFeeds() {
        try {
            feeds.clear();
            feeds.addAll(FeedStorage.loadFeeds());
            if (!feeds.isEmpty()) setStatus("已加载 " + feeds.size() + " 个订阅");
            rebuildTree();
            updateStats();
        } catch (IOException e) { setStatus("加载订阅失败: " + e.getMessage()); }
    }

    private void saveFeeds() {
        try { FeedStorage.saveFeeds(feeds); }
        catch (IOException e) { setStatus("保存订阅失败: " + e.getMessage()); }
    }

    private void showAddFeedDialog() {
        String[] r = AddFeedDialog.showDialog(this);
        if (r == null) return;
        String name = r[0], url = r[1];
        for (Feed f : feeds) {
            if (f.getUrl().equals(url)) {
                JOptionPane.showMessageDialog(this, "该订阅源已经存在:\n" + f.getName(),
                        "重复订阅", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        Feed nf = new Feed(name, url);
        feeds.add(nf);
        saveFeeds();
        rebuildTree();
        updateStats();
        refreshFeed(nf);
    }

    private void removeSelectedFeed() {
        if (selectedFeed != null) { removeFeed(selectedFeed); return; }
        DefaultMutableTreeNode n = (DefaultMutableTreeNode) feedTree.getLastSelectedPathComponent();
        if (n == null) return;
        if (n.getUserObject() instanceof Feed f) removeFeed(f);
        else if (n.getUserObject() instanceof Article a) {
            for (Feed f : feeds) if (f.getArticles().contains(a)) { removeFeed(f); return; }
        }
    }

    private void removeFeed(Feed feed) {
        if (JOptionPane.showConfirmDialog(this, "确定要删除订阅 \"" + feed.getName() + "\" 吗?",
                "删除订阅", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
            return;
        feeds.remove(feed);
        selectedFeed = null; selectedArticle = null;
        contentPane.setText(welcomeHtml());
        saveFeeds();
        rebuildTree();
        updateStats();
        removeButton.setEnabled(false); refreshFeedButton.setEnabled(false);
        setStatus("已删除订阅: " + feed.getName());
    }

    // ==================== 刷新 ====================

    private void refreshAllFeeds() {
        if (feeds.isEmpty()) { setStatus("暂无订阅可刷新，请先添加订阅!"); return; }
        refreshAllButton.setEnabled(false);
        setStatus("正在刷新全部订阅...");

        new SwingWorker<List<Feed>, Feed>() {
            @Override
            protected List<Feed> doInBackground() {
                List<Feed> uf = new ArrayList<>(feeds.size());
                for (Feed f : feeds) {
                    try { uf.add(feedService.fetchFeed(f.getUrl(), f.getName())); publish(uf.get(uf.size() - 1)); }
                    catch (Exception e) { uf.add(new Feed(f.getName(), f.getUrl())); }
                }
                return uf;
            }
            @Override
            protected void process(List<Feed> chunks) {
                for (Feed u : chunks) {
                    for (Feed f : feeds) {
                        if (f.getUrl().equals(u.getUrl())) {
                            f.setArticles(u.getArticles());
                            f.setDescription(u.getDescription());
                            f.setLastUpdated(u.getLastUpdated());
                            break;
                        }
                    }
                }
                rebuildTree(); updateStats();
            }
            @Override
            protected void done() {
                refreshAllButton.setEnabled(true);
                try {
                    List<Feed> r = get();
                    int total = r.stream().mapToInt(Feed::getArticleCount).sum();
                    setStatus("刷新完成: " + r.size() + " 个订阅, 共 " + total + " 篇文章");
                } catch (Exception e) { setStatus("刷新出错: " + e.getMessage()); }
                rebuildTree(); updateStats();
            }
        }.execute();
    }

    private void refreshSelectedFeed() {
        if (selectedFeed != null) refreshFeed(selectedFeed);
        else {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) feedTree.getLastSelectedPathComponent();
            if (n == null) return;
            if (n.getUserObject() instanceof Feed f) refreshFeed(f);
            else if (n.getUserObject() instanceof Article a) {
                for (Feed f : feeds) if (f.getArticles().contains(a)) { refreshFeed(f); return; }
            }
        }
    }

    private void refreshFeed(Feed feed) {
        refreshFeedButton.setEnabled(false); refreshAllButton.setEnabled(false);
        setStatus("正在刷新: " + feed.getName() + "...");
        new SwingWorker<Feed, Void>() {
            @Override
            protected Feed doInBackground() throws Exception {
                return feedService.fetchFeed(feed.getUrl(), feed.getName());
            }
            @Override
            protected void done() {
                refreshFeedButton.setEnabled(true); refreshAllButton.setEnabled(true);
                try {
                    Feed u = get();
                    for (Feed f : feeds) {
                        if (f.getUrl().equals(u.getUrl())) {
                            f.setArticles(u.getArticles());
                            f.setDescription(u.getDescription());
                            f.setLastUpdated(u.getLastUpdated());
                            break;
                        }
                    }
                    rebuildTree(); updateStats();
                    setStatus("刷新完成: " + feed.getName() + " - " + u.getArticleCount() + " 篇文章");
                } catch (Exception ex) {
                    setStatus("刷新出错: " + ex.getMessage());
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "无法获取订阅:\n" + feed.getUrl(), "订阅错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==================== 内容 ====================

    private void displayArticleContent(Article a) {
        StringBuilder h = new StringBuilder(2048);
        h.append("<html><head><style>");
        h.append("body{font-family:\"PingFang SC\",sans-serif;padding:28px 32px;line-height:1.8;color:#2c2c2e;max-width:780px;margin:0 auto;background:#fff}");
        h.append("h1{font-size:22px;color:#1a1a2e;margin-bottom:6px;font-weight:700;letter-spacing:-.3px}");
        h.append("h2{font-size:17px;color:#333;margin:24px 0 10px}h3{font-size:15px;color:#444;margin:18px 0 8px}");
        h.append(".meta{display:flex;flex-wrap:wrap;gap:6px 18px;color:#999;font-size:12px;margin-bottom:24px;padding-bottom:16px;border-bottom:1px solid #f0f0f0}");
        h.append(".meta-item{display:inline-flex;align-items:center;gap:4px}.meta-label{color:#bbb}");
        h.append("a{color:#3b5fd9;text-decoration:none}a:hover{text-decoration:underline}");
        h.append("img,video{max-width:100%;height:auto;border-radius:8px;margin:12px 0}");
        h.append("pre{background:#f6f8fa;padding:16px;border-radius:8px;overflow-x:auto;font-size:13px;line-height:1.5;border:1px solid #e8eaed}");
        h.append("code{background:#f1f3f5;padding:2px 6px;border-radius:4px;font-size:13px}pre code{background:none;padding:0}");
        h.append("blockquote{border-left:3px solid #667eea;margin:16px 0;padding:10px 18px;background:#f8f9ff;color:#555;border-radius:0 8px 8px 0}");
        h.append("p{margin:10px 0}ul,ol{padding-left:24px}li{margin:4px 0}");
        h.append(".origin-link{display:inline-block;margin-top:28px;padding:10px 20px;background:#f0f4ff;border-radius:10px;font-size:13px;color:#3b5fd9;text-decoration:none;font-weight:500}");
        h.append(".origin-link:hover{background:#e0e8ff}");
        h.append("</style></head><body>");

        h.append("<h1>").append(escHtml(a.getTitle())).append("</h1>");
        h.append("<div class=meta>");
        if (a.getFeedName() != null && !a.getFeedName().isEmpty())
            h.append("<span class=meta-item><span class=meta-label>订阅源</span> ").append(escHtml(a.getFeedName())).append("</span>");
        if (a.getAuthor() != null && !a.getAuthor().isEmpty())
            h.append("<span class=meta-item><span class=meta-label>作者</span> ").append(escHtml(a.getAuthor())).append("</span>");
        if (a.getPubDate() != null)
            h.append("<span class=meta-item><span class=meta-label>发布</span> ").append(a.getPubDate().toString()).append("</span>");
        h.append("</div>");

        String c = a.getContent();
        if (c != null && !c.isBlank()) h.append(c);
        else h.append("<p><em>暂无内容</em></p>");

        if (a.getLink() != null && !a.getLink().isEmpty())
            h.append("<a class=origin-link href='").append(a.getLink()).append("'>📎 阅读原文 →</a>");
        h.append("</body></html>");

        contentPane.setText(h.toString());
        contentPane.setCaretPosition(0);
    }

    private void openInBrowser(Article a) {
        if (a.getLink() == null || a.getLink().isEmpty()) { setStatus("该文章没有可用的链接"); return; }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(a.getLink()));
                setStatus("已在浏览器中打开: " + a.getLink());
            } else setStatus("当前平台不支持打开浏览器");
        } catch (Exception e) { setStatus("无法打开浏览器: " + e.getMessage()); }
    }

    // ==================== 导入/导出 ====================

    private void importOpml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("导入 OPML 文件");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OPML 文件 (*.opml, *.xml)", "opml", "xml"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<Feed> imp = parseOpml(fc.getSelectedFile().getAbsolutePath());
            for (Feed f : imp) if (!feeds.contains(f)) feeds.add(f);
            saveFeeds(); rebuildTree(); updateStats();
            setStatus("从 OPML 导入了 " + imp.size() + " 个订阅");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导入 OPML 出错: " + e.getMessage(), "导入错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<Feed> parseOpml(String path) throws Exception {
        List<Feed> r = new ArrayList<>();
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        org.w3c.dom.Document d = f.newDocumentBuilder().parse(path);
        org.w3c.dom.NodeList nl = d.getElementsByTagName("outline");
        for (int i = 0; i < nl.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) nl.item(i);
            String xu = el.getAttribute("xmlUrl"), t = el.getAttribute("title");
            if (xu != null && !xu.isEmpty()) {
                if (t == null || t.isEmpty()) t = el.getAttribute("text");
                if (t == null || t.isEmpty()) t = xu;
                r.add(new Feed(t, xu));
            }
        }
        return r;
    }

    private void exportOpml() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("导出 OPML 文件");
        fc.setSelectedFile(new java.io.File("rss-feeds.opml"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OPML 文件 (*.opml)", "opml"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            String p = fc.getSelectedFile().getAbsolutePath();
            if (!p.endsWith(".opml")) p += ".opml";
            writeOpml(p);
            setStatus("已导出 " + feeds.size() + " 个订阅到 OPML");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出 OPML 出错: " + e.getMessage(), "导出错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeOpml(String path) throws IOException {
        StringBuilder x = new StringBuilder(1024);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n<head><title>RSS Reader</title></head>\n<body>\n");
        for (Feed f : feeds)
            x.append("  <outline text=\"").append(escXml(f.getName())).append("\" title=\"").append(escXml(f.getName()))
             .append("\" type=\"rss\" xmlUrl=\"").append(escXml(f.getUrl())).append("\"/>\n");
        x.append("</body>\n</opml>\n");
        java.nio.file.Files.writeString(java.nio.file.Paths.get(path), x.toString());
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "RSS 阅读器 v1.0\n\n基于 Java Swing 的桌面 RSS 订阅阅读器。\n\n" +
                "• 树形订阅源 + 文章层级展示\n• HTML 渲染阅读\n• OPML 导入/导出\n" +
                "• 浏览器打开原文\n\n技术栈: Java 17 + Swing + Rome RSS 库\nMIT License",
                "关于 RSS 阅读器", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== 工具 ====================

    private void setStatus(String msg) { statusLabel.setText(" " + msg); }

    private void updateStats() {
        int tf = feeds.size();
        int ta = feeds.stream().mapToInt(Feed::getArticleCount).sum();
        statsLabel.setText(tf == 0 ? "  暂无订阅  " : String.format("  %d 个订阅 | %d 篇文章  ", tf, ta));
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
