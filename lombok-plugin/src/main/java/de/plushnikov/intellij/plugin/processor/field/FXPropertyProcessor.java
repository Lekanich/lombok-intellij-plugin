package de.plushnikov.intellij.plugin.processor.field;

import java.util.List;
import java.util.Collection;
import java.lang.annotation.Annotation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.problem.ProblemEmptyBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.AccessLevel;
import lombok.experimental.FXProperty;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Inspect and validate @FXProperty lombok annotation on a field.
 * Creates common accessors for this field, appropriate for JavaFX property.
 *
 * @author Phantom Parakeet
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FXPropertyProcessor extends AbstractFieldProcessor {
	static String READ_ONLY_PROPERTY = "javafx.beans.property.ReadOnlyProperty";
	static String STYLEABLE_PROPERTY = "javafx.css.StyleableProperty";
	static String WRITABLE_VALUE = "javafx.beans.value.WritableValue";
	static String GET_METHOD = "getValue";
	static String PROPERTY = "Property";
	static String ON_PARAM = "onParam";
	static String ON_METHOD = "onMethod";

	public FXPropertyProcessor() {
		super(FXProperty.class, PsiMethod.class);
	}

	protected FXPropertyProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
		super(supportedAnnotationClass, supportedClass);
	}

	@Final @Override
	protected void generatePsiElements(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
		String methodModifier = LombokProcessorUtil.getMethodModifier(psiAnnotation);
		PsiClass psiClass = psiField.getContainingClass();
		PsiType innerType = getPropertyInnerType(psiField);
		if (methodModifier == null || psiClass == null || innerType == null) return;
		if (validateExistingMethods(toGetterFieldName(psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, psiField.getType(), null, toGetterFieldName(psiField)));
		}
		if (validateExistingMethods(toGetterName(psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, innerType, null, toGetterName(psiField)));
		}
		if (validateOnSet(psiField) && validateExistingMethods(toSetterName(psiField), 1, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, determineReturnTypeOnSet(psiField), innerType, toSetterName(psiField)));
		}
	}

	@Final @NotNull
	protected PsiMethod createMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier, @NotNull PsiType returnType, @Nullable PsiType argType, @NotNull String methodName) {
		PsiAnnotation fxPropertyAnnotation = PsiAnnotationUtil.findAnnotation(psiField, FXProperty.class);
		LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiField.getManager(), methodName)
			.withMethodReturnType(returnType)
			.withContainingClass(psiClass)
			.withNavigationElement(psiField);
		if (StringUtil.isNotEmpty(methodModifier)) {
			method.withModifier(methodModifier);
		}
		if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
			method.withModifier(PsiModifier.STATIC);
		}
		if (argType != null) {
			method.withParameter(psiField.getName(), argType);
			PsiModifierList methodParameterModifierList = method.getParameterList().getParameters()[0].getModifierList();
			if (methodParameterModifierList != null) {
				Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField, LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN);
				for (String annotation : annotationsToCopy) {
					methodParameterModifierList.addAnnotation(annotation);
				}
				addOnXAnnotations(fxPropertyAnnotation, methodParameterModifierList, ON_PARAM);
			}
		}
		PsiModifierList modifierList = method.getModifierList();
		copyAnnotations(psiField, modifierList, LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
		addOnXAnnotations(fxPropertyAnnotation, modifierList, ON_METHOD);

		return method;
	}

	@Final @Override
	protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		PsiClass fieldClass = PsiTypesUtil.getPsiClass(psiField.getType());
		Project project = psiField.getProject();
		PsiClass readOnlyClass = JavaPsiFacade.getInstance(project).findClass(READ_ONLY_PROPERTY, GlobalSearchScope.allScope(project));
		PsiClass styleableClass = JavaPsiFacade.getInstance(project).findClass(STYLEABLE_PROPERTY, GlobalSearchScope.allScope(project));
		if (!fieldClass.equals(readOnlyClass) && !fieldClass.equals(styleableClass)
			&& !PsiClassUtil.hasParent(fieldClass, readOnlyClass) && !PsiClassUtil.hasParent(fieldClass, styleableClass)) {
			builder.addError("@FXProperty is not available for type: '%s' ", psiField.getType().getCanonicalText());
			return false;
		}
		validateExistingMethods(toGetterFieldName(psiField), 0, psiField, builder);
		validateExistingMethods(toGetterName(psiField), 0, psiField, builder);
		validateExistingMethods(toSetterName(psiField), 1, psiField, builder);

		return validateAccessorPrefix(psiField, builder);
	}

	@Final
	private boolean validateExistingMethods(@NotNull String methodName, @NotNull Integer paramCount, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		PsiClass psiClass = psiField.getContainingClass();
		if (psiClass == null) return true;
		Collection<PsiMethod> methods = PsiClassUtil.collectClassMethodsIntern(psiField.getContainingClass());
		filterToleratedElements(methods);
		if (PsiMethodUtil.hasSimilarMethod(methods, methodName, paramCount)) {
			builder.addWarning("A method '%s' already exists", methodName);
			return false;
		}
		return true;
	}

	private boolean validateOnSet(@NotNull PsiField psiField) {
		return !psiField.hasModifierProperty(PsiModifier.FINAL)
			&& PsiClassUtil.hasParent(PsiTypesUtil.getPsiClass(psiField.getType()), JavaPsiFacade.getInstance(psiField.getProject()).findClass(WRITABLE_VALUE, GlobalSearchScope.allScope(psiField.getProject())));
	}

	private boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		if (!AccessorsInfo.build(psiField).prefixDefinedAndStartsWith(psiField.getName())) {
			builder.addWarning("Not generating accessors for this field: It does not fit your @Accessors prefix list.");
			return false;
		}
		return true;
	}

	@NotNull
	private PsiSubstitutor getSubstitutor(@NotNull PsiClassType type, @NotNull PsiSubstitutor substitutor) {
		substitutor = substitutor.putAll(type.resolveGenerics().getSubstitutor());
		for (PsiType psiType : type.getSuperTypes()) {
			if (psiType instanceof PsiClassType) substitutor = substitutor.putAll(getSubstitutor((PsiClassType) psiType, substitutor));
		}
		return substitutor;
	}

	/**
	 * Defines a return type for setter.
	 * Returns the type of PsiClass if @Accessors with chain flag is applied.
	 */
	@NotNull
	protected PsiType determineReturnTypeOnSet(@NotNull PsiField psiField) {
		final PsiClass psiClass = psiField.getContainingClass();
		if (psiField.hasModifierProperty(PsiModifier.STATIC) || !AccessorsInfo.build(psiField).isChain() || psiClass == null) return PsiType.VOID;
		return PsiClassUtil.getTypeWithGenerics(psiClass);
	}

	@NotNull @Final @Override
	public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
		PsiClass psiClass = psiField.getContainingClass();
		if (psiClass == null) return LombokPsiElementUsage.NONE;
		Collection<PsiMethod> methods = PsiClassUtil.collectClassMethodsIntern(psiClass);
		if (PsiMethodUtil.hasSimilarMethod(methods, toSetterName(psiField), 1) && PsiMethodUtil.hasSimilarMethod(methods, toGetterName(psiField), 0)) {
			return LombokPsiElementUsage.READ_WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toSetterName(psiField), 1)) {
			return LombokPsiElementUsage.WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toGetterName(psiField), 0) || PsiMethodUtil.hasSimilarMethod(methods, toGetterFieldName(psiField), 0)) {
			return LombokPsiElementUsage.READ;
		}
		return LombokPsiElementUsage.NONE;
	}

	/**
	 * Determines PsiType, returned by getValue() method of JavaFX Property, which was annotated.
	 */
	@Final
	private PsiType getPropertyInnerType(@NotNull PsiField psiField) {
		PsiSubstitutor psiSubstitutor = getSubstitutor((PsiClassType) psiField.getType(), PsiSubstitutor.EMPTY);
		PsiMethod[] psiMethods = PsiTypesUtil.getPsiClass(psiField.getType()).findMethodsByName(GET_METHOD, true);
		for (PsiMethod method : psiMethods) {
			if (method.getParameterList().getParametersCount() == 0 && PsiTypesUtil.getPsiClass(method.getReturnType()).getQualifiedName() != null) {
				return psiSubstitutor.substitute(method.getReturnType());
			}
		}
		return null;
	}

	private String toSetterName(@NotNull PsiField psiField) {
		return LombokUtils.toSetterName(AccessorsInfo.build(psiField), psiField.getName(), false);
	}

	@Final
	private String toGetterName(@NotNull PsiField psiField) {
		PsiType type = getPropertyInnerType(psiField);
		Boolean isBoolean = PsiType.BOOLEAN.equals(type) || PsiType.BOOLEAN.getBoxedType(psiField).equals(type);
		return LombokUtils.toGetterName(AccessorsInfo.build(psiField), psiField.getName(), isBoolean);
	}

	private String toGetterFieldName(@NotNull PsiField psiField) {
		return AccessorsInfo.build(psiField).removePrefix(psiField.getName()) + PROPERTY;
	}
}
