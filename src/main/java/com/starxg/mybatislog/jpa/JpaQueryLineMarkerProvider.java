package com.starxg.mybatislog.jpa;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.starxg.mybatislog.Icons;
import com.starxg.mybatislog.MyBatisLogConsoleFilter;
import com.starxg.mybatislog.gui.MyBatisLogManager;

/**
 * Add gutter icon before {@code @Query} and generate SQL preview on click.
 */
public class JpaQueryLineMarkerProvider implements LineMarkerProvider {

    @Nullable
    @Override
    public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof PsiAnnotation)) {
            return null;
        }

        final PsiAnnotation annotation = (PsiAnnotation) element;
        if (!isSupportedAnnotation(annotation)) {
            return null;
        }

        if (PsiTreeUtil.getParentOfType(annotation, PsiMethod.class) == null) {
            return null;
        }

        return new LineMarkerInfo<PsiElement>(
                annotation,
                annotation.getTextRange(),
                Icons.PRETTY_PRINT,
                Pass.LINE_MARKERS,
                psiElement -> StringUtil.notNullize("Generate SQL from @" + shortAnnotationName(annotation)),
                new JpaQueryNavigationHandler(),
                GutterIconRenderer.Alignment.LEFT
        );
    }

    private static boolean isSupportedAnnotation(PsiAnnotation annotation) {
        final String qualifiedName = annotation.getQualifiedName();
        return StringUtils.equals(qualifiedName, "org.springframework.data.jpa.repository.Query")
                || StringUtils.equals(qualifiedName, "jakarta.persistence.Query")
                || StringUtils.equals(qualifiedName, "javax.persistence.Query")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Select")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Insert")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Update")
                || StringUtils.equals(qualifiedName, "org.apache.ibatis.annotations.Delete");
    }

    private static String shortAnnotationName(PsiAnnotation annotation) {
        final String qualifiedName = annotation.getQualifiedName();
        if (StringUtils.isBlank(qualifiedName)) {
            return "Query";
        }
        final int index = qualifiedName.lastIndexOf('.');
        return index < 0 ? qualifiedName : qualifiedName.substring(index + 1);
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        // no-op
    }

    private static final class JpaQueryNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
        @Override
        public void navigate(MouseEvent e, PsiElement elt) {
            if (!(elt instanceof PsiAnnotation)) {
                return;
            }

            final PsiAnnotation annotation = (PsiAnnotation) elt;
            final PsiMethod method = PsiTreeUtil.getParentOfType(elt, PsiMethod.class);
            if (method == null) {
                return;
            }
            final Project project = elt.getProject();

            final JpaQuerySqlGenerator.GeneratedQuerySql generated = JpaQuerySqlGenerator.generate(method, annotation);
            final String sql = generated.getSql();
            if (StringUtils.isBlank(sql)) {
                Messages.showWarningDialog(project, "未能从 @" + generated.getAnnotationName() + " 提取 SQL，请确认 value 为字符串字面量。", "Generate SQL");
                return;
            }

            final MyBatisLogManager manager = ensureManager(project);
            if (manager != null && manager.getToolWindow().isAvailable()) {
                final int color = PropertiesComponent.getInstance(project).getInt(
                        MyBatisLogConsoleFilter.SELECT_SQL_COLOR_KEY,
                        com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT.getAttributes().getForegroundColor().getRGB());
                manager.println("@" + generated.getAnnotationName(), sql, color);
                manager.getToolWindow().activate(null);
            }

            new JpaSqlPreviewDialog(project,
                    "Generate SQL - @" + generated.getAnnotationName(),
                    sql,
                    generated.getMessages()).show();
        }

        private MyBatisLogManager ensureManager(Project project) {
            MyBatisLogManager manager = MyBatisLogManager.getInstance(project);
            if (manager == null) {
                manager = MyBatisLogManager.createInstance(project);
            }
            if (!manager.isRunning()) {
                manager.run();
            }
            return manager;
        }
    }
}
