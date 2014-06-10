package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.getAnnotationValues;

/**
 * @see de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodProcessor
 * @author Suburban Squirrel
 * @version 1.0.5
 * @since 1.0.5
 */
public class ExtensionMethodBuilderProcessor extends AbstractProcessor {
  public static final String ARRAY_PSI_CLASS_NAME = "_Dummy_.__Array__";

  /**
   * key - qualifier name class with @ExtensionMethod
   * value - set of qualifier names of extensible classes
   */
  public static final Map<String, Set<String>> EXTENSION_CLASSES = new HashMap<String, Set<String>>();

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
  public Collection<LombokProblem> verifyAnnotation(PsiAnnotation psiAnnotation) {
    return Collections.EMPTY_LIST;
  }

  protected void generatePsiElements(@NotNull PsiClass extensionClass, @NotNull List<? super PsiElement> result) {
    Set<PsiClass> utilClasses = new HashSet<PsiClass>();
    for (String scope : getExtensionScope(extensionClass)) {
      PsiClass psiClass = findClass(extensionClass, scope);
      if (psiClass != null) utilClasses.addAll(getUtilClass(psiClass));
    }

    for (PsiMethod method : createMethods(getParamMethods(utilClasses, extensionClass), extensionClass)) {
      result.add(method);
    }
  }

  @NotNull
  protected List<PsiMethod> createMethods(@NotNull List<PsiMethod> methods, @NotNull PsiClass extensibleClass) {
    List<PsiMethod> newMethods = new ArrayList<PsiMethod>();
    Collection<PsiMethod> methodsInClass = PsiClassUtil.collectClassMethodsIntern(extensibleClass);
    for (PsiMethod method : methods) {
      if (!PsiMethodUtil.hasSimilarMethod(methodsInClass, method.getName(), method.getParameterList().getParametersCount())) {
        newMethods.add(createExtensionMethod(method, extensibleClass));
      }
    }
    return newMethods;
  }

  @NotNull
  public static PsiMethod createExtensionMethod(@NotNull PsiMethod method, PsiClass extensibleClass) {
    LombokLightMethodBuilder newMethod = new LombokLightMethodBuilder(method.getManager(), method.getName());
    newMethod.addModifier(PsiModifier.PUBLIC);
    newMethod.setContainingClass(extensibleClass);
    newMethod.setMethodReturnType(method.getReturnType());
    newMethod.setNavigationElement(method);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 1; i < parameters.length; i++) {
      newMethod.addParameter(parameters[i]);
    }

    return newMethod;
  }

  @NotNull
  public static List<PsiClass> getUtilClass(@NotNull PsiClass psiClass) {
    List<PsiClass> utilClasses = new ArrayList<PsiClass>();
    PsiAnnotation annotation = findAnnotation(psiClass, ExtensionMethod.class);
    if (annotation == null) return utilClasses;

    for (PsiType type : getAnnotationValues(annotation, "value", PsiType.class)) {
      PsiClass aClass = PsiTypesUtil.getPsiClass(type);
      if (aClass != null) utilClasses.add(aClass);
    }
    return utilClasses;
  }

  @NotNull
  protected List<PsiMethod> getParamMethods(@NotNull Set<PsiClass> utilClasses, @NotNull PsiClass extensibleClass) {
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
        if (!extensibleClass.getQualifiedName().equals(ARRAY_PSI_CLASS_NAME) && !type.isAssignableFrom(extensionType)) continue;
        if (!hasSimilarExtendedMethod(methods, psiMethod) && !PsiMethodUtil.hasSimilarMethod(extensionClassMethods, psiMethod.getName(), parameters.length - 1)) {
          methods.add(psiMethod);                                                         // filter methods with same names
        }
      }
    }

    return methods;
  }

  public static boolean isInExtensionScope(@NotNull PsiClass scope) {
    for (String extensionClassScope : EXTENSION_CLASSES.keySet()) {
      if (EXTENSION_CLASSES.get(extensionClassScope).contains(scope.getQualifiedName())) return true;
    }
    return false;
  }

  @NotNull
  public static Set<String> getExtensionScope(@NotNull PsiClass extensionClass) {
    Set<String> scopes = new HashSet<String>();
    if (extensionClass.getQualifiedName() == null) return scopes;
    for (String scope : EXTENSION_CLASSES.keySet()) {
      if (extensionClass.getQualifiedName().equals(ARRAY_PSI_CLASS_NAME)) {
        for (String extension : EXTENSION_CLASSES.get(scope)) {
          if (!extension.endsWith("[]")) continue;
          scopes.add(scope);
          break;
        }
      } else if (EXTENSION_CLASSES.get(scope).contains(extensionClass.getQualifiedName())) {
        scopes.add(scope);
      }
    }
    return scopes;
  }

  @Nullable
  public static PsiClass findClass(@NotNull PsiElement context, @NotNull String className) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(context.getProject());
    return facade.findClass(className, searchScope);
  }

  /**
   * For one parameter method
   * Get true type (Example: <T extends Comparable<? super T>> T[] then return Comparable<? super T>[])
   */
  @NotNull
  public static PsiType getType(@NotNull PsiType type, @NotNull PsiMethod psiMethod) {
    PsiTypeParameter[] typeParameters = psiMethod.getTypeParameterList().getTypeParameters();
    if (typeParameters.length == 0) return type;
    String text = typeParameters[0].getText();
    if (text.contains("extends")) {
      return TypeConversionUtil.erasure(type.getSuperTypes()[0]);           // fixme hard spike (erasure generic type) --SS
    }
    return type;
  }

  public static boolean isExtensible(@NotNull PsiClass psiClass) {
    return !getExtensionScope(psiClass).isEmpty();
  }

  public static boolean hasSimilarExtendedMethod(@NotNull Collection<PsiMethod> classMethods, @NotNull PsiMethod method) {
    for (PsiMethod classMethod : classMethods) {
      if (isSimilarExtendedMethod(classMethod, method)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isSimilarExtendedMethod(@NotNull PsiMethod method1, @NotNull PsiMethod method2) {
    if (!method1.getName().equals(method2.getName())) return false;
    if (method1.getParameterList().getParametersCount() != method2.getParameterList().getParametersCount()) return false;

    for (int i = 1; i < method1.getParameterList().getParametersCount(); i++) {
      if (!method1.getParameterList().getParameters()[i].getType().equals(method2.getParameterList().getParameters()[i].getType())) return false;
    }

    return true;
  }
}
