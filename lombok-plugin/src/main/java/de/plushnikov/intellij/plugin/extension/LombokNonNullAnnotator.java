package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.action.RemoveIntentionAction;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.NonNull;
import lombok.experimental.NonNullArgs;
import org.jetbrains.annotations.NotNull;


/**
 * @author Suburban Squirrel
 * @version 1.0.38
 * @since 1.0.38
 */
final public class LombokNonNullAnnotator implements Annotator {
  private static final String MESSAGE_1 = "NonNullArgs made same thing.";

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof PsiAnnotation) || !NonNull.class.getCanonicalName().equals(((PsiAnnotation) psiElement).getQualifiedName())) return;

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class);
    if (parameter == null) return;

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method != null && PsiAnnotationUtil.isAnnotatedWith(method, NonNullArgs.class)) {
      Annotation annotation = holder.createWarningAnnotation(psiElement, MESSAGE_1);
      annotation.registerFix(new RemoveIntentionAction(psiElement));
    }
  }
}
