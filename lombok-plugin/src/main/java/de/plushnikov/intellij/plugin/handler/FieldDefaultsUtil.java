package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiFieldUtil.isFinalByAnnotation;

/**
 * @author Suburban Squirrel
 * @version 1.0.29
 * @since 1.0.4
 */
final public class FieldDefaultsUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

// from com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil

  @Nullable
  public static HighlightInfo checkFinalFieldInitialized(PsiField field) {
    if (!isFinalByAnnotation(field)) return null;
    if (isFinalFieldInitialized(field)) return null;

    String description = JavaErrorMessages.message("variable.not.initialized", field.getName());
    TextRange range = HighlightNamesUtil.getFieldDeclarationTextRange(field);
    HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createCreateConstructorParameterFromFieldFix(field));
    QuickFixAction.registerQuickFixAction(highlightInfo, HighlightMethodUtil.getFixRange(field), QUICK_FIX_FACTORY.createInitializeFinalFieldInConstructorFix(field));

    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !containingClass.isInterface()) {
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(field, PsiModifier.FINAL, false, false));
    }
    QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createAddVariableInitializerFix(field));
    return highlightInfo;
  }

  private static boolean isFinalFieldInitialized(PsiField field) {
    if (field.hasInitializer()) {
      return true;
    }
    final boolean isFieldStatic = field.hasModifierProperty(PsiModifier.STATIC);
    final PsiClass aClass = field.getContainingClass();
    if (aClass != null) {
      // field might be assigned in the other field initializers
      if (isFieldInitializedInOtherFieldInitializer(aClass, field, isFieldStatic)) return true;
    }
    final PsiClassInitializer[] initializers;
    if (aClass != null) {
      initializers = aClass.getInitializers();
    } else {
      return false;
    }
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC) == isFieldStatic && variableDefinitelyAssignedIn(field, initializer.getBody())) return true;
    }
    if (isFieldStatic) {
      return false;
    } else {
      // instance field should be initialized at the end of the each constructor
      final PsiMethod[] constructors = aClass.getConstructors();

      if (constructors.length == 0) return false;
      nextConstructor:
      for (PsiMethod constructor : constructors) {
        PsiCodeBlock ctrBody = constructor.getBody();
        if (ctrBody == null) return false;
        final List<PsiMethod> redirectedConstructors = JavaHighlightUtil.getChainedConstructors(constructor);

        for (int j = 0; redirectedConstructors != null && j < redirectedConstructors.size(); j++) {
          PsiMethod redirectedConstructor = redirectedConstructors.get(j);
          final PsiCodeBlock body = redirectedConstructor.getBody();
          if (body != null && variableDefinitelyAssignedIn(field, body)) continue nextConstructor;
        }

        if (!ctrBody.isValid() || variableDefinitelyAssignedIn(field, ctrBody)) continue;
        return false;
      }
      return true;
    }
  }

  private static boolean isFieldInitializedInOtherFieldInitializer(final PsiClass aClass, PsiField field, final boolean fieldStatic) {
    PsiField[] fields = aClass.getFields();
    for (PsiField psiField : fields) {
      if (psiField != field && psiField.hasModifierProperty(PsiModifier.STATIC) == fieldStatic && variableDefinitelyAssignedIn(field, psiField)) return true;
    }
    return false;
  }

  /**
   * see JLS chapter 16
   * @return true if variable assigned (maybe more than once)
   */
  private static boolean variableDefinitelyAssignedIn(PsiVariable variable, PsiElement context) {
    try {
      ControlFlow controlFlow = getControlFlow(context);
      return ControlFlowUtil.isVariableDefinitelyAssigned(variable, controlFlow);
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  private static ControlFlow getControlFlow(PsiElement context) throws AnalysisCanceledException {
    LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    return ControlFlowFactory.getInstance(context.getProject()).getControlFlow(context, policy);
  }

  @Nullable
  public static HighlightInfo checkCannotWriteToFinal(PsiExpression expression, @NotNull PsiFile containingFile) {
    PsiReferenceExpression reference = null;
    boolean readBeforeWrite = false;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final PsiExpression left = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());

      if (left instanceof PsiReferenceExpression) reference = (PsiReferenceExpression) left;
      readBeforeWrite = assignmentExpression.getOperationTokenType() != JavaTokenType.EQ;
    } else if (expression instanceof PsiPostfixExpression) {
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPostfixExpression)expression).getOperand());
      final IElementType sign = ((PsiPostfixExpression)expression).getOperationTokenType();

      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) reference = (PsiReferenceExpression) operand;
      readBeforeWrite = true;
    } else if (expression instanceof PsiPrefixExpression) {
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(((PsiPrefixExpression)expression).getOperand());
      final IElementType sign = ((PsiPrefixExpression)expression).getOperationTokenType();

      if (operand instanceof PsiReferenceExpression && (sign == JavaTokenType.PLUSPLUS || sign == JavaTokenType.MINUSMINUS)) reference = (PsiReferenceExpression) operand;
      readBeforeWrite = true;
    }
    final PsiElement resolved = reference == null ? null : reference.resolve();
    PsiVariable variable = resolved instanceof PsiVariable ? (PsiVariable)resolved : null;
    if (variable == null || !isFinalByAnnotation(variable)) return null;
    final boolean canWrite = canWriteToFinal(variable, expression, reference, containingFile) && checkWriteToFinalInsideLambda(variable, reference) == null;
    if (readBeforeWrite || !canWrite) {
      final String name = variable.getName();
      String description = canWrite ? JavaErrorMessages.message("variable.not.initialized", name) : JavaErrorMessages.message("assignment.to.final.variable", name);
      final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference.getTextRange()).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createModifierListFix(variable, PsiModifier.FINAL, false, false));
      return highlightInfo;
    }

    return null;
  }

  private static HighlightInfo checkWriteToFinalInsideLambda(PsiVariable variable, PsiJavaCodeReferenceElement context) {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(context, PsiLambdaExpression.class);
    if (lambdaExpression != null && !PsiTreeUtil.isAncestor(lambdaExpression, variable, true)) {
      final PsiElement parent = variable.getParent();
      if (parent instanceof PsiParameterList && parent.getParent() == lambdaExpression) return null;

      if (!HighlightControlFlowUtil.isEffectivelyFinal(variable, lambdaExpression, context)) {
        final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(context)
          .descriptionAndTooltip("Variable used in lambda expression should be effectively final")
          .create();
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, lambdaExpression));
        return highlightInfo;
      }
    }
    return null;
  }

  private static boolean canWriteToFinal(PsiVariable variable, PsiExpression expression, final PsiReferenceExpression reference, @NotNull PsiFile containingFile) {
    if (variable.hasInitializer()) return false;
    if (variable instanceof PsiParameter) return false;
    PsiElement innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, expression);
    if (variable instanceof PsiField) {
      // if inside some field initializer
      if (HighlightUtil.findEnclosingFieldInitializer(expression) != null) return true;
      // assignment from within inner class is illegal always
      PsiField field = (PsiField)variable;
      if (innerClass != null && !containingFile.getManager().areElementsEquivalent(innerClass, field.getContainingClass())) return false;

      final PsiMember enclosingCtrOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expression);
      return enclosingCtrOrInitializer != null && isSameField(variable, enclosingCtrOrInitializer, field, reference, containingFile);
    }
    if (variable instanceof PsiLocalVariable) {
      boolean isAccessedFromOtherClass = innerClass != null;
      if (isAccessedFromOtherClass) return false;
    }
    return true;
  }

  private static boolean isSameField(final PsiVariable variable,
                                     final PsiMember enclosingCtrOrInitializer,
                                     final PsiField field,
                                     final PsiReferenceExpression reference, @NotNull PsiFile containingFile) {

    if (!containingFile.getManager().areElementsEquivalent(enclosingCtrOrInitializer.getContainingClass(), field.getContainingClass())) return false;
    PsiExpression qualifierExpression = reference.getQualifierExpression();
    return qualifierExpression == null || qualifierExpression instanceof PsiThisExpression;
  }

  @Nullable
  public static HighlightInfo checkVariableMustBeFinal(@NotNull PsiField variable, @NotNull PsiJavaCodeReferenceElement context, @NotNull LanguageLevel languageLevel) {
    if (!isFinalByAnnotation(variable)) return null;
    final PsiElement innerClass = HighlightControlFlowUtil.getInnerClassVariableReferencedFrom(variable, context);
    if (innerClass instanceof PsiClass) {
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && HighlightControlFlowUtil.isEffectivelyFinal(variable, innerClass, context)) return null;
      final String description = JavaErrorMessages.message("variable.must.be.final", context.getText());

      final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(context).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createVariableAccessFromInnerClassFix(variable, innerClass));
      return highlightInfo;
    }
    return checkWriteToFinalInsideLambda(variable, context);
  }

  @Nullable
  public static HighlightInfo checkVariableInitializedBeforeUsage(PsiReferenceExpression expression, PsiVariable variable,
                                                                  Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems, @NotNull PsiFile containingFile) {
    if (!isFinalByAnnotation(variable)) return null;
    return HighlightControlFlowUtil.checkVariableInitializedBeforeUsage(expression, variable, uninitializedVarProblems, containingFile);
  }

  private static boolean inInnerClass(PsiElement element, PsiClass containingClass, @NotNull PsiFile containingFile) {
    while (element != null) {
      if (element instanceof PsiClass) return !containingFile.getManager().areElementsEquivalent(element, containingClass);
      element = element.getParent();
    }
    return false;
  }

