package com.rssreader.ui;

import com.rssreader.model.Article;

import javax.swing.table.AbstractTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章表格模型，用于在 JTable 中展示文章列表。
 */
public class ArticleTableModel extends AbstractTableModel {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String[] columnNames = {"标题", "日期", "来源"};
    private List<Article> articles;

    public ArticleTableModel() {
        this.articles = new ArrayList<>();
    }

    public void setArticles(List<Article> articles) {
        this.articles = articles != null ? new ArrayList<>(articles) : new ArrayList<>();
        fireTableDataChanged();
    }

    public Article getArticleAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < articles.size()) {
            return articles.get(rowIndex);
        }
        return null;
    }

    public void addArticles(List<Article> newArticles) {
        if (newArticles != null) {
            this.articles.addAll(newArticles);
            fireTableDataChanged();
        }
    }

    public void clear() {
        this.articles.clear();
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return articles.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Article article = articles.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> article.getTitle();
            case 1 -> formatDate(article.getPubDate());
            case 2 -> article.getFeedName();
            default -> "";
        };
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
}
