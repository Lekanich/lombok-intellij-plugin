package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.daemon.impl.CheckLevelHighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.handler.FieldDefaultsUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * Modified copy of {@code com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl}
 *
 * @author Suburban Squirrel
 * @version 1.0.29
 * @since 1.0.29
 */
final public class LombokHighlightVisitor extends JavaElementVisitor implements HighlightVisitor {
  private static final boolean CHECK_ELEMENT_LEVEL = ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isInternal();
  private HighlightInfoHolder myHolder;
  private PsiFile myFile;
  private LanguageLevel myLanguageLevel;
  // map codeBlock->List of PsiReferenceExpression of extra initialization of final variable
  private final Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> myFinalVarProblems = new THashMap<PsiElement, Collection<ControlFlowUtil.VariableInfo>>();
  // map codeBlock->List of PsiReferenceExpression of uninitialized final variables
  private final Map<PsiElement, Collection<PsiReferenceExpression>> myUninitializedVarProblems = new THashMap<PsiElement, Collection<PsiReferenceExpression>>();

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return !InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file) && file instanceof PsiJavaFile;
  }

  @Override
  public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
    this.myFile = file;
    this.myHolder = CHECK_ELEMENT_LEVEL ? new CheckLevelHighlightInfoHolder(file, holder) : holder;
    try {
      myLanguageLevel = PsiUtil.getLanguageLevel(file);
      action.run();
    } finally {
      myFinalVarProblems.clear();
      myUninitializedVarProblems.clear();
      myFile = null;
      myHolder = null;
    }
    return true;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (CHECK_ELEMENT_LEVEL) {
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(element);
      element.accept(this);
      ((CheckLevelHighlightInfoHolder)myHolder).enterLevel(null);
    } else {
      element.accept(this);
    }
  }

  @Override
  public void visitField(PsiField field) {
    if (field.hasModifierProperty(PsiModifier.FINAL)) return;
    super.visitField(field);
    myHolder.add(FieldDefaultsUtil.checkFinalFieldInitialized(field));
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    JavaResolveResult result;
    JavaResolveResult[] results;
    try {
      results = resolveOptimised(expression);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    catch (IndexNotReadyException e) {
      return;
    }
    PsiElement resolved = result.getElement();

    if (resolved instanceof PsiVariable && resolved.getContainingFile() == expression.getContainingFile()) {
      if (!myHolder.hasErrorResults()) {
        try {
          myHolder.add(FieldDefaultsUtil.checkVariableInitializedBeforeUsage(expression, (PsiVariable) resolved, myUninitializedVarProblems, myFile));
        } catch (IndexNotReadyException ignored) {}
      }
      if (!((PsiVariable) resolved).hasInitializer() && PsiFieldUtil.isFinalByAnnotation((PsiVariable) resolved)) {
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableMightAlreadyHaveBeenAssignedTo((PsiVariable) resolved, expression, myFinalVarProblems));
        }
        if (!myHolder.hasErrorResults()) {
          myHolder.add(HighlightControlFlowUtil.checkFinalVariableInitializedInLoop(expression, resolved));
        }
      }
    }
  }

  @Override
  public void visitExpression(PsiExpression expression) {
    ProgressManager.checkCanceled(); // visitLiteralExpression is invoked very often in array initializers

//    super.visitExpression(expression);
    if (!myHolder.hasErrorResults()) myHolder.add(FieldDefaultsUtil.checkCannotWriteToFinal(expression, myFile));
  }

  @Override
  public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
    JavaResolveResult result;
    try {
      result = resolveOptimised(reference);
    } catch (IndexNotReadyException e) {
      return;
    }

    final PsiElement refName = reference.getReferenceNameElement();
    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return;

    final PsiElement resolved = result.getElement();
    if (!(resolved instanceof PsiField)) return;

    myHolder.add(FieldDefaultsUtil.checkVariableMustBeFinal((PsiField) resolved, reference, myLanguageLevel));    // todo nah?
  }

  @NotNull @Override public HighlightVisitor clone() {
    return new LombokHighlightVisitor();
  }

  @Override public int order() { return 4; }

  @NotNull
  private JavaResolveResult[] resolveOptimised(@NotNull PsiReferenceExpression expression) {
    JavaResolveResult[] results;
    if (expression instanceof PsiReferenceExpressionImpl) {
      PsiReferenceExpressionImpl referenceExpression = (PsiReferenceExpressionImpl) expression;
      results = JavaResolveUtil.resolveWithContainingFile(referenceExpression, PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE, true, true, myFile);
    } else {
      results = expression.multiResolve(true);
    }
    return results;
  }

  @NotNull
  private JavaResolveResult resolveOptimised(@NotNull PsiJavaCodeReferenceElement ref) {
    JavaResolveResult result;
    if (ref instanceof PsiReferenceExpressionImpl) {
      PsiReferenceExpressionImpl referenceExpression = (PsiReferenceExpressionImpl)ref;
      JavaResolveResult[] results = JavaResolveUtil.resolveWithContainingFile(referenceExpression, PsiReferenceExpressionImpl.OurGenericsResolver.INSTANCE,
                                                                              true, true, myFile);
      result = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    } else {
      result = ref.advancedResolve(true);
    }
    return result;
  }
}
