/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.plushnikov.intellij.plugin.codeInsight.completion;

import java.util.Map;
import java.util.Set;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
* @author peter
*/
public class SameSignatureCallParametersProviderEx extends CompletionProvider<CompletionParameters> {
  static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT =
    psiElement().beforeLeaf(psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(
      psiElement(PsiReferenceExpression.class).withParent(
        psiElement(PsiExpressionList.class).withParent(PsiCall.class)));

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    final PsiCall methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiCall.class);
    assert methodCall != null;
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

    PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    while (container != null) {
      for (final Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
        if (container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1) {
          PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
          if (from != null) {
            result.addElement(createParametersLookupElement(from, methodCall, candidate.first));
          }
        }
      }

      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class);

    }
  }

  private static LookupElement createParametersLookupElement(final PsiMethod takeParametersFrom, PsiElement call, PsiMethod invoked) {
    final PsiParameter[] parameters = takeParametersFrom.getParameterList().getParameters();
    final String lookupString = StringUtil.join(parameters, psiParameter -> psiParameter.getName(), ", ");

    final int w = PlatformIcons.PARAMETER_ICON.getIconWidth();
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(PlatformIcons.PARAMETER_ICON, 0, 2*w/5, 0);
    icon.setIcon(PlatformIcons.PARAMETER_ICON, 1);

    LookupElementBuilder element = LookupElementBuilder.create(lookupString).withIcon(icon);
    if (PsiTreeUtil.isAncestor(takeParametersFrom, call, true)) {
      element = element.withInsertHandler(new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          context.commitDocument();
          for (PsiParameter parameter : CompletionUtil.getOriginalOrSelf(takeParametersFrom).getParameterList().getParameters()) {
            VariableLookupItem.makeFinalIfNeeded(context, parameter);
          }
        }
      });
    }
    element.putUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS, Boolean.TRUE);

    return TailTypeDecorator.withTail(element, ExpectedTypesProvider.getFinalCallParameterTailType(call, invoked.getReturnType(), invoked));
  }

  private static Set<Pair<PsiMethod, PsiSubstitutor>> getCallCandidates(PsiCall expression) {
    Set<Pair<PsiMethod, PsiSubstitutor>> candidates = ContainerUtil.newLinkedHashSet();
    JavaResolveResult[] results;
    if (expression instanceof PsiMethodCallExpression) {
      results = ((PsiMethodCallExpression)expression).getMethodExpression().multiResolve(false);
    } else {
      results = new JavaResolveResult[]{expression.resolveMethodGenerics()};
    }

    PsiMethod toExclude = ExpressionUtils.isConstructorInvocation(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class)
                                                                              : null;

    for (final JavaResolveResult candidate : results) {
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod) {
        final PsiClass psiClass = ((PsiMethod)element).getContainingClass();
        if (psiClass != null) {
          for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod)element).getName(), true)) {
            if (overload.first != toExclude) {
              candidates.add(Pair.create(overload.first, candidate.getSubstitutor().putAll(overload.second)));
            }
          }
          break;
        }
      }
    }
    return candidates;
  }


  @Nullable
  private static PsiMethod getMethodToTakeParametersFrom(PsiMethod place, PsiMethod invoked, PsiSubstitutor substitutor) {
    if (PsiSuperMethodUtil.isSuperMethod(place, invoked)) {
      return place;
    }

    Map<String, PsiType> requiredNames = ContainerUtil.newHashMap();
    final PsiParameter[] parameters = place.getParameterList().getParameters();
    final PsiParameter[] callParams = invoked.getParameterList().getParameters();
    if (callParams.length > parameters.length) {
      return null;
    }

    final boolean checkNames = invoked.isConstructor();
    boolean sameTypes = true;
    for (int i = 0; i < callParams.length; i++) {
      PsiParameter callParam = callParams[i];
      PsiParameter parameter = parameters[i];
      requiredNames.put(callParam.getName(), substitutor.substitute(callParam.getType()));
      if (checkNames && !Comparing.equal(parameter.getName(), callParam.getName()) ||
          !Comparing.equal(parameter.getType(), substitutor.substitute(callParam.getType()))) {
        sameTypes = false;
      }
    }

    if (sameTypes && callParams.length == parameters.length) {
      return place;
    }

    for (PsiParameter parameter : parameters) {
      PsiType type = requiredNames.remove(parameter.getName());
      if (type != null && !parameter.getType().equals(type)) {
        return null;
      }
    }

    return requiredNames.isEmpty() ? invoked : null;
  }
}
