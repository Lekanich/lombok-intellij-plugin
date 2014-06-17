package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.handler.SneakyTrowsExceptionHandler;
import de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodBuilderProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodProcessor;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.PackagePrivate;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static de.plushnikov.intellij.plugin.extension.LombokCompletionContributor.LombokElementFilter.getCallType;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final String UNHANDLED_EXCEPTION_PREFIX_TEXT = "Unhandled exception:";
  private static final String UNHANDLED_EXCEPTIONS_PREFIX_TEXT = "Unhandled exceptions:";
  private static final String UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT = "Unhandled exception from auto-closeable resource:";

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (null != file && HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
      final String description = StringUtil.notNullize(highlightInfo.getDescription());

      if (HighlightInfoType.UNHANDLED_EXCEPTION.equals(highlightInfo.type) &&
          (StringUtil.startsWith(description, UNHANDLED_EXCEPTION_PREFIX_TEXT) ||
              StringUtil.startsWith(description, UNHANDLED_EXCEPTIONS_PREFIX_TEXT) ||
              StringUtil.startsWith(description, UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT))) {
        final String unhandledExceptions = description.substring(description.indexOf(':') + 1).trim();
        final String[] exceptionFQNs = unhandledExceptions.split(",");
        if (exceptionFQNs.length > 0) {
          final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(file.findElementAt(highlightInfo.getStartOffset()), PsiMethod.class);
          if (null != psiMethod) {
            return !SneakyTrowsExceptionHandler.isExceptionHandled(psiMethod, exceptionFQNs);
          }
        }
      }
// handle lombok.val
//      if (HighlightInfoType.ERROR.equals(highlightInfo.type)) {
//        return handleValException(highlightInfo, file);
//      }
      if (HighlightInfoType.WRONG_REF.equals(highlightInfo.type)) {
        return handleExtensionPrimitiveMethodException(highlightInfo, file) && handleFieldDefaultsFieldAccess(highlightInfo, file);
      }
    }
    return true;
  }

  private boolean handleValException(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;
    if (PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class) == null) return true;

    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;
    PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
    if (typeElement == null) return true;
    return !val.class.getCanonicalName().equals(typeElement.getType().getCanonicalText());
  }

  private boolean handleFieldDefaultsFieldAccess(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;
    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;
    PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    if (expression == null) return true;
    PsiElement psiElement = expression.resolve();
    if (psiElement == null || !(psiElement instanceof PsiField)) return true;

    PsiField field = (PsiField) psiElement;
    PsiClass classContainingField = field.getContainingClass();
    if (field.getModifierList().hasModifierProperty(PsiModifier.PRIVATE) || field.getModifierList().hasModifierProperty(PsiModifier.PROTECTED)) return true;    // obviously marked

    PsiAnnotation annotation = findAnnotation(classContainingField, FieldDefaults.class.getCanonicalName());
    if (annotation == null) return true;                                                                                                                        // can't find annotation

    PsiAnnotationMemberValue attrValue = annotation.findAttributeValue("level");
    String accessLevelText = ((PsiReferenceExpressionImpl) attrValue).getCanonicalText();
    int lastDotIndex = accessLevelText.lastIndexOf(".");
    if (lastDotIndex != -1) {
      accessLevelText = accessLevelText.substring(lastDotIndex + 1);
    }
    AccessLevel accessLevel = AccessLevel.valueOf(accessLevelText);                                                 // get access level

  // check PackagePrivate annotation before accessLevel attribute
    PsiAnnotation packagePrivate = findAnnotation(field, PackagePrivate.class.getCanonicalName());
    if ((packagePrivate != null || accessLevel == AccessLevel.PACKAGE || accessLevel == AccessLevel.PROTECTED)
        && ((PsiJavaFile) field.getContainingFile()).getPackageName().equals(((PsiJavaFile) file).getPackageName())) return false;                              // same package

    if (accessLevel == AccessLevel.NONE || accessLevel == AccessLevel.PRIVATE) return true;
    if (accessLevel == AccessLevel.PUBLIC) return false;
    PsiMethod methodParentOfType = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (classContainingField == null) return true;
    if (accessLevel == AccessLevel.PROTECTED && PsiClassUtil.hasParent(containingClass, classContainingField)) return methodParentOfType != null && methodParentOfType.hasModifierProperty(PsiModifier.STATIC);

    if (field.getContainingFile().getName().equals(file.getName())) return false;
    return true;
  }

  /**
   * remove highlight error of extension method for primitive type
   */
  private boolean handleExtensionPrimitiveMethodException(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (element == null) return true;
    PsiClass containingClass = getContainingClass(element);
    if (containingClass == null) return true;
    PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    if (expression == null) return true;

    PsiType callType = getCallType(expression, containingClass);
    if (!ClassUtils.isPrimitive(callType)) return true;

    for (PsiMethod method : ExtensionMethodProcessor.getExtendingMethods(containingClass)) {
      if (!method.getName().equals(element.getText())) continue;
      if (ExtensionMethodBuilderProcessor.getType(method.getParameterList().getParameters()[0].getType(), method).isAssignableFrom(callType)) return false;                 // remove exception highlight
    }
    return true;
  }
}
