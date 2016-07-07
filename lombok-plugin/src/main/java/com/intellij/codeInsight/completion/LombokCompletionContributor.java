package com.intellij.codeInsight.completion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.simple.RParenthTailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiQualifiedExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.psi.util.PsiTypesUtil.getPsiClass;
import static com.intellij.util.ObjectUtils.assertNotNull;
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
  private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));
  protected static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
    psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
      .andNot(JavaKeywordCompletion.AFTER_DOT);

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

  private static class IndentingDecorator extends LookupElementDecorator<LookupElement> {
    public IndentingDecorator(LookupElement delegate) {
      super(delegate);
    }

    @Override
    public void handleInsert(InsertionContext context) {
      super.handleInsert(context);
      Project project = context.getProject();
      Document document = context.getDocument();
      int lineStartOffset = DocumentUtil.getLineStartOffset(context.getStartOffset(), document);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      CodeStyleManager.getInstance(project).adjustLineIndent(context.getFile(), lineStartOffset);
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
    JavaCompletionSession session = new JavaCompletionSession(result);

    if (ANNOTATION_ATTRIBUTE_NAME.accepts(position) && !isAfterPrimitiveOrArrayType(position)) {
      Method addExpectedTypeMembers = getMethod(JavaKeywordCompletion.class, void.class, "addExpectedTypeMembers", CompletionParameters.class, CompletionResultSet.class);
      invokeMethod(addExpectedTypeMembers, parameters, result);
      Method addPrimitiveTypes = getMethod(JavaKeywordCompletion.class, void.class, "addPrimitiveTypes", Consumer.class, PsiElement.class, JavaCompletionSession.class);
      invokeMethod(addPrimitiveTypes, result, position, session);
      completeAnnotationAttributeName(result, position, parameters);
      result.stopHere();
      return;
    }

    PrefixMatcher matcher = result.getPrefixMatcher();
    PsiElement parent = position.getParent();

    if (position instanceof PsiIdentifier) {
      addIdentifierVariants(parameters, position, result, matcher, parent, session);
    }

    Set<String> usedWords = addReferenceVariants(parameters, result, session);

    if (psiElement().inside(PsiLiteralExpression.class).accepts(position)) {
      PsiReference reference = position.getContainingFile().findReferenceAt(parameters.getOffset());
      if (reference == null || reference.isSoft()) {
        WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
      }
    }

    if (position instanceof PsiIdentifier) {
      JavaGenerateMemberCompletionContributor.fillCompletionVariants(parameters, result);
    }

    addAllClasses(parameters, result, session);

    if (position instanceof PsiIdentifier) {
      Method addFunctionalVariants = getMethod(FunctionalExpressionCompletionProvider.class, void.class, "addFunctionalVariants", CompletionParameters.class, boolean.class, boolean.class, CompletionResultSet.class);
      invokeMethod(addFunctionalVariants, parameters, false, true, result);
    }

    if (parent instanceof PsiReferenceExpression && !((PsiReferenceExpression)parent).isQualified() && parameters.isExtendedCompletion() && StringUtil.isNotEmpty(matcher.getPrefix())) {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }

    if (position instanceof PsiIdentifier && parent instanceof PsiReferenceExpression && !((PsiReferenceExpression)parent).isQualified() && parameters.isExtendedCompletion()
      && StringUtil.isNotEmpty(matcher.getPrefix()))
    {
      new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
    }
    result.stopHere();
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
      addAllClasses(parameters, result, new JavaCompletionSession(result));
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

  private static boolean hasFieldDefaults(PsiReferenceExpression reference) {
    if (reference == null) return false;
    PsiElement callElement = reference.resolve();

    PsiClass annotatedClass = null;
    if (callElement instanceof PsiLocalVariable) annotatedClass = getPsiClass(((PsiLocalVariable) callElement).getType());
    if (callElement instanceof PsiField) annotatedClass = ((PsiField) callElement).getContainingClass();
    if (callElement instanceof PsiClass) annotatedClass = (PsiClass) callElement;

    return annotatedClass != null && PsiAnnotationUtil.findAnnotation(annotatedClass, FieldDefaults.class) != null;
  }

  private static Set<String> addReferenceVariants(final CompletionParameters parameters, CompletionResultSet result, final JavaCompletionSession session) {
    final Set<String> usedWords = new HashSet<String>();
    final PsiElement position = parameters.getPosition();
    final boolean first = parameters.getInvocationCount() <= 1;
    final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
    final boolean isAfterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
    final boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
    final PsiType[] expectedTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
    LegacyCompletionContributor.processReferences(parameters, result, (reference, result1) -> {
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

        ElementFilter filter = getReferenceFilter(position);
        if (filter != null) {
          if (INSIDE_CONSTRUCTOR.accepts(position) &&
              (parameters.getInvocationCount() <= 1 || CheckInitializedEx.isInsideConstructorCall(position))) {
            filter = new AndFilter(filter, new CheckInitializedEx(position));
          }
          final PsiFile originalFile = parameters.getOriginalFile();
          JavaCompletionProcessor.Options options =
            JavaCompletionProcessor.Options.DEFAULT_OPTIONS
              .withCheckAccess(checkAccess)
              .withFilterStaticAfterInstance(first)
              .withShowInstanceInStaticContext(!first);
          for (LookupElement element : JavaCompletionUtil.processJavaReference(position,
                                                                               (PsiJavaReference)reference,
                                                                               new ElementExtractorFilter(filter),
                                                                               options,
                                                                               result1.getPrefixMatcher(), parameters)) {
            if (session.alreadyProcessed(element)) {
              continue;
            }

            if (isSwitchLabel) {
              result1.addElement(new IndentingDecorator(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':'))));
            }
            else {
              final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
              if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
                  !((PsiJavaCodeReferenceCodeFragment)originalFile).isClassesAccepted() && item != null) {
                item.setTailType(TailType.NONE);
              }
              if (item instanceof JavaMethodCallElement) {
                JavaMethodCallElement call = (JavaMethodCallElement)item;
                final PsiMethod method = call.getObject();
                if (method.getTypeParameters().length > 0) {
                  final PsiType returned = TypeConversionUtil.erasure(method.getReturnType());
                  PsiType matchingExpectation = returned == null
                                                ? null
                                                : ContainerUtil.find(expectedTypes, type -> type.isAssignableFrom(returned));
                  if (matchingExpectation != null) {
                    call.setInferenceSubstitutor(SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, matchingExpectation), position);
                  }
                }
              }

              result1.addElement(element);
            }
          }
        }
        return;
      }
      if (reference instanceof PsiLabelReference) {
        processLabelReference(result1, (PsiLabelReference) reference);
        return;
      }

      final Object[] variants = reference.getVariants();
      //noinspection ConstantConditions
      if (variants == null) {
        LOG.error("Reference=" + reference);
      }
      for (Object completion : variants) {
        if (completion == null) {
          LOG.error("Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(variants));
        }
        if (completion instanceof LookupElement && !session.alreadyProcessed((LookupElement) completion)) {
          usedWords.add(((LookupElement) completion).getLookupString());
          result1.addElement((LookupElement) completion);
        } else if (completion instanceof PsiClass) {
          Condition<PsiClass> condition = psiClass -> !session.alreadyProcessed(psiClass) &&
                                                      JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
          for (JavaPsiClassReferenceElement item : JavaClassNameCompletionContributor.createClassLookupItems((PsiClass)completion,
                                                                                                             isAfterNew,
                                                                                                             JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                                                                                                             condition)) {
            usedWords.add(item.getLookupString());
            result1.addElement(item);
          }
        } else {
          //noinspection deprecation
          LookupElement element = LookupItemUtil.objectToLookupItem(completion);
          usedWords.add(element.getLookupString());
          result1.addElement(element);
        }
      }
    });
    return usedWords;
  }

  static void processLabelReference(CompletionResultSet result, PsiLabelReference ref) {
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

  private static void addIdentifierVariants(@NotNull CompletionParameters parameters,
                                            PsiElement position,
                                            final CompletionResultSet result,
                                            PrefixMatcher matcher, PsiElement parent, @NotNull JavaCompletionSession session) {
    if (TypeArgumentCompletionProviderEx.IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProviderEx(false, session).addCompletions(parameters, new ProcessingContext(), result);
    }

    Method addFunctionalVariants = getMethod(FunctionalExpressionCompletionProvider.class, void.class, "addFunctionalVariants", CompletionParameters.class, boolean.class, boolean.class, CompletionResultSet.class);
    invokeMethod(addFunctionalVariants, parameters, false, false, result);

    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, session);
    }

    if (IN_METHOD_RETURN_TYPE.accepts(position)) {
      addProbableReturnTypes(parameters, element -> {
        registerClassFromTypeElement(element, session);
        result.addElement(element);
      });
    }

    if (shouldSuggestCast(parameters)) {
      addCastVariants(parameters, element -> {
        registerClassFromTypeElement(element, session);
        result.addElement(PrioritizedLookupElement.withPriority(element, 1));
      });
    }

    if (parent instanceof PsiReferenceExpression) {
      final List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression)parent, true));
      Method decorateWithoutTypeCheck = getMethod(JavaSmartCompletionContributor.class, Consumer.class, "decorateWithoutTypeCheck", CompletionResultSet.class, Collection.class);
      CollectConversionEx.addCollectConversion((PsiReferenceExpression)parent, expected, (Consumer<LookupElement>) invokeMethod(decorateWithoutTypeCheck, result, expected));
    }

    if (IMPORT_REFERENCE.accepts(position)) {
      result.addElement(LookupElementBuilder.create("*"));
    }

    addKeywords(parameters, result, session);

    addExpressionVariants(parameters, position, result);
  }

  static void addProbableReturnTypes(@NotNull CompletionParameters parameters, final Consumer<LookupElement> consumer) {
    final PsiElement position = parameters.getPosition();
    PsiMethod method = PsiTreeUtil.getParentOfType(position, PsiMethod.class);
    assert method != null;

    final PsiTypeVisitor<PsiType> eachProcessor = new PsiTypeVisitor<PsiType>() {
      private Set<PsiType> myProcessed = ContainerUtil.newHashSet();

      @Nullable
      @Override
      public PsiType visitType(PsiType type) {
        if (myProcessed.add(type)) {
          int priority = type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? 1 : 1000 - myProcessed.size();
          consumer.consume(PrioritizedLookupElement.withPriority(PsiTypeLookupItem.createLookupItem(type, position), priority));
        }
        return type;
      }
    };
    for (PsiType type : getReturnTypeCandidates(method)) {
      eachProcessor.visitType(type);
      ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), ContainerUtil.<PsiType>newHashSet());
    }
  }

  private static PsiType[] getReturnTypeCandidates(@NotNull PsiMethod method) {
    PsiType lub = null;
    boolean hasVoid = false;
    for (PsiReturnStatement statement : PsiUtil.findReturnStatements(method)) {
      PsiExpression value = statement.getReturnValue();
      if (value == null) {
        hasVoid = true;
      } else {
        PsiType type = value.getType();
        if (lub == null) {
          lub = type;
        } else if (type != null) {
          lub = GenericsUtil.getLeastUpperBound(lub, type, method.getManager());
        }
      }
    }
    if (hasVoid && lub == null) {
      lub = PsiType.VOID;
    }
    if (lub instanceof PsiIntersectionType) {
      return ((PsiIntersectionType) lub).getConjuncts();
    }
    return lub == null ? PsiType.EMPTY_ARRAY : new PsiType[]{lub};
  }

  static boolean shouldSuggestCast(CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = getParenthesisOwner(position);
    if (parent instanceof PsiTypeCastExpression) return true;
    if (parent instanceof PsiParenthesizedExpression) {
      return parameters.getOffset() == position.getTextRange().getStartOffset();
    }
    return false;
  }

  static void addCastVariants(@NotNull CompletionParameters parameters, @NotNull Consumer<LookupElement> result) {
    if (!shouldSuggestCast(parameters)) return;

    PsiElement position = parameters.getPosition();
    PsiElement parenthesisOwner = getParenthesisOwner(position);
    final boolean insideCast = parenthesisOwner instanceof PsiTypeCastExpression;

    if (insideCast) {
      PsiElement parent = parenthesisOwner.getParent();
      if (parent instanceof PsiParenthesizedExpression && parent.getParent() instanceof PsiReferenceExpression) {
        for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiParenthesizedExpression)parent, false)) {
          result.consume(PsiTypeLookupItem.createLookupItem(info.getType(), parent));
        }
        return;
      }
    }

    for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      PsiType type = info.getDefaultType();
      if (type instanceof PsiWildcardType) {
        type = ((PsiWildcardType)type).getBound();
      }

      if (type == null || PsiType.VOID.equals(type)) {
        continue;
      }

      if (type instanceof PsiPrimitiveType) {
        final PsiType castedType = getCastedExpressionType(parenthesisOwner);
        if (castedType != null && !(castedType instanceof PsiPrimitiveType)) {
          final PsiClassType boxedType = ((PsiPrimitiveType)type).getBoxedType(position);
          if (boxedType != null) {
            type = boxedType;
          }
        }
      }
      result.consume(createSmartCastElement(parameters, insideCast, type));
    }
  }

  @Nullable
  private static PsiType getCastedExpressionType(PsiElement parenthesisOwner) {
    if (parenthesisOwner instanceof PsiTypeCastExpression) {
      final PsiExpression operand = ((PsiTypeCastExpression)parenthesisOwner).getOperand();
      return operand == null ? null : operand.getType();
    }

    if (parenthesisOwner instanceof PsiParenthesizedExpression) {
      PsiElement next = parenthesisOwner.getNextSibling();
      while (next != null && (next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
        next = next.getNextSibling();
      }
      if (next instanceof PsiExpression) {
        return ((PsiExpression)next).getType();
      }
    }
    return null;
  }

  private static LookupElement createSmartCastElement(final CompletionParameters parameters, final boolean overwrite, final PsiType type) {
    return AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE.applyPolicy(new LookupElementDecorator<PsiTypeLookupItem>(
      PsiTypeLookupItem.createLookupItem(type, parameters.getPosition())) {

      @Override
      public void handleInsert(InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.casting");

        final Editor editor = context.getEditor();
        final Document document = editor.getDocument();
        if (overwrite) {
          document.deleteString(context.getSelectionEndOffset(),
                                context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
        }

        final CommonCodeStyleSettings csSettings = context.getCodeStyleSettings();
        final int oldTail = context.getTailOffset();
        context.setTailOffset(RParenthTailType.addRParenth(editor, oldTail, csSettings.SPACE_WITHIN_CAST_PARENTHESES));

        getDelegate().handleInsert(CompletionUtil.newContext(context, getDelegate(), context.getStartOffset(), oldTail));

        PostprocessReformattingAspect.getInstance(context.getProject()).doPostponedFormatting();
        if (csSettings.SPACE_AFTER_TYPE_CAST) {
          context.setTailOffset(TailType.insertChar(editor, context.getTailOffset(), ' '));
        }

        if (parameters.getCompletionType() == CompletionType.SMART) {
          editor.getCaretModel().moveToOffset(context.getTailOffset());
        }
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    });
  }

  private static PsiElement getParenthesisOwner(PsiElement position) {
    PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
    return lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
  }

  private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
    PsiType type = assertNotNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
    if (type instanceof PsiPrimitiveType) {
      session.registerKeyword(type.getCanonicalText(false));
      return;
    }

    PsiClass aClass =
      type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() == 0 ? ((PsiClassType)type).resolve() : null;
    if (aClass != null) {
      session.registerClass(aClass);
    }
  }

  private static void addExpressionVariants(@NotNull CompletionParameters parameters, PsiElement position, CompletionResultSet result) {
    if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) &&
        !JavaKeywordCompletion.AFTER_DOT.accepts(position) && !shouldSuggestCast(parameters)) {
      Method addExpectedTypeMembers = getMethod(JavaKeywordCompletion.class, void.class, "addExpectedTypeMembers", CompletionParameters.class, CompletionResultSet.class);
      invokeMethod(addExpectedTypeMembers, parameters, result);
      if (SameSignatureCallParametersProviderEx.IN_CALL_ARGUMENT.accepts(position)) {
        new SameSignatureCallParametersProviderEx().addCompletions(parameters, new ProcessingContext(), result);
      }
    }
  }

  private static void addKeywords(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    Method addKeywords = getMethod(JavaKeywordCompletion.class, void.class, "addKeywords", CompletionParameters.class, JavaCompletionSession.class, Consumer.class);
    invokeMethod(addKeywords, parameters, session, (Consumer<LookupElement>) element -> {
      if (element.getLookupString().startsWith(result.getPrefixMatcher().getPrefix())) {
        result.addElement(element);
      }
    });
  }

	public static Method getMethod(Class<?> parentClass, Class<?> returnType, String methodName, Class<?>... types) {
		for (Method method : parentClass.getDeclaredMethods()) {
			if (equalsTo(method, returnType, methodName, types)) return method;
		}

		return null;
	}

	public static boolean equalsTo(Method otherMethod, Class<?> returnType, String methodName, Class<?>... methodTypes) {
		if (methodName != null && !methodName.equals(otherMethod.getName())) return false;

		if (otherMethod.getParameterCount() != methodTypes.length) return false;			// not equal methods

		if (!otherMethod.getReturnType().equals(returnType)) return false;

		for (int i = 0; i < otherMethod.getParameterTypes().length; i++) {
			if (otherMethod.getParameterTypes()[i] != methodTypes[i]) return false;
		}

		return true;
	}

	public static Object invokeMethod(Method method, Object... params) {
		try {
			method.setAccessible(true);
			return method.invoke(null, params);
		} catch (Exception e) {
			if (e.getCause() != null && e.getCause().getClass() == ProcessCanceledException.class) throw (ProcessCanceledException) e.getCause();
		}

		return null;
	}
}
