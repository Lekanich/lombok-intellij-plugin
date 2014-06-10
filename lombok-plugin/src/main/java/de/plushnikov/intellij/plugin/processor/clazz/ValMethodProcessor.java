package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Suburban Squirrel
 * @version 1.0.10
 * @since 1.0.10
 */
public class ValMethodProcessor extends AbstractValProcessor<PsiMethod> {
  /**
   * {@inheritDoc}
   */
  protected ValMethodProcessor() {
    super(PsiMethod.class);
  }

  @Override
  public List<PsiMethod> getValInheritedElements(@NotNull PsiClass psiClass) {
    return getValInheritedMethods(psiClass);
  }

  public static List<PsiMethod> getValInheritedMethods(@NotNull PsiClass psiClass) {
    Project project = psiClass.getProject();
    List<PsiClass> allValInitedClasses = getAllValInitedClasses(project);
    List<PsiMethod> allPublicMethods = new ArrayList<PsiMethod>(10 * allValInitedClasses.size());

    if (allValInitedClasses.isEmpty()) return Collections.EMPTY_LIST;

    for (PsiClass aClass : allValInitedClasses) {
      for (PsiMethod method : aClass.getAllMethods()) {
        if (method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC) && !method.isConstructor()) {
          allPublicMethods.add(method);
        }
      }
    }

  // remove duplicateMethods
    for (int i = 0; i < allPublicMethods.size(); i++) {
      for (int j = i + 1; j < allPublicMethods.size(); j++) {
        if (ExtensionMethodBuilderProcessor.isSimilarExtendedMethod(allPublicMethods.get(i), allPublicMethods.get(j))) {
          allPublicMethods.remove(j);
          j--;
        }
      }
    }

    return allPublicMethods;
  }
}
