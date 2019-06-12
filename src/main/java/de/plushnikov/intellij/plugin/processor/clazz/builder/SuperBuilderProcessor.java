package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @SuperBuilder lombok annotation on a class.
 * Creates methods for a builder pattern for initializing a class.
 *
 * @author Aleksandr Zhelezniak
 */
public class SuperBuilderProcessor extends AbstractClassProcessor {
  private final SuperBuilderHandler builderHandler;

  public SuperBuilderProcessor(@NotNull ConfigDiscovery configDiscovery,
                               @NotNull SuperBuilderHandler builderHandler) {
    super(configDiscovery, PsiMethod.class, SuperBuilder.class);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    final Collection<PsiAnnotation> result = super.collectProcessedAnnotations(psiClass);
    addFieldsAnnotation(result, psiClass, Singular.class.getCanonicalName());
    return result;
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    // we skip validation here, because it will be validated by other SuperBuilderClassProcessor
    return true;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    // have to create full class (with all methods) here, or auto completion doesn't work
    PsiClass abstractBuilderClass = builderHandler.getExistInnerBuilderClass(psiClass, null, psiAnnotation)
      .orElseGet(() -> builderHandler.createSuperBuilderAbstractClass(psiClass, psiAnnotation));
    PsiClass implBuilderClass = builderHandler.getExistInnerImplBuilderClass(psiClass, psiAnnotation)
      .orElseGet(() -> builderHandler.createSuperBuilderImplClass(psiClass, abstractBuilderClass, psiAnnotation));

    // generate "builder()" method
    builderHandler.createBuilderMethodForClassIfNecessary(psiClass, abstractBuilderClass, implBuilderClass, psiAnnotation)
      .ifPresent(target::add);

    // generate "toBuilder()" method
    builderHandler.createToBuilderMethodIfNecessary(psiClass, null, abstractBuilderClass, psiAnnotation)
      .ifPresent(target::add);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}
