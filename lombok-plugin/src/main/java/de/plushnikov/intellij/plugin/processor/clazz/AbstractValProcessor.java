package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Suburban Squirrel
 * @version 1.0.10
 * @since 1.0.10
 */
abstract public class AbstractValProcessor<T extends PsiElement> extends AbstractProcessor {
  public static final String ANNOTATION_METHOD = "annotationType";          // remove this method from completion (easy hard code this)

  /**
   * {@inheritDoc}
   */
  protected AbstractValProcessor(@NotNull Class<? extends PsiElement> supportedClass) {
    super(val.class, supportedClass);
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
    if (isVal(extensionClass)) {
      result = new ArrayList<PsiElement>();
      result.addAll(getValInheritedElements(extensionClass));
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(PsiAnnotation psiAnnotation) {
    return Collections.EMPTY_LIST;
  }

  public static boolean isVal(@NotNull PsiType psiType) {
    return val.class.getCanonicalName().equals(psiType.getCanonicalText());
  }

  public static boolean isVal(@NotNull PsiClass psiClass) {
    return val.class.getCanonicalName().equals(psiClass.getQualifiedName());
  }

  abstract public List<T> getValInheritedElements(@NotNull PsiClass psiClass);

  public static List<PsiClass> getAllValInitedClasses(@NotNull Project project) {
  // get all Project Classes
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    String[] allClassNames = PsiShortNamesCache.getInstance(project).getAllClassNames();
    List<PsiClass> allClasses = new ArrayList<PsiClass>((int) (1.5 * allClassNames.length));
    for (String allClassName : allClassNames) {
      for (PsiClass foundClass : PsiShortNamesCache.getInstance(project).getClassesByName(allClassName, scope)) {
        allClasses.add(foundClass);
      }
    }

  // get all initializer clases from local lombok.val variables
    List<PsiClass> localPsiClasses = new ArrayList<PsiClass>(2 * allClasses.size());
    for (PsiClass aClass : allClasses) {
      Collection<PsiLocalVariable> childrenOfType = PsiTreeUtil.findChildrenOfType(aClass, PsiLocalVariable.class);
      for (PsiLocalVariable variable : childrenOfType) {
        if (val.class.getCanonicalName().equals(variable.getType().getCanonicalText()) && variable.getInitializer() != null) {
          PsiClass psiClass = PsiTypesUtil.getPsiClass(variable.getInitializer().getType());
          if (psiClass != null) localPsiClasses.add(psiClass);
        }
      }
    }

    return localPsiClasses;
  }
}
