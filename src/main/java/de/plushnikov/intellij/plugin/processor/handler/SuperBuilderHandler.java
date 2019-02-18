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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SuperBuilderHandler extends BuilderHandler {
  private static final String SELF_METHOD_NAME = "self";
  private static final String IMPL_BUILDER_END = "Impl";
  private static final String TYPE_PARAM_FOR_ORIGINAL_CLASS = "C";
  private static final String TYPE_PARAM_FOR_BUILDER = "B";

  public SuperBuilderHandler(@NotNull ToStringProcessor toStringProcessor, @NotNull NoArgsConstructorProcessor noArgsConstructorProcessor) {
    super(toStringProcessor, noArgsConstructorProcessor);
  }

  @NotNull
  public LombokLightClassBuilder createSuperBuilderImplClass(@NotNull PsiClass parentClass, @NotNull LombokLightClassBuilder builderAbstractClass, @NotNull PsiAnnotation psiAnnotation) {
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


    final PsiSubstitutor substitutor = getBuilderSubstitutor(parentClass, builderClassImpl);

    // populate extend list
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(parentClass.getProject());
    PsiType[] types = new PsiType[builderAbstractClass.getTypeParameterList().getTypeParameters().length];
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
  public LombokLightClassBuilder createSuperBuilderAbstractClass(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation) {
    // create empty abstract builder class
    LombokLightClassBuilder builderClass = createEmptyBuilderClass(psiClass, psiAnnotation);
    builderClass.withModifier(PsiModifier.ABSTRACT);

    // add generics
    int typeParamCountFromOriginalClass = builderClass.getTypeParameterList().getTypeParameters().length;
    LightTypeParameterBuilder cParam = new LightTypeParameterBuilder(TYPE_PARAM_FOR_ORIGINAL_CLASS, builderClass, typeParamCountFromOriginalClass);
    cParam.getExtendsList().addReference(psiClass);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
    LightTypeParameterBuilder bParam = new LightTypeParameterBuilder(TYPE_PARAM_FOR_BUILDER, builderClass, typeParamCountFromOriginalClass + 1);

    PsiType[] types = new PsiType[builderClass.getTypeParameters().length + 2];
    for (int i = 0; i < builderClass.getTypeParameters().length; i++) {
      types[i] = factory.createType(builderClass.getTypeParameters()[i]);
    }
    types[types.length - 2] = factory.createType(bParam);
    types[types.length - 1] = factory.createType(cParam);

    bParam.getExtendsList().addReference(factory.createType(builderClass, types));

    LightTypeParameterListBuilder typeParameterList = new LightTypeParameterListBuilder(psiClass.getManager(), psiClass.getLanguage());
    typeParameterList.addParameter(cParam);
    typeParameterList.addParameter(bParam);

    builderClass.withParameterTypes(typeParameterList);

    // populate methods
    builderClass.withMethods(getNoArgsConstructorProcessor().createNoArgsConstructor(builderClass, PsiModifier.PUBLIC, psiAnnotation));

    // create 'self' method
    builderClass.addMethod(createAbstractSelfMethod(psiClass, builderClass, psiAnnotation));

    final List<BuilderInfo> builderInfos = createBuilderInfos(psiAnnotation, psiClass, null, builderClass);

    // create builder fields
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderFields)
      .forEach(builderClass::withFields);

    // create builder methods
    builderInfos.stream()
      .map(BuilderInfo::renderBuilderMethods)
      .forEach(builderClass::withMethods);

    // create 'build' method
    builderClass.addMethod(createAbstractBuildMethod(psiClass, builderClass, psiAnnotation));

    // create 'toString' method
    builderClass.addMethod(createToStringMethod(psiAnnotation, builderClass));

    return builderClass;
  }

  private PsiMethod createAbstractBuildMethod(@NotNull PsiClass parentClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation) {
    final String buildMethodName = getBuildMethodName(psiAnnotation);
    return new LombokLightMethodBuilder(parentClass.getManager(), buildMethodName)
      .withContainingClass(builderClass)
      .withMethodReturnType(JavaPsiFacade.getElementFactory(parentClass.getProject()).createTypeFromText(TYPE_PARAM_FOR_ORIGINAL_CLASS, builderClass))
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PUBLIC, PsiModifier.ABSTRACT);
  }

  private PsiMethod createAbstractSelfMethod(@NotNull PsiClass parentClass, @NotNull PsiClass builderClass, @NotNull PsiAnnotation psiAnnotation) {
    return new LombokLightMethodBuilder(parentClass.getManager(), SELF_METHOD_NAME)
      .withMethodReturnType(JavaPsiFacade.getElementFactory(parentClass.getProject()).createTypeFromText(TYPE_PARAM_FOR_BUILDER, builderClass))
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
}
