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

	// adds getter for the inner value of the property to the list of PsiElements of the class
		if (validateExistingMethods(toGetter(psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, innerType, null, toGetter(psiField)));
		}

	// adds accessor for the property to the list of PsiElements of the class
		if (validateExistingMethods(toPropertyAccessor(psiField), 0, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, fieldType, null, toPropertyAccessor(psiField)));
		}

	// adds setter for the inner value of the property to the list of PsiElements of the class
		if (validateOnSet(psiField) && validateExistingMethods(toSetter(psiField), 1, psiField, ProblemEmptyBuilder.getInstance())) {
			target.add(createMethod(psiField, psiClass, methodModifier, determineReturnTypeOnSet(psiField), innerType, toSetter(psiField)));
		}
	}

	@Final @NotNull
	protected PsiMethod createMethod(@NotNull PsiField psiField, @NotNull PsiClass psiClass, @NotNull String methodModifier, @NotNull PsiType returnType, @Nullable PsiType argType, @NotNull String methodName) {
	// creates PsiMethod
		LombokLightMethodBuilder method = new LombokLightMethodBuilder(psiField.getManager(), methodName)
			.withMethodReturnType(returnType)
			.withContainingClass(psiClass)
			.withNavigationElement(psiField);

	// adds method argument
		if (argType != null) {
			method.withParameter(psiField.getName(), argType);
		}

	// adds modifiers
		if (StringUtil.isNotEmpty(methodModifier)) {
			method.withModifier(methodModifier);
		}

	// adds static modifier
		if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
			method.withModifier(PsiModifier.STATIC);
		}

	// copies annotations of psiField to modifierList of method
		PsiModifierList modifierList = method.getModifierList();
		copyAnnotations(psiField, modifierList, LombokUtils.NON_NULL_PATTERN, LombokUtils.NULLABLE_PATTERN, LombokUtils.DEPRECATED_PATTERN);
		addOnXAnnotations(PsiAnnotationUtil.findAnnotation(psiField, FXProperty.class), modifierList, ON_METHOD);

		return method;
	}

	@Final @Override
	protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
	// checks whether psiField is a JavaFX property
		if (!isProperty(psiField.getType(), psiField.getProject())) {
			builder.addError("@FXProperty is not available for type: '%s' ", psiField.getType().getCanonicalText());
			return false;
		}

		PsiType innerType = getPropertyInnerType(psiField);
		if (innerType == null) return false;

	// checks existence of similar methods
		validateExistingMethods(toGetter(psiField), 0, psiField, builder);
		validateExistingMethods(toPropertyAccessor(psiField), 0, psiField, builder);
		validateExistingMethods(toSetter(psiField), 1, psiField, builder);

	// validates field prefixes if @Accessors is used
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

	/**
	 * checks existence of final modifier and implementation of javafx.beans.value.WritableValue.
	 */
	@Final
	private boolean validateOnSet(@NotNull PsiField psiField) {
		PsiClass psiClass = PsiTypesUtil.getPsiClass(psiField.getType());
		PsiClass writableClass = JavaPsiFacade.getInstance(psiField.getProject()).findClass(WritableValue.class.getName(), GlobalSearchScope.allScope(psiField.getProject()));
		return psiClass != null && writableClass != null && !psiField.hasModifierProperty(PsiModifier.FINAL) && PsiClassUtil.hasParent(psiClass, writableClass);
	}

	/**
	 * verifies that field prefixes match @Accessors.
	 */
	private boolean validateAccessorPrefix(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
		if (!AccessorsInfo.build(psiField).prefixDefinedAndStartsWith(psiField.getName())) {
			builder.addWarning("Not generating accessors for this field: It does not fit your @Accessors prefix list.");
			return false;
		}
		return true;
	}

	/**
	 * checks implementation of javafx.beans.property.ReadOnlyProperty or javafx.css.StyleableProperty.
	 */
	@Final
	private boolean isProperty(@NotNull PsiType type, Project project) {
		PsiClass fieldClass = PsiTypesUtil.getPsiClass(type);
		PsiClass readOnlyClass = JavaPsiFacade.getInstance(project).findClass(ReadOnlyProperty.class.getName(), GlobalSearchScope.allScope(project));
		PsiClass styleableClass = JavaPsiFacade.getInstance(project).findClass(StyleableProperty.class.getName(), GlobalSearchScope.allScope(project));

		return fieldClass != null && readOnlyClass != null && styleableClass != null
			&& (fieldClass.isEquivalentTo(readOnlyClass) || fieldClass.isEquivalentTo(styleableClass)
			|| PsiClassUtil.hasParent(fieldClass, readOnlyClass) || PsiClassUtil.hasParent(fieldClass, styleableClass));
	}

	/**
	 * creates Substitutor collecting all parameter types available for type of the annotated field.
	 */
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

	/**
	 * Defines implicit usage of the field.
	 */
	@NotNull @Final @Override
	public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
		PsiClass psiClass = psiField.getContainingClass();
		PsiType innerType = getPropertyInnerType(psiField);
		if (psiClass == null) return LombokPsiElementUsage.NONE;

		Collection<PsiMethod> methods = PsiClassUtil.collectClassMethodsIntern(psiClass);
		if (PsiMethodUtil.hasSimilarMethod(methods, toSetter(psiField), 1) && PsiMethodUtil.hasSimilarMethod(methods, toGetter(psiField), 0)) {
			return LombokPsiElementUsage.READ_WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toSetter(psiField), 1)) {
			return LombokPsiElementUsage.WRITE;
		} else if (PsiMethodUtil.hasSimilarMethod(methods, toGetter(psiField), 0) || (innerType != null && PsiMethodUtil.hasSimilarMethod(methods, toPropertyAccessor(psiField), 0))) {
			return LombokPsiElementUsage.READ;
		}

		return LombokPsiElementUsage.NONE;
	}

	/**
	 * Determines PsiType, returned by getValue() method of JavaFX Property, which was annotated.
	 */
	@Final @Nullable
	private PsiType getPropertyInnerType(@NotNull PsiField psiField) {
	// creates substitutor for the type of annotated field
		PsiSubstitutor psiSubstitutor = createSubstitutor((PsiClassType) psiField.getType(), PsiSubstitutor.EMPTY);
		PsiClass psiClass = PsiTypesUtil.getPsiClass(psiField.getType());
		if (psiClass == null) return null;

	// substitutes type of property value
		@NonFinal PsiType type = null;
		for (PsiMethod method : psiClass.findMethodsByName(GET_METHOD, true)) {
			if (method.getParameterList().getParametersCount() != 0) continue;
			type = method.getReturnType();
			PsiClass methReturnClass = PsiTypesUtil.getPsiClass(type);
			if (methReturnClass != null && methReturnClass.getQualifiedName() != null) return psiSubstitutor.substitute(type);
		}

		return type;
	}

	/**
	 * Determines name for the setter of the property value.
	 */
	private String toSetter(@NotNull PsiField psiField) {
		return LombokUtils.toSetterName(AccessorsInfo.build(psiField), psiField.getName(), false);
	}

	/**
	 * Determines name for the getter of the property value.
	 */
	private String toGetter(@NotNull PsiField psiField) {
		return LombokUtils.toGetterName(AccessorsInfo.build(psiField), psiField.getName(), PsiType.BOOLEAN.getBoxedType(psiField) != null);
	}

	/**
	 * Determines name for the accessor of the property.
	 */
	private String toPropertyAccessor(@NotNull PsiField psiField) {
		return AccessorsInfo.build(psiField).removePrefix(psiField.getName()) + PROPERTY;
	}
}
