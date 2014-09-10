package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static de.plushnikov.intellij.plugin.extension.LombokHighlightErrorFilter.isAccessible;

/**
 * @author Suburban Squirrel
 * @version 1.0.25
 * @since 1.0.25
 */
final public class LombokErrorAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof PsiReferenceExpression)) return;
    PsiClass containingClass = getContainingClass(element);

    if (containingClass == null) return;
    PsiReferenceExpression expression = (PsiReferenceExpression) element;
    PsiElement psiElement = expression.resolve();

    if (psiElement == null || !(psiElement instanceof PsiField)) return;

    PsiField field = (PsiField) psiElement;
    if (field.hasModifierProperty(PsiModifier.PUBLIC) || field.hasModifierProperty(PsiModifier.PROTECTED) || field.hasModifierProperty(PsiModifier.PRIVATE)) return;
    if (isAccessible(field, element)) return;

    PsiElement errorElement = PsiTreeUtil.getChildOfType(expression, PsiIdentifier.class);
    if (errorElement == null) errorElement = expression;

  // error for unresolved fields
    holder.createErrorAnnotation(errorElement, "can't resolve (@FD)").setTextAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  }
}
