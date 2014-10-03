package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import lombok.experimental.FieldDefaults;
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

  public static boolean isFinal(@NotNull PsiField psiField) {
    if (psiField.hasModifierProperty(PsiModifier.FINAL)) return true;
    PsiClass containingClass = psiField.getContainingClass();
    if (containingClass == null) return false;

    if (isAnnotatedWith(psiField, NonFinal.class)) return false;

    PsiAnnotation annotation = findAnnotation(containingClass, FieldDefaults.class);
    if (annotation == null) return false;

    Boolean makeFinal = getAnnotationValue(annotation, "makeFinal", Boolean.class);
    return makeFinal != null ? makeFinal : psiField.hasModifierProperty(PsiModifier.FINAL);       // if couldn't find annotation value get really final modifier of field
  }
}
