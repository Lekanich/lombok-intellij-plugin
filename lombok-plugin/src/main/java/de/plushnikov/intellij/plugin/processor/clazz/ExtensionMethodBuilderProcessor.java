package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.createExtensionMethod;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.findClass;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtensionScope;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getUtilClass;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.hasSimilarExtendedMethod;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.isExtensible;
import static de.plushnikov.intellij.plugin.util.PsiMethodUtil.hasSimilarMethod;

/**
 * @see de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodProcessor
 * @author Suburban Squirrel
 * @version 1.0.5
 * @since 1.0.5
 */
public class ExtensionMethodBuilderProcessor extends AbstractProcessor {

  /**
   * {@inheritDoc}
   */
  protected ExtensionMethodBuilderProcessor() {
    super(ExtensionMethod.class, PsiMethod.class);
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    return Collections.EMPTY_LIST;
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass extensionClass) {
    List<? super PsiElement> result = Collections.emptyList();
    if (isExtensible(extensionClass)) {
        result = new ArrayList<PsiElement>();
        generatePsiElements(extensionClass, result);
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    return Collections.EMPTY_LIST;
  }

  protected void generatePsiElements(@NotNull PsiClass extensionClass, @NotNull List<? super PsiElement> result) {
    Set<PsiClass> utilClasses = new HashSet<PsiClass>();
    for (String scope : getExtensionScope(extensionClass)) {
      PsiClass psiClass = findClass(extensionClass, scope);
      if (psiClass != null) utilClasses.addAll(getUtilClass(psiClass));
    }

    result.addAll(createMethods(getSpecialParamMethods(utilClasses, extensionClass), extensionClass));
  }

  @NotNull
  protected List<PsiMethod> createMethods(@NotNull List<PsiMethod> methods, @NotNull PsiClass extensibleClass) {
    List<PsiMethod> newMethods = new ArrayList<PsiMethod>();
    Collection<PsiMethod> methodsInClass = PsiClassUtil.collectClassMethodsIntern(extensibleClass);
    for (PsiMethod method : methods) {
      if (!hasSimilarMethod(methodsInClass, method.getName(), method.getParameterList().getParametersCount())) {
        newMethods.add(createExtensionMethod(method, extensibleClass));
      }
    }
    return newMethods;
  }

  @NotNull
  protected List<PsiMethod> getSpecialParamMethods(@NotNull Set<PsiClass> utilClasses, @NotNull PsiClass extensibleClass) {
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    Collection<PsiMethod> extensionClassMethods = PsiClassUtil.collectClassMethodsIntern(extensibleClass);

    List<PsiClass> list = new ArrayList<PsiClass>(utilClasses);
    for (int i = 0; i < list.size(); i++) {
      for (PsiMethod psiMethod : list.get(i).getAllMethods()) {
        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        if (parameters.length == 0) continue;
        if (!psiMethod.hasModifierProperty(PsiModifier.PUBLIC) || !psiMethod.hasModifierProperty(PsiModifier.STATIC) || psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) continue;

        PsiClassType extensionType = PsiTypesUtil.getClassType(extensibleClass);
        PsiType type = getType(parameters[0].getType(), psiMethod);
        if (extensibleClass.getQualifiedName() == null) continue;
        if (!ExtensionMethodUtil.ARRAY_PSI_CLASS_NAME.equals(extensibleClass.getQualifiedName()) && !type.isAssignableFrom(extensionType)) continue;

        if (!hasSimilarExtendedMethod(methods, psiMethod) && !hasSimilarMethod(extensionClassMethods, psiMethod.getName(), parameters.length - 1)) {
          methods.add(psiMethod);                                                         // filter methods with same names
        }
      }
    }

    return methods;
  }
}
