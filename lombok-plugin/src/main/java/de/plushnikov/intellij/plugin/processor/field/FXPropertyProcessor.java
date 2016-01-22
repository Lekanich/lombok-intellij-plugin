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
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.value.WritableValue;
import javafx.css.StyleableProperty;
import lombok.AccessLevel;
import lombok.experimental.FXProperty;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import lombok.experimental.NonFinal;
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
	static String GET_METHOD = "getValue";
	static String PROPERTY = "Property";
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
		PsiType fieldType = psiField.getType();

		if (methodModifier == null || psiClass == null || innerType == null) return;

		if (validateExistingMethods(toGetterName(innerType, psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, innerType, null, toGetterName(innerType, psiField)));
		}

		if (validateExistingMethods(toGetterName(fieldType, psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, fieldType, null, toGetterName(fieldType, psiField)));
		}

		if (validateOnSet(psiField) && validateExistingMethods(toSetterName(psiField), 1, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, determineReturnTypeOnSet(psiField), innerType, toSetterName(psiField)));
		}
	}

	@Final @NotNull
	protected PsiMethod createMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier, @NotNull PsiType returnType, @Nullable PsiType argType, @NotNull String methodName) {
		LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiField.getManager(), methodName)
			.withMethodReturnType(returnType)
			.withContainingClass(psiClass)
			.withNavigationElement(psiField);

		if (argType != null) {
			method.withParameter(psiField.getName(), argType);
		}

		if (StringUtil.isNotEmpty(methodModifier)) {
			method.withModifier(methodModifier);
		}

		if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
			method.withModifier(PsiModifier.STATIC);
		}

		PsiModifierList modifierList = method.getModifierList();
		copyAnnotations(psiField, modifierList, LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
		addOnXAnnotations(PsiAnnotationUtil.findAnnotation(psiField, FXProperty.class), modifierList, ON_METHOD);

		return method;
	}

	@Final @Override
	protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		if (!isProperty(psiField.getType(), psiField.getProject())) {
			builder.addError("@FXProperty is not available for type: '%s' ", psiField.getType().getCanonicalText());
			return false;
		}

		PsiType innerType = getPropertyInnerType(psiField);
		if (innerType == null) return false;

		validateExistingMethods(toGetterName(innerType, psiField), 0, psiField, builder);
		validateExistingMethods(toGetterName(psiField.getType(), psiField), 0, psiField, builder);
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

	@Final
	private boolean validateOnSet(@NotNull PsiField psiField) {
		PsiClass psiClass = PsiTypesUtil.getPsiClass(psiField.getType());
		PsiClass writableClass = JavaPsiFacade.getInstance(psiField.getProject()).findClass(WritableValue.class.getName(), GlobalSearchScope.allScope(psiField.getProject()));
		return psiClass != null && writableClass != null && !psiField.hasModifierProperty(PsiModifier.FINAL) && PsiClassUtil.hasParent(psiClass, writableClass);
	}

	private boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		if (!AccessorsInfo.build(psiField).prefixDefinedAndStartsWith(psiField.getName())) {
			builder.addWarning("Not generating accessors for this field: It does not fit your @Accessors prefix list.");
			return false;
		}
		return true;
	}

	@Final
	private boolean isProperty(@NotNull PsiType type, Project project) {
		PsiClass fieldClass = PsiTypesUtil.getPsiClass(type);
		PsiClass readOnlyClass = JavaPsiFacade.getInstance(project).findClass(ReadOnlyProperty.class.getName(), GlobalSearchScope.allScope(project));
		PsiClass styleableClass = JavaPsiFacade.getInstance(project).findClass(StyleableProperty.class.getName(), GlobalSearchScope.allScope(project));

		return fieldClass != null && readOnlyClass != null && styleableClass != null
			&& (fieldClass.isEquivalentTo(readOnlyClass) || fieldClass.isEquivalentTo(styleableClass)
			|| PsiClassUtil.hasParent(fieldClass, readOnlyClass) || PsiClassUtil.hasParent(fieldClass, styleableClass));
	}

	@NotNull
	private PsiSubstitutor createSubstitutor(@NotNull PsiClassType type, @NotNull PsiSubstitutor substitutor) {
		substitutor = substitutor.putAll(type.resolveGenerics().getSubstitutor());
		for (PsiType psiType : type.getSuperTypes()) {
			if (psiType instanceof PsiClassType) substitutor = substitutor.putAll(createSubstitutor((PsiClassType) psiType, substitutor));
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
		PsiType innerType = getPropertyInnerType(psiField);
		if (psiClass == null) return LombokPsiElementUsage.NONE;

		Collection<PsiMethod> methods = PsiClassUtil.collectClassMethodsIntern(psiClass);
		if (PsiMethodUtil.hasSimilarMethod(methods, toSetterName(psiField), 1) && PsiMethodUtil.hasSimilarMethod(methods, toGetterName(psiField.getType(), psiField), 0)) {
			return LombokPsiElementUsage.READ_WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toSetterName(psiField), 1)) {
			return LombokPsiElementUsage.WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toGetterName(psiField.getType(), psiField), 0) || (innerType != null && PsiMethodUtil.hasSimilarMethod(methods, toGetterName(innerType, psiField), 0))) {
			return LombokPsiElementUsage.READ;
		}

		return LombokPsiElementUsage.NONE;
	}

	/**
	 * Determines PsiType, returned by getValue() method of JavaFX Property, which was annotated.
	 */
	@Final
	private PsiType getPropertyInnerType(@NotNull PsiField psiField) {
		PsiSubstitutor psiSubstitutor = createSubstitutor((PsiClassType) psiField.getType(), PsiSubstitutor.EMPTY);
		PsiClass psiClass = PsiTypesUtil.getPsiClass(psiField.getType());
		if (psiClass == null) return null;

		@NonFinal PsiType type = null;
		PsiMethod[] psiMethods = psiClass.findMethodsByName(GET_METHOD, true);

		for (PsiMethod method : psiMethods) {
			if (method.getParameterList().getParametersCount() != 0) continue;
			type = method.getReturnType();
			PsiClass methReturnClass = PsiTypesUtil.getPsiClass(type);
			if (methReturnClass != null && methReturnClass.getQualifiedName() != null) return psiSubstitutor.substitute(type);
		}

		return type;
	}

	private String toSetterName(@NotNull PsiField psiField) {
		return LombokUtils.toSetterName(AccessorsInfo.build(psiField), psiField.getName(), false);
	}

	private String toGetterName(@NotNull PsiType type, PsiField psiField) {
		if (isProperty(type, psiField.getProject())) return AccessorsInfo.build(psiField).removePrefix(psiField.getName()) + PROPERTY;
		final PsiClassType psiClassType = PsiType.BOOLEAN.getBoxedType(psiField);
		return LombokUtils.toGetterName(AccessorsInfo.build(psiField), psiField.getName(), psiClassType != null && psiClassType.equals(type));
	}
}
