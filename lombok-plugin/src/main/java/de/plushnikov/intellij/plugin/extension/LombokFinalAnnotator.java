package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.action.RemoveIntentionAction;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
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

    if (finalAnnotation != null && finalArgsAnnotation != null) holder.createWarningAnnotation(finalArgsAnnotation, MESSAGE_1).registerFix(new RemoveIntentionAction(keyword));

    PsiVariable variable = PsiTreeUtil.getParentOfType(keyword, PsiVariable.class);
    if (variable != null && PsiFieldUtil.isFinalByAnnotation(variable)) {
      if (finalArgsAnnotation != null) holder.createWarningAnnotation(keyword, String.format(MESSAGE_2, finalArgsAnnotation.getQualifiedName())).registerFix(new RemoveIntentionAction(keyword));
      if (finalAnnotation != null) holder.createWarningAnnotation(keyword, String.format(MESSAGE_2, finalAnnotation.getQualifiedName())).registerFix(new RemoveIntentionAction(keyword));
    }
  }

  public void handleGlobalVariable(@NotNull final PsiField psiField) {
    if (PsiFieldUtil.isFinalByAnnotation(psiField)) holder.createWarningAnnotation(keyword, MESSAGE_3).registerFix(new RemoveIntentionAction(keyword));
  }
}
