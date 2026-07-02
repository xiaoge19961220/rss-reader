package com.rssreader;

import com.rssreader.ui.MainFrame;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

/**
 * RSS Reader 应用程序入口。
 * 设置全局 UI 样式并启动主窗口。
 */
public class RssReaderApp {

    public static void main(String[] args) {
        // 设置系统原生外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("无法设置系统外观: " + e.getMessage());
        }

        // 全局 UI 美化设置
        applyGlobalUIPolish();

        // 在事件分发线程上启动应用
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }

    /**
     * 应用全局 UI 美化：字体、表格斑马纹、圆角等。
     */
    private static void applyGlobalUIPolish() {
        // 设置全局默认字体
        Font defaultFont = new Font("PingFang SC", Font.PLAIN, 13);
        setUIFont(new FontUIResource(defaultFont));

        // 表格斑马纹
        UIManager.put("Table.alternateRowColor", new Color(245, 247, 250));

        // 更柔和的选择色
        UIManager.put("Table.selectionBackground", new Color(59, 130, 246));
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("List.selectionBackground", new Color(59, 130, 246));
        UIManager.put("List.selectionForeground", Color.WHITE);

        // 工具提示样式
        UIManager.put("ToolTip.background", new Color(50, 50, 55));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.font", new Font("PingFang SC", Font.PLAIN, 12));

        // 分割面板
        UIManager.put("SplitPane.dividerSize", 1);
        UIManager.put("SplitPane.background", new Color(220, 220, 224));
    }

    /**
     * 递归设置所有组件的默认字体。
     */
    private static void setUIFont(FontUIResource f) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }
}
