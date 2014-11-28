package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import lombok.experimental.FinalArgs;
import org.jetbrains.annotations.NotNull;

/**
 * @author Suburban Squirrel
 * @version 1.0.36
 * @since 1.0.36
 */
public class LombokFinalAnnotator implements Annotator {
  private static final String MESSAGE_1 = "Final contains functionality of FinalArgs.";
  private static final String MESSAGE_2 = "%s can make final this variable without 'final' modifier";
  private static final String MESSAGE_3 = "FieldDefaults(makeFinal = true) can make final this field without 'final' modifier.";
  private AnnotationHolder holder;
  private PsiKeyword keyword;

  private static class RemoveFinalIntentionAction extends BaseIntentionAction {
    private PsiKeyword keyword;

    public RemoveFinalIntentionAction(PsiKeyword keyword) {
      this.keyword = keyword;
    }

    @NotNull @Override public String getText() { return getFamilyName(); }

    @NotNull @Override public String getFamilyName() { return "Remove 'final' modifier"; }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return this.keyword != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      this.keyword.delete();
    }
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!(element instanceof PsiKeyword) || !PsiKeyword.FINAL.equals(element.getText())) return;
    this.keyword = (PsiKeyword) element;
    this.holder = holder;

    PsiMethod parentMethod = PsiTreeUtil.getParentOfType(keyword, PsiMethod.class);
    PsiField parentField = PsiTreeUtil.getParentOfType(keyword, PsiField.class);

    if (parentMethod != null && parentField == null) handleLocalVariable(parentMethod);
    if (parentMethod == null && parentField != null) handleGlobalVariable(parentField);
  }

  public void handleLocalVariable(@NotNull final PsiMethod psiMethod) {
    PsiAnnotation finalAnnotation = PsiAnnotationUtil.findAnnotation(psiMethod, Final.class);
    PsiAnnotation finalArgsAnnotation = PsiAnnotationUtil.findAnnotation(psiMethod, FinalArgs.class);

    if (finalAnnotation != null && finalArgsAnnotation != null) holder.createWarningAnnotation(finalArgsAnnotation, MESSAGE_1).registerFix(new RemoveFinalIntentionAction(keyword));

    if (finalArgsAnnotation != null && PsiTreeUtil.getParentOfType(keyword, PsiParameter.class) != null) {
       holder.createWarningAnnotation(keyword, String.format(MESSAGE_2, finalArgsAnnotation.getQualifiedName())).registerFix(new RemoveFinalIntentionAction(keyword));
    }
    if (finalAnnotation != null) {
      holder.createWarningAnnotation(keyword, String.format(MESSAGE_2, finalAnnotation.getQualifiedName())).registerFix(new RemoveFinalIntentionAction(keyword));
    }
  }

  public void handleGlobalVariable(@NotNull final PsiField psiField) {
    PsiClass containingClass = psiField.getContainingClass();
    if (containingClass == null) return;

    PsiAnnotation annotation = PsiAnnotationUtil.findAnnotation(containingClass, FieldDefaults.class);
    if (annotation == null) return;

    Boolean makeFinal = PsiAnnotationUtil.getAnnotationValue(annotation, "makeFinal", Boolean.class);
    if (makeFinal) holder.createWarningAnnotation(keyword, MESSAGE_3).registerFix(new RemoveFinalIntentionAction(keyword));
  }
}
