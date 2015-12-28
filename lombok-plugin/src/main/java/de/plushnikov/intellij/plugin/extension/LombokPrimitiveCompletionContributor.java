package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.ProcessingContext;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import static com.intellij.psi.util.PsiTreeUtil.findChildrenOfType;
import static com.intellij.psi.util.PsiTreeUtil.getParentOfType;
import static com.siyeh.ig.psiutils.ClassUtils.getContainingClass;
import static com.siyeh.ig.psiutils.ClassUtils.isPrimitive;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.createExtensionMethod;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.isInExtensionScope;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtendingMethods;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.isAnnotatedWith;


/**
 * @author Suburban Squirrel
 * @version 1.0.7
 * @since 1.0.7
 */
public class LombokPrimitiveCompletionContributor extends CompletionContributor {
  public LombokPrimitiveCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        PsiReferenceExpression expression = getParentOfType(parameters.getPosition(), PsiReferenceExpression.class);
        Iterator<PsiExpression> iterator = findChildrenOfType(expression, PsiExpression.class).iterator();
        if (!iterator.hasNext()) return;

        PsiType callType = iterator.next().getType();
        if (callType == null || !isPrimitive(callType)) return;

        PsiClass classCall = getContainingClass(parameters.getPosition());
        if (classCall == null || !isAnnotatedWith(classCall, ExtensionMethod.class)) return;

      // get methods for this type
        for (PsiMethod psiMethod : getExtendingMethods(classCall)) {
            PsiType paramType = getType(psiMethod.getParameterList().getParameters()[0].getType(), psiMethod);
            if (paramType.isAssignableFrom(callType) && isInExtensionScope(classCall)) {
              result.addElement(new JavaMethodCallElement(createExtensionMethod(psiMethod, null)));
            }
        }
      }
    });
  }
}
