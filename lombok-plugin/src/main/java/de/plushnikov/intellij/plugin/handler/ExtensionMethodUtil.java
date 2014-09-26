package de.plushnikov.intellij.plugin.handler;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.getAnnotationValues;

/**
 * @author Suburban Squirrel
 * @version 1.0.26
 * @since 1.0.26
 */
final public class ExtensionMethodUtil {
  /**
   * key - qualifier name class with @ExtensionMethod
   * value - set of qualifier names of extensible classes
   */
  public static final Map<String, Set<String>> EXTENSION_CLASSES = new HashMap<String, Set<String>>();
  public static final String ARRAY_PSI_CLASS_NAME = "_Dummy_.__Array__";

  public static boolean isInExtensionScope(@NotNull PsiClass scope) {
    for (String extensionClassScope : EXTENSION_CLASSES.keySet()) {
      if (EXTENSION_CLASSES.get(extensionClassScope).contains(scope.getQualifiedName())) return true;
    }
    return false;
  }

  @NotNull
  public static List<PsiMethod> getExtendingMethods(@NotNull PsiClass currentClass) {
    Set<PsiClass> utilClasses = new HashSet<PsiClass>();
    for (String scope : ExtensionMethodUtil.EXTENSION_CLASSES.keySet()) {
      PsiClass psiClass = findClass(currentClass, scope);
      if (psiClass != null) {
        utilClasses.addAll(getUtilClass(psiClass));
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

  public static boolean isExtensible(@NotNull PsiClass psiClass) {
    return !getExtensionScope(psiClass).isEmpty();
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
      return TypeConversionUtil.erasure(type.getSuperTypes()[0]);           // todo hard spike (erasure generic type) mb change something  --SS
    }
    return type;
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
        PsiType type = ((PsiLiteralExpression) child).getType();

        if (type != null) {
          set.add(type.getCanonicalText());
        }
      } else if (child.getReference() == null) {
        fillListReference(child, set);
      } else {
        PsiElement element = ((PsiReference) child).resolve();

        if (element instanceof PsiField) {
          set.add(((PsiField) element).getType().getCanonicalText());
        } else if (element instanceof PsiMethod) {
          PsiType type = ((PsiMethod) element).getReturnType();
          if (type != null) {
            set.add(type.getCanonicalText());
          }
        }
      }
    }

    return set;
  }
}
