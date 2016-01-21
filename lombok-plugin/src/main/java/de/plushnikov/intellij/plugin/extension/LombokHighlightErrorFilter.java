package de.plushnikov.intellij.plugin.extension;

import java.util.regex.Pattern;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
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
import de.plushnikov.intellij.plugin.handler.OnXAnnotationHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.AccessLevel;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static com.intellij.codeInsight.completion.LombokCompletionContributor.LombokElementFilter.getCallType;
import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtendingMethods;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.FieldDefaultsUtil.isAccessible;
import static de.plushnikov.intellij.plugin.util.PsiClassUtil.hasParent;


public class LombokHighlightErrorFilter implements HighlightInfoFilter {
  private static final Pattern UNINITIALIZED_MESSAGE = Pattern.compile("Variable '.+' might not have been initialized");
  private static final Pattern LOMBOK_ANYANNOTATIONREQUIRED = Pattern.compile("Incompatible types\\. Found: '__*', required: 'lombok.*AnyAnnotation\\[\\]'");

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null) return true;
    if (HighlightSeverity.WARNING.equals(highlightInfo.getSeverity()) && CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES.equals(highlightInfo.type.getAttributesKey())) {
      return isUnusedFieldDefaultsField(highlightInfo, file);
    }

    if (HighlightSeverity.ERROR.equals(highlightInfo.getSeverity())) {

      String description = StringUtil.notNullize(highlightInfo.getDescription());

      // Handling LazyGetter
      if (uninitializedField(description) && LazyGetterHandler.isLazyGetterHandled(highlightInfo, file)) {
        return false;
      }

      //Handling onX parameters
      if (OnXAnnotationHandler.isOnXParameterAnnotation(highlightInfo, file)
          || OnXAnnotationHandler.isOnXParameterValue(highlightInfo, file)
          || LOMBOK_ANYANNOTATIONREQUIRED.matcher(description).matches()) {
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

	// explicit modifier
		if (field.hasModifierProperty(PsiModifier.PUBLIC) || field.hasModifierProperty(PsiModifier.PROTECTED) || field.hasModifierProperty(PsiModifier.PRIVATE)) return true;

	// find usages of field
		Query<PsiReference> search = ReferencesSearch.search(field, field.getResolveScope(), true);
		boolean isUses = search.iterator().hasNext();                                                 // find some uses
		if (!isUses) return true;                                                                     // remain info

	// get implicit access level
		AccessLevel accessLevel = null;
		PsiAnnotation valueAnnotation = PsiAnnotationUtil.findAnnotation(containingClass, Value.class);
		if (valueAnnotation != null) {
			accessLevel = AccessLevel.PRIVATE;
		}

		PsiAnnotation annotation = PsiAnnotationUtil.findAnnotation(containingClass, FieldDefaults.class);
		if (annotation != null) {
			String level = PsiAnnotationUtil.getStringAnnotationValue(annotation, "level");
			if (level == null) return true;

			accessLevel = AccessLevel.valueOf(level);
		}

	// not found implicit access lvl from annotations
		if (accessLevel == null) return true;

		switch (accessLevel) {
			case PUBLIC: return false;														// remove info about unused

			case PRIVATE:
				for (PsiReference reference : search) {
					PsiClass classWithUsed = PsiTreeUtil.getParentOfType(reference.getElement(), PsiClass.class);
					if (containingClass.equals(classWithUsed)) return false;
				}
				break;

			case PROTECTED:
				for (PsiReference reference : search) {
					PsiClass classWithUsed = PsiTreeUtil.getParentOfType(reference.getElement(), PsiClass.class);
					if (classWithUsed == null) continue;

					if (hasParent(classWithUsed, containingClass)) return false;
				}
				break;
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
    return UNINITIALIZED_MESSAGE.matcher(description).matches();
  }
}
