package com.rssreader.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 添加 / 编辑 RSS 订阅的对话框。
 */
public class AddFeedDialog extends JDialog {

    private JTextField nameField;
    private JTextField urlField;
    private JButton confirmButton;
    private JButton testButton;
    private JLabel statusLabel;

    private boolean confirmed = false;
    private final boolean editMode;

    /** 新增模式 */
    public AddFeedDialog(JFrame parent) {
        this(parent, "", "");
    }

    /** 编辑模式 */
    public AddFeedDialog(JFrame parent, String existingName, String existingUrl) {
        super(parent, existingUrl.isEmpty() ? "添加 RSS 订阅" : "编辑 RSS 订阅", true);
        this.editMode = !existingUrl.isEmpty();
        initUI(existingName, existingUrl);
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI(String initName, String initUrl) {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 16, 20));
        mainPanel.setBackground(Color.WHITE);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 订阅名称
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel nameLabel = new JLabel("订阅名称:");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13));
        inputPanel.add(nameLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        nameField = new JTextField(initName, 25);
        nameField.setFont(nameField.getFont().deriveFont(Font.PLAIN, 13));
        nameField.setToolTipText("订阅的显示名称（可选，留空则自动使用订阅源标题）");
        inputPanel.add(nameField, gbc);

        // 订阅 URL
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel urlLabel = new JLabel("订阅 URL:");
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 13));
        inputPanel.add(urlLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        urlField = new JTextField(initUrl, 25);
        urlField.setFont(urlField.getFont().deriveFont(Font.PLAIN, 13));
        urlField.setToolTipText("RSS/Atom 订阅源的链接地址");
        inputPanel.add(urlField, gbc);

        // 状态标签
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        inputPanel.add(statusLabel, gbc);

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btnPanel.setBackground(Color.WHITE);

        testButton = new JButton("测试连接");
        testButton.setFont(testButton.getFont().deriveFont(Font.PLAIN, 12));
        testButton.addActionListener(e -> testFeed());

        confirmButton = new JButton(editMode ? "保存" : "添加");
        confirmButton.setFont(confirmButton.getFont().deriveFont(Font.PLAIN, 12));
        if (!editMode) confirmButton.setEnabled(false);
        confirmButton.addActionListener(e -> { confirmed = true; dispose(); });

        JButton cancelButton = new JButton("取消");
        cancelButton.setFont(cancelButton.getFont().deriveFont(Font.PLAIN, 12));
        cancelButton.addActionListener(e -> dispose());

        btnPanel.add(testButton);
        btnPanel.add(confirmButton);
        btnPanel.add(cancelButton);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        // 编辑模式下 URL 已有值，直接可保存
        if (editMode) {
            testButton.setEnabled(!initUrl.isEmpty());
        }

        urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange(); }
        });

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(confirmButton);
    }

    private void onChange() {
        testButton.setEnabled(!urlField.getText().trim().isEmpty());
        confirmButton.setEnabled(editMode);
        statusLabel.setText(" ");
    }

    private void testFeed() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        try { java.net.URI.create(url).toURL(); }
        catch (Exception e) {
            statusLabel.setText("✗ URL 格式无效");
            statusLabel.setForeground(new Color(220, 38, 38));
            return;
        }
        testButton.setEnabled(false);
        statusLabel.setText("正在测试连接...");
        statusLabel.setForeground(new Color(37, 99, 235));

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                            java.net.URI.create(url).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "RSS-Reader/1.0");
                    conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    return code == 200;
                } catch (Exception e) { return false; }
            }
            @Override
            protected void done() {
                testButton.setEnabled(true);
                try {
                    if (get()) {
                        statusLabel.setText("✓ 连接成功!");
                        statusLabel.setForeground(new Color(22, 163, 74));
                        confirmButton.setEnabled(true);
                    } else {
                        statusLabel.setText("✗ 无法连接到该 URL");
                        statusLabel.setForeground(new Color(220, 38, 38));
                    }
                } catch (Exception e) {
                    statusLabel.setText("✗ 错误: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 38, 38));
                }
            }
        }.execute();
    }

    public boolean isConfirmed() { return confirmed; }
    public String getFeedName() { return nameField.getText().trim(); }
    public String getFeedUrl() { return urlField.getText().trim(); }

    /** 新增对话框 → [name, url] 或 null */
    public static String[] showAddDialog(JFrame parent) {
        AddFeedDialog d = new AddFeedDialog(parent);
        d.setVisible(true);
        if (d.isConfirmed()) {
            String n = d.getFeedName(), u = d.getFeedUrl();
            if (n.isEmpty()) n = u;
            return new String[]{n, u};
        }
        return null;
    }

    /** 编辑对话框 → [name, url] 或 null */
    public static String[] showEditDialog(JFrame parent, String currentName, String currentUrl) {
        AddFeedDialog d = new AddFeedDialog(parent, currentName, currentUrl);
        d.setVisible(true);
        if (d.isConfirmed()) {
            String n = d.getFeedName(), u = d.getFeedUrl();
            if (n.isEmpty()) n = u;
            return new String[]{n, u};
        }
        return null;
    }
}
