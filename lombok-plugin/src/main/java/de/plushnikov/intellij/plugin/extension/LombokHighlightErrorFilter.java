package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.handler.SneakyTrowsExceptionHandler;
import de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodBuilderProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static de.plushnikov.intellij.plugin.extension.LombokCompletionContributor.LombokElementFilter.getCallType;
import static de.plushnikov.intellij.plugin.util.PsiClassUtil.hasParent;

public class LombokHighlightErrorFilter implements HighlightInfoFilter {

  private static final String UNHANDLED_EXCEPTION_PREFIX_TEXT = "Unhandled exception:";
  private static final String UNHANDLED_EXCEPTIONS_PREFIX_TEXT = "Unhandled exceptions:";
  private static final String UNHANDLED_AUTOCLOSABLE_EXCEPTIONS_PREFIX_TEXT = "Unhandled exception from auto-closeable resource:";

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null) return true;
    if (HighlightSeverity.WARNING.equals(highlightInfo.getSeverity()) && CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.equals(highlightInfo.type.getAttributesKey())) {
      return handleFieldDefaultsUnused(highlightInfo, file);
    }
    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {
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

  private boolean handleFieldDefaultsUnused(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
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
    return !isAccessible(field, element);
  }

  public static boolean isAccessible(@NotNull PsiField field, @NotNull PsiElement place) {
    PsiClass contextClass = findContextForPlace(place);                                                                                     // find context
    PsiModifierList modifierList = field.getModifierList();
    PsiAnnotation annotation = findAnnotation(field.getContainingClass(), FieldDefaults.class.getCanonicalName());

    if (!field.hasModifierProperty(PsiModifier.PUBLIC) && !field.hasModifierProperty(PsiModifier.PRIVATE) && !field.hasModifierProperty(PsiModifier.PROTECTED) && annotation != null) {
      String access = LombokProcessorUtil.convertAccessLevelToJavaString(PsiAnnotationUtil.getAnnotationValue(annotation, "level", String.class));

      if (!"".equals(access)) {
        List<String> modifiers = new ArrayList<String>(2){{ add(access); }};
        if (field.hasModifierProperty(PsiModifier.STATIC)) modifiers.add(PsiModifier.STATIC);

        modifierList = new LombokLightModifierList(field.getManager(), field.getLanguage(), modifiers.toArray(new String[modifiers.size()]));
      }
    }

    return JavaResolveUtil.isAccessible(field, field.getContainingClass(), modifierList, place, contextClass, null);
  }

    /**
     * Copy peace from inner IDEA method (JavaCompletionProcessor (constructor))
     */
    private static PsiClass findContextForPlace(PsiElement context) {
      PsiClass contextClass = null;
      PsiElement elementParent = context;
      if (context.getText().contains("IntellijIdeaRulezzz")) elementParent = context.getContext();
      if (elementParent instanceof PsiReferenceExpression) {
        PsiExpression qualifier = ((PsiReferenceExpression) elementParent).getQualifierExpression();
        if (qualifier instanceof PsiSuperExpression) {
          final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression) qualifier).getQualifier();
          if (qSuper == null) {
            contextClass = JavaResolveUtil.getContextClass(context);
          } else {
            final PsiElement target = qSuper.resolve();
            contextClass = target instanceof PsiClass ? (PsiClass) target : null;
          }
        } else if (qualifier != null) {
          contextClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
          if (qualifier.getType() == null && qualifier instanceof PsiJavaCodeReferenceElement) {
            final PsiElement target = ((PsiJavaCodeReferenceElement) qualifier).resolve();
            if (target instanceof PsiClass) {
              contextClass = (PsiClass) target;
            }
          }
        } else {
          contextClass = JavaResolveUtil.getContextClass(context);
        }
      }
      return contextClass;
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
