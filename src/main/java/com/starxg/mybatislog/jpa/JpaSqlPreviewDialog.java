package com.starxg.mybatislog.jpa;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

/**
 * SQL preview dialog with copy and close actions.
 */
public class JpaSqlPreviewDialog extends DialogWrapper {

    private static final int MIN_WIDTH = 420;
    private static final int MAX_WIDTH = 900;
    private static final int MIN_HEIGHT = 180;
    private static final int MAX_HEIGHT = 560;

    private final String sourceSql;
    private final List<String> messages;
    private JTextArea sqlArea;
    private JPanel rootPanel;
    private String displayedSql;

    protected JpaSqlPreviewDialog(@Nullable Project project, String title, String sql, List<String> messages) {
        super(project, false);
        this.sourceSql = StringUtils.defaultString(sql);
        this.messages = messages;
        setTitle(title);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        rootPanel = new JPanel(new BorderLayout(0, 8));
        rootPanel.add(createTopToolbar(), BorderLayout.NORTH);

        sqlArea = new JTextArea();
        sqlArea.setEditable(false);
        sqlArea.setLineWrap(false);
        sqlArea.setWrapStyleWord(false);
        sqlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        rootPanel.add(new JScrollPane(sqlArea), BorderLayout.CENTER);
        applyFormatMode("轻量美化");

        if (!messages.isEmpty()) {
            final JTextArea tipArea = new JTextArea(String.join("\n", messages));
            tipArea.setEditable(false);
            tipArea.setOpaque(false);
            tipArea.setLineWrap(true);
            tipArea.setWrapStyleWord(true);
            rootPanel.add(tipArea, BorderLayout.SOUTH);
        }
        return rootPanel;
    }

    @Override
    protected Action[] createActions() {
        setCancelButtonText("关闭");
        return new Action[]{new DialogWrapperAction("复制") {
            @Override
            protected void doAction(java.awt.event.ActionEvent e) {
                CopyPasteManager.getInstance().setContents(new StringSelection(displayedSql));
            }
        }, getCancelAction()};
    }

    private JComponent createTopToolbar() {
        final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        final JLabel label = new JLabel("格式: ");
        final JComboBox<String> modeBox = new JComboBox<>(new String[]{"保留原样", "轻量美化", "强格式化"});
        modeBox.setSelectedItem("轻量美化");
        modeBox.addActionListener(e -> applyFormatMode((String) modeBox.getSelectedItem()));
        toolbar.add(label);
        toolbar.add(modeBox);
        return toolbar;
    }

    private void applyFormatMode(String mode) {
        if ("保留原样".equals(mode)) {
            displayedSql = sourceSql;
        } else if ("强格式化".equals(mode)) {
            displayedSql = strongFormatSql(sourceSql);
        } else {
            displayedSql = lightFormatSql(sourceSql);
        }
        sqlArea.setText(displayedSql);
        sqlArea.setCaretPosition(0);
        updateDialogSize(displayedSql);
    }

    private String lightFormatSql(String sql) {
        final String[] lines = StringUtils.defaultString(sql).split("\\R", -1);
        final StringBuilder sb = new StringBuilder(sql.length());
        for (int i = 0; i < lines.length; i++) {
            sb.append(StringUtils.normalizeSpace(lines[i]));
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return StringUtils.trimToEmpty(sb.toString());
    }

    private String strongFormatSql(String sql) {
        String formatted = lightFormatSql(sql);
        final List<String> keywords = Arrays.asList(
                "select", "from", "where", "group by", "order by", "having", "limit",
                "left join", "right join", "inner join", "join", "on",
                "insert into", "values", "update", "set", "delete from", "and", "or"
        );
        for (String keyword : keywords) {
            final String upper = keyword.toUpperCase(Locale.ROOT);
            formatted = formatted.replaceAll("(?i)\\s+" + java.util.regex.Pattern.quote(keyword) + "\\s+", "\n" + upper + " ");
        }
        return formatted.replaceAll("\\n{2,}", "\n").trim();
    }

    private void updateDialogSize(String sql) {
        if (rootPanel == null || sqlArea == null) {
            return;
        }

        final FontMetrics metrics = sqlArea.getFontMetrics(sqlArea.getFont());
        final int lineHeight = Math.max(metrics.getHeight(), 16);
        final String[] lines = StringUtils.defaultString(sql).split("\\R", -1);
        final int lineCount = Math.max(lines.length, 1);

        int maxLineChars = 1;
        for (String line : lines) {
            maxLineChars = Math.max(maxLineChars, StringUtils.length(line));
        }

        final int estimatedWidth = maxLineChars * metrics.charWidth('W') + 110;
        final int estimatedHeight = lineCount * lineHeight + 130;

        final int width = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, estimatedWidth));
        final int height = Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, estimatedHeight));

        rootPanel.setPreferredSize(new Dimension(width, height));
        if (getWindow() != null) {
            getWindow().pack();
            getWindow().setLocationRelativeTo(getWindow().getOwner());
        }
    }
}
