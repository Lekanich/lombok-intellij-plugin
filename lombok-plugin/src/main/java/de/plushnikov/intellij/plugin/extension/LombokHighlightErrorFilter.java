package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.handler.LazyGetterHandler;
import de.plushnikov.intellij.plugin.handler.SneakyThrowsExceptionHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static de.plushnikov.intellij.plugin.extension.LombokCompletionContributor.LombokElementFilter.getCallType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtendingMethods;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.FieldDefaultsUtil.isAccessible;
import static de.plushnikov.intellij.plugin.util.PsiClassUtil.hasParent;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null) return true;
    if (HighlightSeverity.WARNING.equals(highlightInfo.getSeverity()) && CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.equals(highlightInfo.type.getAttributesKey())) {
      return isUnusedFieldDefaultsField(highlightInfo, file);
    }

    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      final String description = StringUtil.notNullize(highlightInfo.getDescription());

      // Handling LazyGetter
      if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
        return false;
      }
      if (HighlightInfoType.WRONG_REF.equals(highlightInfo.type)) {
        return isUnresolvedMethodExtensionPrimitive(highlightInfo, file) && isInaccessibleFieldDefaultsField(highlightInfo, file);
      }
    }
    return true;
  }

  private boolean isUnusedFieldDefaultsField(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;

    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;

    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) return true;

    PsiAnnotation annotation = PsiAnnotationUtil.findAnnotation(containingClass, FieldDefaults.class);
    if (annotation == null || field.hasModifierProperty(PsiModifier.PUBLIC) || field.hasModifierProperty(PsiModifier.PROTECTED) || field.hasModifierProperty(PsiModifier.PRIVATE)) return true;

    Query<PsiReference> search = ReferencesSearch.search(field, field.getResolveScope(), true);
    boolean isUses = search.iterator().hasNext();                                                 // find some uses
    if (!isUses) return true;                                                                     // remain info

    String level = PsiAnnotationUtil.getAnnotationValue(annotation, "level", String.class);
    if (level == null) return true;

    AccessLevel accessLevel = AccessLevel.valueOf(level);
    if (accessLevel == AccessLevel.PUBLIC) return false;                                // remove this info

    if (accessLevel == AccessLevel.PROTECTED) {
      for (PsiReference reference : search) {
        PsiClass classWithUsed = PsiTreeUtil.getParentOfType(reference.getElement(), PsiClass.class);
        if (classWithUsed == null) continue;
        if (hasParent(classWithUsed, containingClass)) return false;
      }
    }

    if (accessLevel == AccessLevel.PRIVATE) {
      for (PsiReference reference : search) {
        PsiClass classWithUsed = PsiTreeUtil.getParentOfType(reference.getElement(), PsiClass.class);
        if (containingClass.equals(classWithUsed)) return false;
      }
    }

    return true;                                  // also if accessLevel == AccessLevel.PACKAGE
  }

  private boolean isInaccessibleFieldDefaultsField(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;
    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;
    PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    if (expression == null) return true;
    PsiElement psiElement = expression.resolve();
    if (psiElement == null || !(psiElement instanceof PsiField)) return true;

    PsiField field = (PsiField) psiElement;
    return !isAccessible(field, element);
  }

  /**
   * remove highlight error of extension method for primitive type
   */
  private boolean isUnresolvedMethodExtensionPrimitive(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;
    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;
    PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    if (expression == null) return true;

    PsiType callType = getCallType(expression, containingClass);
    if (!ClassUtils.isPrimitive(callType)) return true;

    for (PsiMethod method : getExtendingMethods(containingClass)) {
      if (!method.getName().equals(element.getText())) continue;
      if (getType(method.getParameterList().getParameters()[0].getType(), method).isAssignableFrom(callType)) return false;                 // remove exception highlight
    }
    return true;
  }

  private boolean uninitializedField(String description) {
    Matcher matcher = UNINITIALIZED_MESSAGE.matcher(description);
    return matcher.matches();
  }
}
