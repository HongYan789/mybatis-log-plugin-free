package com.starxg.mybatislog;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.starxg.mybatislog.gui.MyBatisLogManager;

/**
 * MyBatisLogConsoleFilter
 *
 * @author huangxingguang
 */
public class MyBatisLogConsoleFilter implements Filter {

    public static final String PREPARING_KEY = MyBatisLogConsoleFilter.class.getName() + ".Preparing";
    public static final String PARAMETERS_KEY = MyBatisLogConsoleFilter.class.getName() + ".Parameters";
    public static final String KEYWORDS_KEY = MyBatisLogConsoleFilter.class.getName() + ".Keywords";

    public static final String INSERT_SQL_COLOR_KEY = MyBatisLogConsoleFilter.class.getName() + ".InsertSQLColor";
    public static final String DELETE_SQL_COLOR_KEY = MyBatisLogConsoleFilter.class.getName() + ".DeleteSQLColor";
    public static final String UPDATE_SQL_COLOR_KEY = MyBatisLogConsoleFilter.class.getName() + ".UpdateSQLColor";
    public static final String SELECT_SQL_COLOR_KEY = MyBatisLogConsoleFilter.class.getName() + ".SelectSQLColor";

    private static final char MARK = '?';
    private static final String HIBERNATE_SQL_PREFIX = "Hibernate:";
    private static final String HIBERNATE_SQL_LOGGER = "org.hibernate.SQL";
    private static final Pattern JPA_BINDING_PATTERN = Pattern.compile("binding (?:parameter|value) \\[(\\d+)](?: as \\[?([^\\]]+)\\]?)?\\s*(?:-|<-)\\s*(.+)$");

    private static final Set<String> NEED_BRACKETS;

    private final Project project;

    private String sql = null;
    private String jpaSql = null;
    private final Map<Integer, Map.Entry<String, String>> jpaParams = new TreeMap<>();

    static {
        Set<String> types = new HashSet<>(8);
        types.add("String");
        types.add("Date");
        types.add("Time");
        types.add("LocalDate");
        types.add("LocalTime");
        types.add("LocalDateTime");
        types.add("BigDecimal");
        types.add("Timestamp");
        NEED_BRACKETS = Collections.unmodifiableSet(types);
    }

    MyBatisLogConsoleFilter(Project project) {
        this.project = project;
    }

    @Override
    public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
        final MyBatisLogManager manager = ensureManager(line);
        if (Objects.isNull(manager)) {
            return null;
        }

        if (!manager.isRunning()) {
            if (isPotentialSqlLine(line, manager)) {
                manager.run();
            } else {
                return null;
            }
        }

        final String preparing = manager.getPreparing();
        final String parameters = manager.getParameters();
        final List<String> keywords = manager.getKeywords();

        if (CollectionUtils.isNotEmpty(keywords)) {
            for (String keyword : keywords) {
                if (line.contains(keyword)) {
                    sql = null;
                    resetJpa();
                    return null;
                }
            }
        }

        if (handleJpaLine(line, manager)) {
            return null;
        }

        if (line.contains(preparing)) {
            flushJpaSql(manager, true);
            sql = line;
            return null;
        }

        if (StringUtils.isNotBlank(sql) && !line.contains(parameters)) {
            return null;
        }

        if (StringUtils.isBlank(sql)) {
            return null;
        }

        final String logPrefix = StringUtils.substringBefore(sql, preparing);
        final String wholeSql = parseSql(StringUtils.substringAfter(sql, preparing), parseParams(StringUtils.substringAfter(line, parameters))).toString();

        final String key;
        if (StringUtils.startsWithIgnoreCase(wholeSql, "insert")) {
            key = INSERT_SQL_COLOR_KEY;
        } else if (StringUtils.startsWithIgnoreCase(wholeSql, "delete")) {
            key = DELETE_SQL_COLOR_KEY;
        } else if (StringUtils.startsWithIgnoreCase(wholeSql, "update")) {
            key = UPDATE_SQL_COLOR_KEY;
        } else if (StringUtils.startsWithIgnoreCase(wholeSql, "select")) {
            key = SELECT_SQL_COLOR_KEY;
        } else {
            key = "unknown";
        }

        manager.println(logPrefix, wholeSql, PropertiesComponent.getInstance(project).getInt(key, ConsoleViewContentType.ERROR_OUTPUT.getAttributes().getForegroundColor().getRGB()));

