package com.rssreader.update;

import com.rssreader.AppConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;

/**
 * 更新提示对话框。
 */
public class UpdateDialog extends JDialog {

    private boolean shouldUpdate = false;
    private boolean skipThisVersion = false;

    public UpdateDialog(JFrame parent, UpdateChecker.UpdateInfo info) {
        super(parent, "发现新版本", true);
        initUI(info);
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI(UpdateChecker.UpdateInfo info) {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(new EmptyBorder(20, 24, 16, 24));
        main.setBackground(Color.WHITE);

        // 顶部图标 + 标题
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Color.WHITE);
        JLabel icon = new JLabel("🆕");
        icon.setFont(icon.getFont().deriveFont(Font.PLAIN, 28));
        header.add(icon, BorderLayout.WEST);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setBackground(Color.WHITE);
        JLabel title = new JLabel("发现新版本 v" + info.version());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15));
        JLabel sub = new JLabel("当前版本: v" + AppConfig.VERSION + "  →  最新版本: v" + info.version());
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 12));
        sub.setForeground(Color.GRAY);
        titlePanel.add(title);
        titlePanel.add(sub);
        header.add(titlePanel, BorderLayout.CENTER);
        main.add(header, BorderLayout.NORTH);

        // 更新日志
        if (info.body() != null && !info.body().isBlank()) {
            JTextArea body = new JTextArea(info.body());
            body.setFont(new Font("PingFang SC", Font.PLAIN, 12));
            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setBackground(new Color(248, 249, 250));
            body.setBorder(new EmptyBorder(10, 12, 10, 12));
            JScrollPane sp = new JScrollPane(body);
            sp.setPreferredSize(new Dimension(420, 120));
            sp.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 224)));
            main.add(sp, BorderLayout.CENTER);
        }

        // 按钮
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setBackground(Color.WHITE);

        JButton skip = new JButton("跳过此版本");
        skip.setFont(skip.getFont().deriveFont(Font.PLAIN, 12));
        skip.setForeground(Color.GRAY);
        skip.addActionListener(e -> { skipThisVersion = true; dispose(); });

        JButton later = new JButton("稍后提醒");
        later.setFont(later.getFont().deriveFont(Font.PLAIN, 12));
        later.addActionListener(e -> dispose());

        JButton update = new JButton("立即更新");
        update.setFont(update.getFont().deriveFont(Font.BOLD, 12));
        update.setBackground(new Color(59, 130, 246));
        update.setForeground(Color.WHITE);
        update.setBorder(new EmptyBorder(8, 16, 8, 16));
        update.setFocusPainted(false);
        update.setCursor(new Cursor(Cursor.HAND_CURSOR));
        update.addActionListener(e -> { shouldUpdate = true; dispose(); });

        btns.add(skip);
        btns.add(later);
        btns.add(update);
        main.add(btns, BorderLayout.SOUTH);

        setContentPane(main);
        getRootPane().setDefaultButton(update);
    }

    public boolean isShouldUpdate() { return shouldUpdate; }
    public boolean isSkipThisVersion() { return skipThisVersion; }

    /**
     * 显示更新对话框，处理升级流程。
     */
    public static void showIfAvailable(JFrame parent) {
        UpdateChecker.checkAsync(parent, info -> {
            UpdateDialog dlg = new UpdateDialog(parent, info);
            dlg.setVisible(true);

            if (dlg.isSkipThisVersion()) {
                // 记录跳过版本，下次不再提醒
                saveSkipVersion(info.version());
            }

            if (dlg.isShouldUpdate()) {
                doUpdate(parent, info);
            }
        });
    }

    private static void doUpdate(JFrame parent, UpdateChecker.UpdateInfo info) {
        // 显示下载进度
        JDialog progressDlg = new JDialog(parent, "正在更新...", true);
        JLabel progressLabel = new JLabel("正在下载新版本...", SwingConstants.CENTER);
        progressLabel.setBorder(new EmptyBorder(20, 40, 20, 40));
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.PLAIN, 13));
        progressDlg.add(progressLabel);
        progressDlg.pack();
        progressDlg.setLocationRelativeTo(parent);
        progressDlg.setResizable(false);

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return UpdateChecker.downloadAndReplace(info, parent);
            }
            @Override
            protected void done() {
                progressDlg.dispose();
                try {
                    Path newJar = get();
                    int r = JOptionPane.showConfirmDialog(parent,
                            "新版本已下载完成!\n\n" +
                            "文件: " + newJar.getFileName() + "\n\n" +
                            "是否立即重启应用新版本?",
                            "更新完成", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                    if (r == JOptionPane.YES_OPTION) {
                        restart(newJar);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(parent,
                            "更新失败: " + e.getMessage(),
                            "更新错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
        progressDlg.setVisible(true);
    }

    /** 用新 JAR 重启应用 */
    private static void restart(Path newJar) {
        try {
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(java, "-jar", newJar.toAbsolutePath().toString())
                    .inheritIO().start();
            System.exit(0);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "请手动运行: java -jar " + newJar.toAbsolutePath(),
                    "重启提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 记录跳过的版本号 */
    private static void saveSkipVersion(String version) {
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("user.home"), ".rss-reader", "skip-version"),
                    version);
        } catch (Exception ignored) {}
    }

    /** 读取跳过的版本号 */
    public static String getSkipVersion() {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of(System.getProperty("user.home"), ".rss-reader", "skip-version"));
        } catch (Exception e) {
            return "";
        }
    }
}
