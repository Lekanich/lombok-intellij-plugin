package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodBuilderProcessor.EXTENSION_CLASSES;

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
    if (!EXTENSION_CLASSES.containsKey(className)) {
      EXTENSION_CLASSES.put(className, new HashSet<String>());
    }
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
      for (String child : getTypeElements(method)) {
        setNames.add(child);
      }
    }
  }

  protected boolean validateAnnotationOnRightType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError("'@ExtensionMethod' is only supported on a class type");
      result = false;
    }
    return result;
  }

  @NotNull
  public static List<PsiMethod> getExtendingMethods(@NotNull PsiClass currentClass) {
    Set<PsiClass> utilClasses = new HashSet<PsiClass>();
    for (String scope : ExtensionMethodBuilderProcessor.EXTENSION_CLASSES.keySet()) {
      PsiClass psiClass = ExtensionMethodBuilderProcessor.findClass(currentClass, scope);
      if (psiClass != null) {
        utilClasses.addAll(ExtensionMethodBuilderProcessor.getUtilClass(psiClass));
      }
    }

    List<PsiMethod> list = new ArrayList<PsiMethod>();
    for (PsiClass utilClass : utilClasses) {
      for (PsiMethod method : utilClass.getMethods()) {
        if (method.getParameterList().getParametersCount() != 0) {
          list.add(method);
        }
      }
    }

    return list;
  }

  @NotNull
  public static Set<String> getTypeElements(@NotNull PsiMethod method) {
    Set<String> set = new HashSet<String>();
    PsiElement codeBlock = null;
    for (PsiElement psiElement : method.getChildren()) {
      if (psiElement instanceof PsiCodeBlock) {
        codeBlock = psiElement;
      }
    }
    if (codeBlock != null) {
      fillListReference(codeBlock, set);
    }

    return set;
  }

  @NotNull
  public static Set<String> fillListReference(@NotNull PsiElement parent, @NotNull Set<String> set) {
    for (PsiElement child : parent.getChildren()) {
      if (child instanceof PsiLocalVariable) {
        set.add(((PsiLocalVariable) child).getType().getCanonicalText());
      } if (child instanceof PsiLiteralExpression) {
        if (((PsiLiteralExpression)child).getType() != null) {
          set.add(((PsiLiteralExpression)child).getType().getCanonicalText());
        }
      } else if (child.getReference() == null) {
        fillListReference(child, set);
      } else {
        PsiElement element = ((PsiReference) child).resolve();
        if (element instanceof PsiField) {
          set.add(((PsiField) element).getType().getCanonicalText());
        } else if (element instanceof PsiMethod) {
          if (((PsiMethod) element).getReturnType() != null) {
            set.add(((PsiMethod) element).getReturnType().getCanonicalText());
          }
        }
      }
    }

    return set;
  }
}