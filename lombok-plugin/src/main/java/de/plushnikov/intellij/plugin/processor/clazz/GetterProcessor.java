package de.plushnikov.intellij.plugin.processor.clazz;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.processor.field.GetterFieldProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Inspect and validate @Getter lombok annotation on a class
 * Creates getter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public class GetterProcessor extends AbstractClassProcessor {

  private final GetterFieldProcessor fieldProcessor = new GetterFieldProcessor();

  public GetterProcessor() {
    super(Getter.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRightType(psiClass, builder) && validateVisibility(psiAnnotation);

    if (PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "lazy", false)) {
      builder.addWarning("'lazy' is not supported for @Getter on a type");
    }

    return result;
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("'@Getter' is only supported on a class, enum or field type");
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldGetters(psiClass, methodVisibility));
    }
  }

  @NotNull
  public Collection<PsiMethod> createFieldGetters(@NotNull PsiClass psiClass, @NotNull String methodModifier) {
    Collection<PsiMethod> result = new ArrayList<PsiMethod>();
    final Collection<PsiField> getterFields = filterGetterFields(psiClass);
    for (PsiField getterField : getterFields) {
      result.add(fieldProcessor.createGetterMethod(getterField, psiClass, methodModifier));
    }
    return result;
  }

	@NotNull
	protected Collection<PsiField> filterGetterFields(@NotNull PsiClass psiClass) {
		final Collection<PsiField> getterFields = new ArrayList<PsiField>();

		final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
		filterToleratedElements(classMethods);

		for (PsiField psiField : psiClass.getFields()) {
			boolean createGetter = true;
			PsiModifierList modifierList = psiField.getModifierList();
			if (null != modifierList) {
				//Skip static fields.
				createGetter = !modifierList.hasModifierProperty(PsiModifier.STATIC);
				//Skip fields having Getter annotation already
				createGetter &= !hasFieldProcessorAnnotation(modifierList);
				//Skip fields that start with $
				createGetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
				//Skip fields if a method with same name and arguments count already exists
				final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
				final Collection<String> methodNames = LombokUtils.toAllGetterNames(accessorsInfo, psiField.getName(), PsiType.BOOLEAN.equals(psiField.getType()));
				for (String methodName : methodNames) {
					createGetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0);
				}
			}

			if (createGetter) {
				getterFields.add(psiField);
			}
		}
		return getterFields;
	}

  private boolean hasFieldProcessorAnnotation(PsiModifierList modifierList) {
    boolean hasSetterAnnotation = false;
    for (PsiAnnotation fieldAnnotation : modifierList.getAnnotations()) {
      hasSetterAnnotation |= fieldProcessor.acceptAnnotation(fieldAnnotation, PsiMethod.class);
    }
    return hasSetterAnnotation;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterGetterFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.READ;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
