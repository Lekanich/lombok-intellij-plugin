package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
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
  private final BuilderHandler builderHandler;

  public SuperBuilderProcessor(@NotNull ConfigDiscovery configDiscovery,
                               @NotNull BuilderHandler builderHandler) {
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
    PsiClass builderClass = builderHandler.getExistInnerBuilderClass(psiClass, null, psiAnnotation).orElse(null);
    if (null == builderClass) {
      // have to create full class (with all methods) here, or auto completion doesn't work
      builderClass = builderHandler.createBuilderClass(psiClass, psiAnnotation);
    }

    builderHandler.createBuilderMethodIfNecessary(psiClass, null, builderClass, psiAnnotation)
      .ifPresent(target::add);

    builderHandler.createToBuilderMethodIfNecessary(psiClass, null, builderClass, psiAnnotation)
      .ifPresent(target::add);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.READ_WRITE;
  }
}
