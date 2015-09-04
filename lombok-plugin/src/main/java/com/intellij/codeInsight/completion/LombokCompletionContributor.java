package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiQualifiedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.psi.util.PsiTypesUtil.getPsiClass;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getExtendingMethods;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.getType;
import static de.plushnikov.intellij.plugin.handler.ExtensionMethodUtil.isInExtensionScope;
import static de.plushnikov.intellij.plugin.handler.FieldDefaultsUtil.isAccessible;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.isAnnotatedWith;
import static de.plushnikov.intellij.plugin.util.PsiClassUtil.getAllParents;

/**
 * Replace JavaCompletionContributor, because he placed before {@code com.intellij.codeInsight.completion.JavaCompletionContributor}
 *
 * @author Suburban Squirrel
 * @version 0.8.6
 * @since 0.8.6
 */
public class LombokCompletionContributor extends JavaCompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.LombokCompletionContributor");
  private static final PsiJavaElementPattern.Capture<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT = psiElement().afterLeaf(".").insideStarting(psiExpressionStatement());
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR = psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME = or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
      psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL = psiElement().afterLeaf(psiElement().withElementType(elementType()
      .oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE = psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  private static final PsiElementPattern.Capture<PsiElement> IN_TYPE_ARGS = PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(PsiReferenceParameterList.class));
  private static final ElementPattern SWITCH_LABEL = psiElement().withSuperParent(2, psiElement(PsiSwitchLabelStatement.class).withSuperParent(2,
      psiElement(PsiSwitchStatement.class).with(new PatternCondition<PsiSwitchStatement>("enumExpressionType") {
        @Override
        public boolean accepts(@NotNull PsiSwitchStatement psiSwitchStatement, ProcessingContext context) {
          final PsiExpression expression = psiSwitchStatement.getExpression();
          if (expression == null) return false;
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
          return aClass != null && aClass.isEnum();
        }
      })
  ));
  static final PsiElementPattern.Capture<PsiElement> LAMBDA = PlatformPatterns.psiElement().with(new PatternCondition<PsiElement>("LAMBDA_CONTEXT") {
    @Override
    public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
      final PsiElement rulezzRef = element.getParent();
      return rulezzRef != null &&
             rulezzRef instanceof PsiReferenceExpression &&
             ((PsiReferenceExpression)rulezzRef).getQualifier() == null &&
             LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
    }});

  final public static class LombokElementFilter implements ElementFilter {
    public static final ElementFilter INSTANCE = new LombokElementFilter();

    private LombokElementFilter() {}

    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
      if (element instanceof PsiMethod) return filterExtensionMethods((PsiMethod) element, context);
      if (element instanceof PsiField) return filterFieldDefault((PsiField) element, context);
      return true;
    }

    /**
     * also manual handle access modifiers
     */
    private boolean filterFieldDefault(@NotNull PsiField field, @Nullable PsiElement context) {
      return context == null || isAccessible(field, context);

    }

    private boolean filterExtensionMethods(@NotNull PsiMethod method, @Nullable PsiElement context) {
      if(context == null) return true;
      PsiClass containingClass = ClassUtils.getContainingClass(context);
      if (containingClass == null) return true;

      PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(context.getContainingFile().findElementAt(context.getTextOffset()), PsiReferenceExpression.class);
      if(expression == null) return true;

      PsiType type = getCallType(expression, containingClass);
      if (type == null) return true;

      boolean result = true;
      for (PsiMethod psiMethod : getExtendingMethods(containingClass)) {
        if (psiMethod.getName().equals(method.getName())) {
          PsiType paramType = getType(psiMethod.getParameterList().getParameters()[0].getType(), psiMethod);
          if (paramType.isAssignableFrom(type) && isInExtensionScope(containingClass)) {
            return true;
          }
          result = false;
        }
      }

      return result;
    }

    @Override
    public String toString() {
      return "true(hc)";
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return true;       // all PsiElement, because we not need this check
    }

    @Nullable
    public static PsiType getCallType(@NotNull PsiExpression expression, @NotNull PsiClass currentClass) {
      List<PsiJavaToken> tokens = new ArrayList<PsiJavaToken>(PsiTreeUtil.findChildrenOfType(expression, PsiJavaToken.class));
      PsiJavaToken dot = null;

      for (int i = tokens.size() - 1; i >= 0; i--) {
        if (tokens.get(i).getTokenType() == JavaTokenType.DOT) {
          dot = tokens.get(i);
          break;
        }
      }
      PsiElement callSibling = (dot != null ? dot.getPrevSibling() : null);

      return callSibling instanceof PsiExpression ? ((PsiExpression) callSibling).getType() : PsiTypesUtil.getClassType(currentClass);
    }
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    super.beforeCompletion(context);
    if (context.getDummyIdentifier() == null) return;

    try {
      Field dummyIdentifierChanger = context.getClass().getDeclaredField("dummyIdentifierChanger");
      dummyIdentifierChanger.setAccessible(true);
      dummyIdentifierChanger.set(context, null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet _result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

    final PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (AFTER_NUMBER_LITERAL.accepts(position) || UNEXPECTED_REFERENCE_AFTER_DOT.accepts(position)) {
      _result.stopHere();
      return;
    }

    final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !isAfterPrimitiveOrArrayType(position)) {
      addExpectedTypeMembers(parameters, result);
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    final InheritorsHolder inheritors = new InheritorsHolder(result);
    if (IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProviderEx(false, inheritors){
        @Override
        public void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext processingContext, @NotNull CompletionResultSet resultSet) {
          super.addCompletions(parameters, processingContext, resultSet);
        }
      }.addCompletions(parameters, new ProcessingContext(), result);
    }

    if (LAMBDA.accepts(parameters.getPosition())) {
      result.addAllElements(getLambdaVariants(parameters, true));
    }

    PrefixMatcher matcher = result.getPrefixMatcher();
    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, inheritors);
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      result.addElement(LookupElementBuilder.create("*"));
    }

    addKeywords(parameters, result);

    Set<String> usedWords = addReferenceVariants(parameters, result, inheritors);

    if (psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    JavaGenerateMemberCompletionContributor.fillCompletionVariants(parameters, result);

    addAllClasses(parameters, result, inheritors);

    final PsiElement parent = position.getParent();
    if (parent instanceof PsiReferenceExpression && !((PsiReferenceExpression)parent).isQualified() && parameters.isExtendedCompletion() && StringUtil.isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }
    result.stopHere();
  }

  static List<LookupElement> getLambdaVariants(@NotNull CompletionParameters parameters, boolean prioritize) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile())) return Collections.emptyList();

    List<LookupElement> result = ContainerUtil.newArrayList();
    for (ExpectedTypeInfo expectedType : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (functionalInterfaceMethod != null) {
          PsiParameter[] params = new PsiParameter[0];
          final PsiElement originalPosition = parameters.getPosition();
          final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(functionalInterfaceMethod, PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
          if (!functionalInterfaceMethod.hasTypeParameters()) {
            params = functionalInterfaceMethod.getParameterList().getParameters();
            final Project project = functionalInterfaceMethod.getProject();
            final JVMElementFactory jvmElementFactory = JVMElementFactories.getFactory(originalPosition.getLanguage(), project);
            final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
            if (jvmElementFactory != null) {
              params = GenerateMembersUtil.overriddenParameters(params, jvmElementFactory, javaCodeStyleManager, substitutor, originalPosition);
            }

            String paramsString =
              params.length == 1 ? getParamName(params[0], javaCodeStyleManager, originalPosition) : "(" + StringUtil.join(params, parameter -> {
                return getParamName(parameter, javaCodeStyleManager, originalPosition);
              }, ",") + ")";

            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) JavaPsiFacade.getElementFactory(project)
              .createExpressionFromText(paramsString + " -> {}", null);
            lambdaExpression = (PsiLambdaExpression)codeStyleManager.reformat(lambdaExpression);
            paramsString = lambdaExpression.getParameterList().getText();
            final LookupElementBuilder builder =
              LookupElementBuilder.create(functionalInterfaceMethod, paramsString).withPresentableText(paramsString + " -> {}").withInsertHandler((context, item) -> {
                final Editor editor = context.getEditor();
                EditorModificationUtil.insertStringAtCaret(editor, " -> ");
              }).withIcon(AllIcons.Nodes.AnonymousClass);
            LookupElement lambdaElement = builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
            if (prioritize) {
              lambdaElement = PrioritizedLookupElement.withPriority(lambdaElement, 1);
            }
            result.add(lambdaElement);
          }

          if (params.length == 1) {
            final PsiType expectedReturnType = substitutor.substitute(functionalInterfaceMethod.getReturnType());
            if (expectedReturnType != null) {
              final PsiClass paramClass = PsiUtil.resolveClassInClassTypeOnly(params[0].getType());
              if (paramClass != null && !paramClass.hasTypeParameters()) {
                final Set<String> visited = new HashSet<String>();
                for (PsiMethod psiMethod : paramClass.getAllMethods()) {
                  final PsiType returnType = psiMethod.getReturnType();
                  if (returnType != null &&
                      psiMethod.getParameterList().getParametersCount() == 0 &&
                      visited.add(psiMethod.getName()) &&
                      !psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
                      JavaResolveUtil.isAccessible(psiMethod, null, psiMethod.getModifierList(), originalPosition, null, null) &&
                      TypeConversionUtil.isAssignable(expectedReturnType, returnType)) {
                    LookupElement methodRefLookupElement = LookupElementBuilder
                      .create(psiMethod)
                      .withPresentableText(paramClass.getName() + "::" + psiMethod.getName())
                      .withInsertHandler((context, item) -> {
                        final int startOffset = context.getStartOffset();
                        final Document document = context.getDocument();
                        final PsiFile file = context.getFile();
                        document.insertString(startOffset, "::");
                        JavaCompletionUtil.insertClassReference(paramClass, file, startOffset);
                      })
                      .withIcon(AllIcons.Nodes.AnonymousClass)
                      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
                    if (prioritize && psiMethod.getContainingClass() == paramClass) {
                      methodRefLookupElement = PrioritizedLookupElement.withPriority(methodRefLookupElement, 1);
                    }
                    result.add(methodRefLookupElement);
                  }
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static String getParamName(PsiParameter param, JavaCodeStyleManager javaCodeStyleManager, PsiElement originalPosition) {
    return javaCodeStyleManager.suggestUniqueVariableName(param.getName(), originalPosition, true);
  }

  static void addExpectedTypeMembers(CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getInvocationCount() <= 1) { // on second completion, StaticMemberProcessor will suggest those
      for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
        new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(false, result);
      }
    }
  }

  static boolean isAfterPrimitiveOrArrayType(PsiElement element) {
    return psiElement().withParent(
      psiReferenceExpression().withFirstChild(
        psiElement(PsiClassObjectAccessExpression.class).withLastChild(
          not(psiElement().withText(PsiKeyword.CLASS))))).accepts(element);
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement,
                                                      CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList) ObjectUtils.assertNotNull(pair).getParent();
    PsiAnnotation anno = (PsiAnnotation)parameterList.getParent();
    boolean showClasses = psiElement().afterLeaf("(").accepts(insertedElement);
    PsiClass annoClass = null;
    final PsiJavaCodeReferenceElement referenceElement = anno.getNameReferenceElement();
    if (referenceElement != null) {
      final PsiElement element = referenceElement.resolve();
      if (element instanceof PsiClass) {
        annoClass = (PsiClass)element;
        if (annoClass.findMethodsByName("value", false).length == 0) {
          showClasses = false;
        }
      }
    }

    if (showClasses && insertedElement.getParent() instanceof PsiReferenceExpression) {
      final Set<LookupElement> set = JavaCompletionUtil.processJavaReference(
        insertedElement, (PsiJavaReference)insertedElement.getParent(), new ElementExtractorFilter(createAnnotationFilter(insertedElement)), JavaCompletionProcessor.Options.DEFAULT_OPTIONS, result.getPrefixMatcher(), parameters);
      for (final LookupElement element : set) {
        result.addElement(element);
      }
      addAllClasses(parameters, result, new InheritorsHolder(result));
    }

    if (annoClass != null) {
      final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods: for (PsiMethod method : annoClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod)) continue;

        final String attrName = method.getName();
        for (PsiNameValuePair existingAttr : existingPairs) {
          if (PsiTreeUtil.isAncestor(existingAttr, insertedElement, false)) break;
          if (Comparing.equal(existingAttr.getName(), attrName) ||
              PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) continue methods;
        }
        LookupElementBuilder element = LookupElementBuilder.createWithIcon(method).withInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            final Editor editor = context.getEditor();
            TailType.EQ.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);

            context.commitDocument();
            PsiAnnotationParameterList paramList =
              PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiAnnotationParameterList.class, false);
            if (paramList != null && paramList.getAttributes().length > 0 && paramList.getAttributes()[0].getName() == null) {
              int valueOffset = paramList.getAttributes()[0].getTextRange().getStartOffset();
              context.getDocument().insertString(valueOffset, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
              TailType.EQ.processTail(editor, valueOffset + PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.length());
            }
          }
        });

        PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)method).getDefaultValue();
        if (defaultValue != null) {
          element = element.withTailText(" default " + defaultValue.getText(), true);
        }

        result.addElement(element);
      }
    }
  }

  private static ElementFilter createAnnotationFilter(PsiElement position) {
    OrFilter orFilter = new OrFilter(ElementClassFilter.CLASS,
                                   ElementClassFilter.PACKAGE_FILTER,
                                   new AndFilter(new ClassFilter(PsiField.class),
                                                 new ModifierFilter(PsiModifier.STATIC, PsiModifier.FINAL)));
    if (psiElement().insideStarting(psiNameValuePair()).accepts(position)) {
      orFilter.addFilter(new ClassFilter(PsiAnnotationMethod.class) {
        @Override
        public boolean isAcceptable(Object element, PsiElement context) {
          return element instanceof PsiAnnotationMethod && PsiUtil.isAnnotationMethod((PsiElement)element);
        }
      });
    }
    return orFilter;
  }

  private static void addKeywords(CompletionParameters parameters, final CompletionResultSet result) {
    Consumer<LookupElement> noMiddleMatches = new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        if (element.getLookupString().startsWith(result.getPrefixMatcher().getPrefix())) {
          result.addElement(element);
        }
      }
    };

    de.plushnikov.intellij.plugin.codeInsight.completion.JavaKeywordCompletion.addKeywords(parameters, noMiddleMatches);
  }

  private static boolean hasFieldDefaults(PsiReferenceExpression reference) {
    if (reference == null) return false;
    PsiElement callElement = reference.resolve();

    PsiClass annotatedClass = null;
    if (callElement instanceof PsiLocalVariable) annotatedClass = getPsiClass(((PsiLocalVariable) callElement).getType());
    if (callElement instanceof PsiField) annotatedClass = ((PsiField) callElement).getContainingClass();
    if (callElement instanceof PsiClass) annotatedClass = (PsiClass) callElement;

    return annotatedClass != null && PsiAnnotationUtil.findAnnotation(annotatedClass, FieldDefaults.class) != null;
  }

  private static Set<String> addReferenceVariants(final CompletionParameters parameters, CompletionResultSet result, final InheritorsHolder inheritors) {
    final Set<String> usedWords = new HashSet<String>();
    final PsiElement position = parameters.getPosition();
    final boolean first = parameters.getInvocationCount() <= 1;
    final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
    final boolean isAfterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    final boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
    LegacyCompletionContributor.processReferences(parameters, result, new PairConsumer<PsiReference, CompletionResultSet>() {
      @Override
      public void consume(final PsiReference reference, final CompletionResultSet result) {
        if (reference instanceof PsiJavaReference) {
        // check access
          boolean checkAccess = first;
          if (reference instanceof PsiReferenceExpression) {
            PsiElement qualifier = ((PsiReferenceExpression) reference).getQualifier();
            if (qualifier instanceof PsiReferenceExpression) {
              checkAccess = !hasFieldDefaults((PsiReferenceExpression) qualifier);           // if found annotation try see all elements
            }

            PsiClass psiClass = null;
            if (qualifier == null) {
              psiClass = PsiTreeUtil.getParentOfType(((PsiReferenceExpression) reference).getReferenceNameElement(), PsiClass.class);
            }
            if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
              psiClass = getPsiClass(((PsiQualifiedExpression) qualifier).getType());
            }

            if (psiClass != null) {
              checkAccess = !isAnnotatedWith(psiClass, FieldDefaults.class);
              if (checkAccess == first) {
                for (PsiClass parent : getAllParents(psiClass)) {
                  checkAccess = !isAnnotatedWith(parent, FieldDefaults.class);
                  if (checkAccess != first) break;
                }
              }

            }
          }

          final ElementFilter filter = getReferenceFilter(position);
          if (filter != null) {
            final PsiFile originalFile = parameters.getOriginalFile();
            JavaCompletionProcessor.Options options =
                JavaCompletionProcessor.Options.DEFAULT_OPTIONS
                    .withCheckAccess(checkAccess)
                    .withFilterStaticAfterInstance(first)
                    .withShowInstanceInStaticContext(!first);
            for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                (PsiJavaReference) reference,
                new ElementExtractorFilter(filter),
                options,
                result.getPrefixMatcher(), parameters)) {
              if (inheritors.alreadyProcessed(element)) {
                continue;
              }

              if (isSwitchLabel) {
                result.addElement(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':')));
              } else {
                final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
                if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
                    !((PsiJavaCodeReferenceCodeFragment) originalFile).isClassesAccepted() && item != null) {
                  item.setTailType(TailType.NONE);
                }

                result.addElement(element);
              }
            }
          }
          return;
        }
        if (reference instanceof PsiLabelReference) {
          processLabelReference(result, (PsiLabelReference) reference);
          return;
        }

        final Object[] variants = reference.getVariants();
        if (variants == null) {
          LOG.error("Reference=" + reference);
        }
        for (Object completion : variants) {
          if (completion == null) {
            LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
          }
          if (completion instanceof LookupElement && !inheritors.alreadyProcessed((LookupElement) completion)) {
            usedWords.add(((LookupElement) completion).getLookupString());
            result.addElement((LookupElement) completion);
          } else if (completion instanceof PsiClass) {
            for (JavaPsiClassReferenceElement item : JavaClassNameCompletionContributor.createClassLookupItems((PsiClass) completion, isAfterNew,
                JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER, new Condition<PsiClass>() {
                  @Override
                  public boolean value(PsiClass psiClass) {
                    return !inheritors.alreadyProcessed(psiClass) && JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
                  }
                })) {
              usedWords.add(item.getLookupString());
              result.addElement(item);
            }

          } else {
            //noinspection deprecation
            LookupElement element = LookupItemUtil.objectToLookupItem(completion);
            usedWords.add(element.getLookupString());
            result.addElement(element);
          }
        }
      }
    });
    return usedWords;
  }

  public static void processLabelReference(CompletionResultSet result, PsiLabelReference ref) {
    for (String s : ref.getVariants()) {
      result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
    }
  }

  @Nullable
  public static ElementFilter getReferenceFilter(PsiElement position) {
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(position);
    if (filter == TrueFilter.INSTANCE) {
      return LombokElementFilter.INSTANCE;              // replace filter
    }
    return filter;
  }
}
