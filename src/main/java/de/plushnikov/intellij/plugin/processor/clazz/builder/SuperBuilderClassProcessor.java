package de.plushnikov.intellij.plugin.processor.clazz.builder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.handler.SuperBuilderHandler;
import de.plushnikov.intellij.plugin.psi.LombokLightClassBuilder;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SuperBuilderClassProcessor extends AbstractClassProcessor {

  private final SuperBuilderHandler builderHandler;

  public SuperBuilderClassProcessor(@NotNull ConfigDiscovery configDiscovery, @NotNull SuperBuilderHandler builderHandler) {
    super(configDiscovery, PsiClass.class, SuperBuilder.class);
    this.builderHandler = builderHandler;
  }

  @Override
  public boolean isEnabled(@NotNull PropertiesComponent propertiesComponent) {
    return ProjectSettings.isEnabled(propertiesComponent, ProjectSettings.IS_BUILDER_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return true;
  }

  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    LombokLightClassBuilder superBuilderAbstractClass = builderHandler.createSuperBuilderAbstractClass(psiClass, psiAnnotation);
    target.add(superBuilderAbstractClass);
    target.add(builderHandler.createSuperBuilderImplClass(psiClass, superBuilderAbstractClass, psiAnnotation));
  }
}
