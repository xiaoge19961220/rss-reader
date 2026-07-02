package com.rssreader.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * 添加 RSS 订阅的对话框。
 */
public class AddFeedDialog extends JDialog {

    private JTextField nameField;
    private JTextField urlField;
    private JButton addButton;
    private JButton cancelButton;
    private JButton testButton;
    private JLabel statusLabel;

    private boolean confirmed = false;

    public AddFeedDialog(JFrame parent) {
        super(parent, "添加 RSS 订阅", true);
        initUI();
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(20, 20, 16, 20));
        mainPanel.setBackground(Color.WHITE);

        // 输入面板
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 订阅名称
        JLabel nameLabel = new JLabel("订阅名称:");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 13));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(nameLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        nameField = new JTextField(25);
        nameField.setToolTipText("订阅的显示名称（可选，留空则自动使用订阅源标题）");
        nameField.setFont(nameField.getFont().deriveFont(Font.PLAIN, 13));
        inputPanel.add(nameField, gbc);

        // 订阅 URL
        JLabel urlLabel = new JLabel("订阅 URL:");
        urlLabel.setFont(urlLabel.getFont().deriveFont(Font.PLAIN, 13));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(urlLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        urlField = new JTextField(25);
        urlField.setToolTipText("RSS/Atom 订阅源的链接地址");
        urlField.setFont(urlField.getFont().deriveFont(Font.PLAIN, 13));
        inputPanel.add(urlField, gbc);

        // 状态标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        inputPanel.add(statusLabel, gbc);

        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonPanel.setBackground(Color.WHITE);

        testButton = new JButton("测试连接");
        testButton.setFont(testButton.getFont().deriveFont(Font.PLAIN, 12));
        testButton.addActionListener(e -> testFeed());

        addButton = new JButton("添加");
        addButton.setFont(addButton.getFont().deriveFont(Font.PLAIN, 12));
        addButton.setEnabled(false);
        addButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        cancelButton = new JButton("取消");
        cancelButton.setFont(cancelButton.getFont().deriveFont(Font.PLAIN, 12));
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(testButton);
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // URL 输入监听
        urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onUrlChanged();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onUrlChanged();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onUrlChanged();
            }
        });

        setContentPane(mainPanel);
        getRootPane().setDefaultButton(addButton);
    }

    private void onUrlChanged() {
        String url = urlField.getText().trim();
        testButton.setEnabled(!url.isEmpty());
        addButton.setEnabled(false);
        statusLabel.setText(" ");
    }

    private void testFeed() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            return;
        }

        // 基础 URL 校验
        try {
            java.net.URI.create(url).toURL();
        } catch (Exception e) {
            statusLabel.setText("✗ URL 格式无效");
            statusLabel.setForeground(new Color(220, 38, 38));
            return;
        }

        testButton.setEnabled(false);
        statusLabel.setText("正在测试连接...");
        statusLabel.setForeground(new Color(37, 99, 235));

        // 后台测试连接
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                            java.net.URI.create(url).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "RSS-Reader/1.0");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();
                    int code = conn.getResponseCode();
                    conn.disconnect();
                    return code == 200;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                testButton.setEnabled(true);
                try {
                    boolean success = get();
                    if (success) {
                        statusLabel.setText("✓ 连接成功!");
                        statusLabel.setForeground(new Color(22, 163, 74));
                        addButton.setEnabled(true);
                    } else {
                        statusLabel.setText("✗ 无法连接到该 URL");
                        statusLabel.setForeground(new Color(220, 38, 38));
                    }
                } catch (Exception e) {
                    statusLabel.setText("✗ 错误: " + e.getMessage());
                    statusLabel.setForeground(new Color(220, 38, 38));
                }
            }
        };
        worker.execute();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getFeedName() {
        return nameField.getText().trim();
    }

    public String getFeedUrl() {
        return urlField.getText().trim();
    }

    /**
     * 显示对话框，返回 [名称, URL] 或 null（取消时）。
     */
    public static String[] showDialog(JFrame parent) {
        AddFeedDialog dialog = new AddFeedDialog(parent);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            String name = dialog.getFeedName();
            String url = dialog.getFeedUrl();
            if (name.isEmpty()) {
                name = url;
            }
            return new String[]{name, url};
        }
        return null;
    }
}
