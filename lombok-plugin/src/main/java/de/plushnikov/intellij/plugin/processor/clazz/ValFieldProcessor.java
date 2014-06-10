package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Suburban Squirrel
 * @version 1.0.10
 * @since 1.0.10
 */
public class ValFieldProcessor extends AbstractValProcessor<PsiField> {
  /**
   * {@inheritDoc}
   */
  protected ValFieldProcessor() {
    super(PsiField.class);
  }

  @Override
  public List<PsiField> getValInheritedElements(@NotNull PsiClass psiClass) {
    return getValInheritedFields(psiClass);
  }

  public static List<PsiField> getValInheritedFields(@NotNull PsiClass psiClass) {
    Project project = psiClass.getProject();
    List<PsiClass> allValInitedClasses = getAllValInitedClasses(project);
    List<PsiField> allPublicFields = new ArrayList<PsiField>(10 * allValInitedClasses.size());

    for (PsiClass aClass : allValInitedClasses) {
      for (PsiField field : aClass.getAllFields()) {
        if (field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) allPublicFields.add(field);
      }
    }

    return allPublicFields;
  }
}
