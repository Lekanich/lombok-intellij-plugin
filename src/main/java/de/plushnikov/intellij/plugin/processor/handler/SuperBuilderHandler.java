package de.plushnikov.intellij.plugin.processor.handler;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightTypeParameterBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.ToStringProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Handler methods for {@link lombok.experimental.SuperBuilder}-processing
 *
 * @author Aleksandr Zhelezniak
 */
public class SuperBuilderHandler extends BuilderHandler {
  private static final String SELF_METHOD_NAME = "self";
  private static final String IMPL_BUILDER_END = "Impl";
  private static final String TYPE_PARAM_FOR_ORIGINAL_CLASS = "C";
  private static final String TYPE_PARAM_FOR_BUILDER = "B";

  public SuperBuilderHandler(@NotNull ToStringProcessor toStringProcessor, @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    super(toStringProcessor, noArgsConstructorProcessor);
  }

  public Optional<PsiClass> getExistInnerImplBuilderClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(psiClass, psiAnnotation, null) + IMPL_BUILDER_END;
    return PsiClassUtil.getInnerClassInternByName(psiClass, builderClassName);
  }

  @NotNull
  public LombokLightClassBuilder createSuperBuilderImplClass(@NotNull PsiClass parentClass, @NotNull PsiClass builderAbstractClass, @NotNull PsiAnnotation psiAnnotation) {
    final String builderClassName = getBuilderClassName(parentClass, psiAnnotation, null) + IMPL_BUILDER_END;
    final String builderClassQualifiedName = parentClass.getQualifiedName() + "." + builderClassName;

    // create empty class
    final LombokLightClassBuilder builderClassImpl = new LombokLightClassBuilder(parentClass, builderClassName, builderClassQualifiedName)
      .withContainingClass(parentClass)
      .withNavigationElement(psiAnnotation)
      .withParameterTypes(parentClass.getTypeParameterList())
      .withModifier(PsiModifier.PRIVATE)
      .withModifier(PsiModifier.STATIC)
      .withModifier(PsiModifier.FINAL);
    builderClassImpl.withContainingClass(parentClass);

    final PsiSubstitutor substitutor = getBuilderSubstitutor(parentClass, builderClassImpl);

    // populate extend list
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(parentClass.getProject());
    PsiType[] types = new PsiType[builderAbstractClass.getTypeParameters().length];
    for (int i = 0; i < parentClass.getTypeParameters().length; i++) {
      types[i] = factory.createType(parentClass.getTypeParameters()[i]);
    }

    types[types.length - 2] = factory.createType(parentClass, substitutor);
    types[types.length - 1] = factory.createType(builderClassImpl, substitutor);

    builderClassImpl.getExtendsList().addReference(factory.createType(builderAbstractClass, types));

    // create 'build' method
    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, parentClass, null, builderClassImpl);
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    builderClassImpl.addMethod(createBuildMethod(parentClass, null, builderClassImpl, buildMethodName, builderInfos));

    // create 'self' method
    builderClassImpl.addMethod(createSelfMethod(parentClass, builderClassImpl, psiAnnotation));

    return builderClassImpl;
  }

  @NotNull
  public LombokLightClassBuilder createSuperBuilderAbstractClass(@NotNull PsiClass parentClass, @NotNull PsiAnnotation psiAnnotation) {
    // create empty abstract builder class
    LombokLightClassBuilder builderClass = createEmptyBuilderClass(parentClass, psiAnnotation);
    builderClass.withContainingClass(parentClass);
    builderClass.withModifier(PsiModifier.ABSTRACT);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(parentClass.getProject());

    // add generics
    int typeParamCountFromOriginalClass = builderClass.getTypeParameterList().getTypeParameters().length;
    LightTypeParameterBuilder refToOriginClassParam = new LightTypeParameterBuilder(TYPE_PARAM_FOR_ORIGINAL_CLASS, builderClass, typeParamCountFromOriginalClass);

    PsiType[] typesClass = new PsiType[parentClass.getTypeParameters().length + 2];
    for (int i = 0; i < parentClass.getTypeParameters().length; i++) {
      typesClass[i] = factory.createType(parentClass.getTypeParameters()[i]);
    }
    refToOriginClassParam.getExtendsList().addReference(factory.createType(parentClass, typesClass));

    LightTypeParameterBuilder refToBuilderClassParam = new LightTypeParameterBuilder(TYPE_PARAM_FOR_BUILDER, builderClass, typeParamCountFromOriginalClass + 1);

    PsiType[] typesBuilder = new PsiType[builderClass.getTypeParameters().length + 2];
    for (int i = 0; i < builderClass.getTypeParameters().length; i++) {
      typesBuilder[i] = factory.createType(builderClass.getTypeParameters()[i]);
    }
    typesBuilder[typesBuilder.length - 2] = factory.createType(refToBuilderClassParam);
    typesBuilder[typesBuilder.length - 1] = factory.createType(refToOriginClassParam);

    refToBuilderClassParam.getExtendsList().addReference(factory.createType(builderClass, typesBuilder));

    LightTypeParameterListBuilder typeParameterList = new LightTypeParameterListBuilder(parentClass.getManager(), parentClass.getLanguage());
    typeParameterList.addParameter(refToOriginClassParam);
    typeParameterList.addParameter(refToBuilderClassParam);

    builderClass.withParameterTypes(typeParameterList);

    // populate methods
    builderClass.withMethods(getNoArgsConstructorProcessor().createNoArgsConstructor(builderClass, PsiModifier.PUBLIC, psiAnnotation));

    // create 'self' method
    builderClass.addMethod(createAbstractSelfMethod(parentClass, psiAnnotation, refToBuilderClassParam));

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, parentClass, null, builderClass);

    // create builder fields
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderFields)
      .forEach(builderClass::withFields);

    // create builder methods
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(builderClass::withMethods);

    // create 'build' method
    builderClass.addMethod(createAbstractBuildMethod(parentClass, builderClass, psiAnnotation, refToOriginClassParam));

    // create 'toString' method
    builderClass.addMethod(createToStringMethod(psiAnnotation, builderClass));

    return builderClass;
  }

  private PsiMethod createAbstractBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass returnType) {
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    return new LombokLightMethodBuilder(parentClass.getManager(), buildMethodName)
      .withContainingClass(builderClass)
      .withMethodReturnType(JavaPsiFacade.getElementFactory(parentClass.getProject()).createType(returnType))
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC, PsiModifier.ABSTRACT);
  }

  private PsiMethod createAbstractSelfMethod(@NotNull PsiClass parentClass, @NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass returnType) {
    return new LombokLightMethodBuilder(parentClass.getManager(), SELF_METHOD_NAME)
      .withMethodReturnType(JavaPsiFacade.getElementFactory(parentClass.getProject()).createType(returnType))
      .withModifier(PsiModifier.PROTECTED, PsiModifier.ABSTRACT)
      .withNavigationElement(psiAnnotation);
  }

  private PsiMethod createSelfMethod(@NotNull PsiClass parentClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(parentClass.getManager(), SELF_METHOD_NAME)
      .withMethodReturnType(PsiClassUtil.getTypeWithGenerics(builderClass))
      .withModifier(PsiModifier.PROTECTED)
      .withNavigationElement(psiAnnotation)
      .withBody(PsiMethodUtil.createCodeBlockFromText("return this;", builderClass));
  }

  public Optional<PsiMethod> createBuilderMethodForClassIfNecessary(
    @NotNull PsiClass parentClass,
    @NotNull PsiClass abstractBuilderClass,
    @NotNull PsiClass implBuilderClass,
    @NotNull PsiAnnotation psiAnnotation) {

    final String builderMethodName = getBuilderMethodName(psiAnnotation);
    if (hasMethod(parentClass, builderMethodName)) {
      return Optional.empty();
    }

    final PsiType psiImplTypeWithGenerics = PsiClassUtil.getTypeWithGenerics(implBuilderClass);
    final PsiType psiAbstractTypeWithGenerics = getBuilderSubstitutor(parentClass, abstractBuilderClass)
      .substitute(PsiClassUtil.getTypeWithGenerics(abstractBuilderClass));

    final LombokLightMethodBuilder method = new LombokLightMethodBuilder(parentClass.getManager(), builderMethodName)
      .withMethodReturnType(psiAbstractTypeWithGenerics)
      .withContainingClass(parentClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC)
      .withModifier(PsiModifier.STATIC)
      .withBody(createBuilderMethodCodeBlock(parentClass, psiImplTypeWithGenerics));
    addTypeParameters(parentClass, null, method);

    return Optional.of(method);
  }

  private PsiSubstitutor getBuilderSubstitutor(@NotNull PsiTypeParameterListOwner classOrMethodToBuild, @NotNull PsiClass innerClass) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiTypeParameter[] typeParameters = classOrMethodToBuild.getTypeParameters();
      PsiTypeParameter[] builderParams = innerClass.getTypeParameters();
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        substitutor = substitutor.put(typeParameter, PsiSubstitutor.EMPTY.substitute(builderParams[i]));
      }
      for (int i = typeParameters.length; i < builderParams.length; i++) {
        substitutor = substitutor.put(builderParams[i], PsiWildcardType.createUnbounded(innerClass.getManager()));
      }
    }
    return substitutor;
  }
}
