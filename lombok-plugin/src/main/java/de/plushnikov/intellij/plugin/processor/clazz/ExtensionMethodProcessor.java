package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.EXTENSION_CLASSES;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.fillListReference;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getTypeElements;

/**
 * @author Suburban Squirrel
 * @version 0.8.6
 * @since 0.8.6
 */
public class ExtensionMethodProcessor extends AbstractClassProcessor {

  protected ExtensionMethodProcessor() {
    super(ExtensionMethod.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiClass, builder);
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    // find all possible extension classes
    String className = psiClass.getQualifiedName();                 // get class qualifier name from type
    Set<String> setNames;
    if (!EXTENSION_CLASSES.containsKey(className)) EXTENSION_CLASSES.put(className, new HashSet<String>());
    setNames = EXTENSION_CLASSES.get(className);
    setNames.add(className);                                        // add class that contain

    // get from class initializer
    for (PsiClassInitializer initializer : psiClass.getInitializers()) {
      fillListReference(initializer.getBody(), setNames);
    }

    // get field types
    for (PsiField field : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      setNames.add(field.getType().getCanonicalText());
    }

    // parse and find
    for (PsiMethod method : PsiClassUtil.collectClassMethodsIntern(psiClass)) {
      if (method.getReturnType() != null) {
        setNames.add(method.getReturnType().getCanonicalText());
      }

      for (PsiParameter parameter : method.getParameterList().getParameters()) {
        setNames.add(parameter.getType().getCanonicalText());
      }
      setNames.addAll(getTypeElements(method));
    }
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@ExtensionMethod' is only supported on a class type");
      return false;
    }
    return true;
  }
}