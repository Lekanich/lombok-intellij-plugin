package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.extension.LombokProcessorExtensionPoint;
import de.plushnikov.intellij.plugin.handler.FieldDefaultsUtil;
import de.plushnikov.intellij.plugin.problem.LombokProblem;
import de.plushnikov.intellij.plugin.processor.Processor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import gnu.trove.THashMap;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import static de.plushnikov.intellij.plugin.extension.LombokCompletionContributor.LombokElementFilter.getCallType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtendingMethods;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.isInExtensionScope;

/**
 * @author Plushnikov Michail
 */
public class LombokInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(LombokInspection.class.getName());

  private final Map<String, Collection<Processor>> allProblemHandlers;

  public LombokInspection() {
    allProblemHandlers = new THashMap<String, Collection<Processor>>();
    for (Processor lombokInspector : LombokProcessorExtensionPoint.EP_NAME.getExtensions()) {
      Collection<Processor> inspectorCollection = allProblemHandlers.get(lombokInspector.getSupportedAnnotation());
      if (null == inspectorCollection) {
        inspectorCollection = new ArrayList<Processor>(2);
        allProblemHandlers.put(lombokInspector.getSupportedAnnotation(), inspectorCollection);
      }
      inspectorCollection.add(lombokInspector);

      LOG.debug(String.format("LombokInspection registered %s inspector", lombokInspector));
    }
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Lombok annotations inspection";
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Lombok";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiClass currentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (currentClass == null) return;

        PsiType callType = getCallType(expression, currentClass);

        PsiElement resolve = expression.getMethodExpression().resolve();
        if (resolve == null || callType == null || !(resolve instanceof PsiMethod)) {
          return;
        }

        PsiMethod psiMethod = (PsiMethod) resolve;

        PsiClass methodContainingClass = psiMethod.getContainingClass();
        if (methodContainingClass == null || currentClass.getQualifiedName() == null) return;
        if (!(psiMethod instanceof LombokLightMethodBuilder) || methodContainingClass.getQualifiedName() == null || methodContainingClass.getQualifiedName().endsWith(currentClass.getQualifiedName())) {
          return;
        }

      // find this method
        boolean foundProblem = false;
        String methodName = psiMethod.getName();
        for (PsiMethod method : getExtendingMethods(currentClass)) {
          if (!method.getName().equals(methodName)) {
            continue;
          }

          PsiType type = getType(method.getParameterList().getParameters()[0].getType(), method);
          if (type.isAssignableFrom(callType)) {
            if (!isInExtensionScope(currentClass)) {
              foundProblem = true;
              break;                    // (this method can't resolve in this scope)
            }
            return;                     // exit (found method in this scope)
          } else {
            foundProblem = true;        // can't find method with that parameters, try another
          }
        }
        if (foundProblem) {
          holder.registerProblem(expression.getMethodExpression(), String.format("Cannot resolve method '%s()'...", psiMethod.getName()), ProblemHighlightType.ERROR);
        }
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        // do nothing, just implement
      }

      @Override
      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        FieldDefaultsUtil.handleClass(aClass, holder);
      }

      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        super.visitAnnotation(annotation);

        final String qualifiedName = annotation.getQualifiedName();
        if (StringUtils.isNotBlank(qualifiedName) && allProblemHandlers.containsKey(qualifiedName)) {
          final Collection<LombokProblem> problems = new HashSet<LombokProblem>();

          for (Processor inspector : allProblemHandlers.get(qualifiedName)) {
            problems.addAll(inspector.verifyAnnotation(annotation));
          }

          for (LombokProblem problem : problems) {
            holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
          }
        }
      }
    };
  }
}