        return null;
    }

    private boolean handleJpaLine(String line, MyBatisLogManager manager) {
        final String jpaRawSql = extractJpaSql(line);
        if (StringUtils.isNotBlank(jpaRawSql)) {
            flushJpaSql(manager, true);
            jpaSql = jpaRawSql;
            flushJpaSql(manager, false);
            return true;
        }

        final Map.Entry<Integer, Map.Entry<String, String>> binding = parseJpaBinding(line);
        if (Objects.isNull(binding)) {
            return false;
        }

        jpaParams.put(binding.getKey(), binding.getValue());
        flushJpaSql(manager, false);
        return true;
    }

    private MyBatisLogManager ensureManager(String line) {
        MyBatisLogManager manager = MyBatisLogManager.getInstance(project);
        if (manager != null) {
            return manager;
        }
        if (!isPotentialSqlLine(line, null)) {
            return null;
        }
        return MyBatisLogManager.createInstance(project);
    }

    private boolean isPotentialSqlLine(String line, MyBatisLogManager manager) {
        final String preparing = manager != null ? manager.getPreparing()
                : PropertiesComponent.getInstance(project).getValue(PREPARING_KEY, "Preparing: ");
        final String parameters = manager != null ? manager.getParameters()
                : PropertiesComponent.getInstance(project).getValue(PARAMETERS_KEY, "Parameters: ");
        return line.contains(preparing) || line.contains(parameters) || line.contains(HIBERNATE_SQL_PREFIX)
                || line.contains(HIBERNATE_SQL_LOGGER) || line.contains("binding parameter [")
                || line.contains("binding value [");
    }

    private String extractJpaSql(String line) {
        if (line.contains(HIBERNATE_SQL_PREFIX)) {
            return StringUtils.trimToNull(StringUtils.substringAfterLast(line, HIBERNATE_SQL_PREFIX));
        }

        if (line.contains(HIBERNATE_SQL_LOGGER) && line.contains(" - ")) {
            return StringUtils.trimToNull(StringUtils.substringAfterLast(line, " - "));
        }

        return null;
    }

    private void flushJpaSql(MyBatisLogManager manager, boolean force) {
        if (StringUtils.isBlank(jpaSql)) {
            return;
        }

        final int paramCount = StringUtils.countMatches(jpaSql, String.valueOf(MARK));
        if (!force && paramCount > jpaParams.size()) {
            return;
        }

        final Queue<Map.Entry<String, String>> queue = new ArrayDeque<>(jpaParams.values());
        final String wholeSql = parseSql(jpaSql, queue).toString();
        final int color = PropertiesComponent.getInstance(project).getInt(SELECT_SQL_COLOR_KEY, ConsoleViewContentType.ERROR_OUTPUT.getAttributes().getForegroundColor().getRGB());
        manager.println("JPA", wholeSql, color);
        resetJpa();
    }

    private void resetJpa() {
        jpaSql = null;
        jpaParams.clear();
    }

    static StringBuilder parseSql(String sql, Queue<Map.Entry<String, String>> params) {

        final StringBuilder sb = new StringBuilder(sql);

        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) != MARK) {
                continue;
            }

            final Map.Entry<String, String> entry = params.poll();
            if (Objects.isNull(entry)) {
                continue;
            }


            sb.deleteCharAt(i);

            if (needsBrackets(entry.getKey(), entry.getValue())) {
                sb.insert(i, String.format("'%s'", entry.getKey()));
            } else {
                sb.insert(i, entry.getKey());
            }


        }

        return sb;
    }

    static Queue<Map.Entry<String, String>> parseParams(String line) {
        line = StringUtils.removeEnd(line, "\n");

        final String[] strings = StringUtils.splitByWholeSeparator(line, ", ");
        final Queue<Map.Entry<String, String>> queue = new ArrayDeque<>(strings.length);

        for (String s : strings) {
            String value = StringUtils.substringBeforeLast(s, "(");
            String type = StringUtils.substringBetween(s, "(", ")");
            if (StringUtils.isEmpty(type)) {
                queue.offer(new AbstractMap.SimpleEntry<>(value, null));
            } else {
                queue.offer(new AbstractMap.SimpleEntry<>(value, type));
            }
        }

        return queue;
    }

    static Map.Entry<Integer, Map.Entry<String, String>> parseJpaBinding(String line) {
        final Matcher matcher = JPA_BINDING_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        final int index = Integer.parseInt(matcher.group(1));
        final String type = matcher.group(2);
        String value = StringUtils.trimToEmpty(matcher.group(3));
        if (value.startsWith("[") && value.endsWith("]") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }

        if ("null".equalsIgnoreCase(value)) {
            value = "null";
        }

        return new AbstractMap.SimpleEntry<>(index, new AbstractMap.SimpleEntry<>(value, type));
    }

    private static boolean needsBrackets(String value, String type) {
        if (StringUtils.equalsIgnoreCase(value, "null")) {
            return false;
        }

        if (StringUtils.isBlank(type)) {
            final boolean isNumber = value.matches("-?\\d+(\\.\\d+)?");
            final boolean isBoolean = "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            return !isNumber && !isBoolean;
        }

        if (NEED_BRACKETS.contains(type)) {
            return true;
        }

        final String normalizedType = type.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("string") || normalizedType.contains("char") || normalizedType.contains("text")
                || normalizedType.contains("date") || normalizedType.contains("time")
                || normalizedType.contains("timestamp") || normalizedType.contains("json")
                || normalizedType.contains("uuid")) {
            return true;
        }

        return !normalizedType.contains("int") && !normalizedType.contains("long") && !normalizedType.contains("double")
                && !normalizedType.contains("float") && !normalizedType.contains("decimal")
                && !normalizedType.contains("number") && !normalizedType.contains("bool");
    }

}