// end copy past with changes block todo

  /**
   * Check accessible for this field in current place (include @FieldDefaults changes)
   */
  public static boolean isAccessible(@NotNull PsiField field, @NotNull PsiElement place) {
    PsiClass contextClass = findContextForPlace(place);                                                                                     // find context
    PsiModifierList modifierList = field.getModifierList();
    PsiAnnotation annotation = findAnnotation(field.getContainingClass(), FieldDefaults.class.getCanonicalName());

    if (!field.hasModifierProperty(PsiModifier.PUBLIC) && !field.hasModifierProperty(PsiModifier.PRIVATE) && !field.hasModifierProperty(PsiModifier.PROTECTED) && annotation != null) {
      String access = LombokProcessorUtil.convertAccessLevelToJavaString(PsiAnnotationUtil.getAnnotationValue(annotation, "level", String.class));

      if (!"".equals(access)) {
        List<String> modifiers = new ArrayList<String>(2){{ add(access); }};
        if (field.hasModifierProperty(PsiModifier.STATIC)) modifiers.add(PsiModifier.STATIC);

        modifierList = new LombokLightModifierList(field.getManager(), field.getLanguage(), modifiers.toArray(new String[modifiers.size()]));
      }
    }

    return JavaResolveUtil.isAccessible(field, field.getContainingClass(), modifierList, place, contextClass, null);
  }

  /**
   * Copy peace from inner IDEA method (JavaCompletionProcessor (constructor))
   */
  private static PsiClass findContextForPlace(PsiElement context) {
    PsiClass contextClass = null;
    PsiElement elementParent = context.getContext();
//    if (!(context instanceof PsiReferenceExpression)) elementParent = context.getContext();           // for annotator use

    if (elementParent instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression) elementParent).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiJavaCodeReferenceElement qSuper = ((PsiSuperExpression) qualifier).getQualifier();
        if (qSuper == null) {
          contextClass = JavaResolveUtil.getContextClass(context);
        } else {
          final PsiElement target = qSuper.resolve();
          contextClass = target instanceof PsiClass ? (PsiClass) target : null;
        }
      } else if (qualifier != null) {
        contextClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (qualifier.getType() == null && qualifier instanceof PsiJavaCodeReferenceElement) {
          final PsiElement target = ((PsiJavaCodeReferenceElement) qualifier).resolve();
          if (target instanceof PsiClass) {
            contextClass = (PsiClass) target;
          }
        }
      } else {
        contextClass = JavaResolveUtil.getContextClass(context);
      }
    }
    return contextClass;
  }

  private static boolean isFinal(@NotNull PsiField psiField) {
    return psiField.hasModifierProperty(PsiModifier.FINAL);
  }
}
