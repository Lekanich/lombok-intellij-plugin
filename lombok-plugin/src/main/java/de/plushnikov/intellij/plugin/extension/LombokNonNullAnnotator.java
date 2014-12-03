package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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

  private static class RemoveNonNullIntentionAction extends BaseIntentionAction {
    private PsiAnnotation annotation;

    public RemoveNonNullIntentionAction(PsiAnnotation annotation) {
      this.annotation = annotation;
    }

    @NotNull @Override public String getText() { return getFamilyName(); }

    @NotNull @Override public String getFamilyName() { return "Remove 'NonNull'"; }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return this.annotation != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      this.annotation.delete();
    }
  }

  @Override
  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {
    if (!(psiElement instanceof PsiAnnotation) || !NonNull.class.getCanonicalName().equals(((PsiAnnotation) psiElement).getQualifiedName())) return;

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class);
    if (parameter == null) return;

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method != null && PsiAnnotationUtil.isAnnotatedWith(method, NonNullArgs.class)) {
      Annotation annotation = holder.createWarningAnnotation(psiElement, MESSAGE_1);
      annotation.registerFix(new RemoveNonNullIntentionAction((PsiAnnotation) psiElement));
    }
  }
}
