package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import lombok.experimental.FinalArgs;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.getAnnotationValue;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.isAnnotatedWith;

/**
 * @author Plushnikov Michail
 */
public class PsiFieldUtil {
  @NotNull
  public static Collection<PsiField> filterFieldsByModifiers(@NotNull PsiField[] psiFields, String... modifiers) {
    Collection<PsiField> filterdFields = new ArrayList<PsiField>(psiFields.length);
    for (PsiField psiField : psiFields) {
      boolean addField = true;

      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        for (String modifier : modifiers) {
          addField &= !modifierList.hasModifierProperty(modifier);
        }
      }

      if (addField) {
        filterdFields.add(psiField);
      }
    }
    return filterdFields;
  }

  public static boolean isFinal(@NotNull PsiVariable variable) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) return true;

    if (isAnnotatedWith(variable, NonFinal.class)) return false;

  // check Final.
    PsiElement parent = variable.getParent();
    PsiMethod method = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
    if (method != null && (parent instanceof PsiParameterList || parent instanceof PsiDeclarationStatement)
        && isAnnotatedWith(method, Final.class) && !(parent.getParent() instanceof PsiLambdaExpression)) return true;
    if (method != null && parent instanceof PsiParameterList && isAnnotatedWith(method, FinalArgs.class) && !(parent.getParent() instanceof PsiLambdaExpression)) return true;

    if (!(variable instanceof PsiField)) return false;

    PsiClass containingClass = ((PsiField)variable).getContainingClass();
    if (containingClass == null) return false;

    PsiAnnotation annotation = findAnnotation(containingClass, FieldDefaults.class);
    if (annotation == null) return false;

    Boolean makeFinal = getAnnotationValue(annotation, "makeFinal", Boolean.class);
    return makeFinal != null ? makeFinal : variable.hasModifierProperty(PsiModifier.FINAL);       // if couldn't find annotation value get really final modifier of field
  }
}
