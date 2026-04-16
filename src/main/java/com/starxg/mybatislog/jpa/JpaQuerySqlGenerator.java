package com.starxg.mybatislog.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

/**
 * Generate preview SQL from {@code @Query} annotation.
 */
public final class JpaQuerySqlGenerator {

    private static final Pattern SPEL_PARAM_PATTERN = Pattern.compile(":#\\{#([A-Za-z0-9_]+)}");
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(?!:)([A-Za-z0-9_]+)");
    private static final Pattern POSITION_PARAM_PATTERN = Pattern.compile("\\?(\\d+)");
    private static final Pattern JAVA_STRING_LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern MYBATIS_HASH_PARAM_PATTERN = Pattern.compile("#\\{\\s*([A-Za-z0-9_\\.]+)(?:,[^}]*)?}");

    private JpaQuerySqlGenerator() {
    }

    public static GeneratedQuerySql generate(PsiMethod method, PsiAnnotation queryAnnotation) {
        final String qualifiedName = StringUtils.defaultString(queryAnnotation.getQualifiedName());
        final boolean isMyBatisCrud = isMyBatisCrudAnnotation(qualifiedName);
        final String annotationName = shortAnnotationName(qualifiedName);

        String query = readStringAttribute(queryAnnotation.findAttributeValue("value"));
        if (StringUtils.isBlank(query)) {
            query = readStringAttribute(queryAnnotation.findAttributeValue("name"));
        }

        if (StringUtils.isBlank(query)) {
            return new GeneratedQuerySql("", Collections.singletonList("`@" + annotationName + "` 未找到可解析的 value 字符串"), false, annotationName);
        }

        final boolean nativeQuery = isMyBatisCrud || readBooleanAttribute(queryAnnotation.findAttributeValue("nativeQuery"));
        final List<String> parameters = collectMethodParameters(method);
        final List<String> orderedParams = new ArrayList<>();

        String sql = beautifySqlPreserveLines(query);
        if (isMyBatisCrud) {
            sql = replaceMyBatisHashParams(sql, orderedParams);
        } else {
            sql = replaceSpel(sql, orderedParams);
            sql = replaceNamedParams(sql, orderedParams);
            sql = replacePositionParams(sql, orderedParams);
        }

        final List<String> messages = new ArrayList<>();
        if (!nativeQuery && !isMyBatisCrud) {
            messages.add("当前为 JPQL/HQL 预览，未连接实体元数据做完整 SQL 方言转换");
        }

        if (!orderedParams.isEmpty()) {
            messages.add("参数顺序: " + String.join(", ", orderedParams));
        } else if (!parameters.isEmpty()) {
            messages.add("方法参数: " + String.join(", ", parameters));
        }

        return new GeneratedQuerySql(sql, messages, nativeQuery, annotationName);
    }

    private static String replaceSpel(String query, List<String> orderedParams) {
        final Matcher matcher = SPEL_PARAM_PATTERN.matcher(query);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            orderedParams.add(matcher.group(1));
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceNamedParams(String query, List<String> orderedParams) {
        final Matcher matcher = NAMED_PARAM_PATTERN.matcher(query);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            orderedParams.add(matcher.group(1));
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replacePositionParams(String query, List<String> orderedParams) {
        final Matcher matcher = POSITION_PARAM_PATTERN.matcher(query);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            orderedParams.add("?" + matcher.group(1));
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceMyBatisHashParams(String query, List<String> orderedParams) {
        final Matcher matcher = MYBATIS_HASH_PARAM_PATTERN.matcher(query);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            orderedParams.add(matcher.group(1));
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static List<String> collectMethodParameters(PsiMethod method) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 0) {
            return Collections.emptyList();
        }
        final List<String> names = new ArrayList<>(parameters.length);
        for (PsiParameter parameter : parameters) {
            names.add(parameter.getName());
        }
        return names;
    }

    private static boolean readBooleanAttribute(PsiAnnotationMemberValue value) {
        if (value == null) {
            return false;
        }

        final String text = StringUtils.trimToEmpty(value.getText());
        return "true".equalsIgnoreCase(text);
    }

    private static String readStringAttribute(PsiAnnotationMemberValue value) {
        if (value == null) {
            return null;
        }

        if (value instanceof PsiLiteralExpression) {
            final Object val = ((PsiLiteralExpression) value).getValue();
            return val instanceof String ? (String) val : null;
        }

        final String sourceStyleText = parseStringBySourceLayout(value.getText());
        if (StringUtils.isNotBlank(sourceStyleText)) {
            return sourceStyleText;
        }

        if (value instanceof PsiExpression) {
            final Object constantValue = JavaPsiFacade.getInstance(value.getProject()).getConstantEvaluationHelper()
                    .computeConstantExpression((PsiExpression) value);
            if (constantValue instanceof String) {
                return (String) constantValue;
            }
        }

        final String text = StringUtils.trimToEmpty(value.getText());
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }

        return null;
    }

    private static String parseStringBySourceLayout(String sourceText) {
        final Matcher matcher = JAVA_STRING_LITERAL_PATTERN.matcher(StringUtils.defaultString(sourceText));
        final List<String> segments = new ArrayList<>();
        while (matcher.find()) {
            segments.add(StringUtil.unescapeStringCharacters(matcher.group(1)));
        }
        if (segments.isEmpty()) {
            return null;
        }
        return String.join("\n", segments);
    }

    private static String beautifySqlPreserveLines(String sql) {
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

    private static boolean isMyBatisCrudAnnotation(String qualifiedName) {
        return StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Select")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Insert")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Update")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Delete");
    }

    private static String shortAnnotationName(String qualifiedName) {
        if (StringUtils.isBlank(qualifiedName)) {
            return "Query";
        }
        final int idx = qualifiedName.lastIndexOf('.');
        return idx >= 0 ? qualifiedName.substring(idx + 1) : qualifiedName;
    }

    public static final class GeneratedQuerySql {
        private final String sql;
        private final List<String> messages;
        private final boolean nativeQuery;
        private final String annotationName;

        public GeneratedQuerySql(String sql, List<String> messages, boolean nativeQuery, String annotationName) {
            this.sql = sql;
            this.messages = messages;
            this.nativeQuery = nativeQuery;
            this.annotationName = annotationName;
        }

        public String getSql() {
            return sql;
        }

        public List<String> getMessages() {
            return messages;
        }

        public boolean isNativeQuery() {
            return nativeQuery;
        }

        public String getAnnotationName() {
            return annotationName;
        }
    }
}
