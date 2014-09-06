package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.CharTailType;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.CompletionVariant;
import com.intellij.codeInsight.completion.ConstructorInsertHandler;
import com.intellij.codeInsight.completion.InheritorsHolder;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.Java15CompletionData;
import com.intellij.codeInsight.completion.Java18CompletionData;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionData;
import com.intellij.codeInsight.completion.JavaCompletionSorting;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaGenerateMemberCompletionContributor;
import com.intellij.codeInsight.completion.JavaInheritorsGetter;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.JavaStaticMemberProcessor;
import com.intellij.codeInsight.completion.LegacyCompletionContributor;
import com.intellij.codeInsight.completion.OffsetMap;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.WordCompletionContributor;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiNameValuePairPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.JavaMembersGetter;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PairConsumer;
import com.intellij.util.ProcessingContext;
import com.siyeh.ig.psiutils.ClassUtils;
import de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodBuilderProcessor;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import static de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodBuilderProcessor.getType;
import static de.plushnikov.intellij.plugin.processor.clazz.ExtensionMethodProcessor.getExtendingMethods;
import static de.plushnikov.intellij.plugin.util.PsiClassUtil.hasParent;

/**
 * @author Suburban Squirrel
 * @version 0.8.6
 * @since 0.8.6
 */
public class LombokCompletionContributor extends JavaCompletionContributor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.LombokCompletionContributor");
  private static final PsiJavaElementPattern.Capture<PsiElement> UNEXPECTED_REFERENCE_AFTER_DOT = psiElement().afterLeaf(".").insideStarting(psiExpressionStatement());
  private static final Map<LanguageLevel, JavaCompletionData> ourCompletionData;
  private static final PsiNameValuePairPattern NAME_VALUE_PAIR = psiNameValuePair().withSuperParent(2, psiElement(PsiAnnotation.class));
  private static final ElementPattern<PsiElement> ANNOTATION_ATTRIBUTE_NAME = or(psiElement(PsiIdentifier.class).withParent(NAME_VALUE_PAIR),
        psiElement().afterLeaf("(").withParent(psiReferenceExpression().withParent(NAME_VALUE_PAIR)));
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
  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL = psiElement().afterLeaf(psiElement().withElementType(elementType().oneOf(JavaTokenType.DOUBLE_LITERAL, JavaTokenType.LONG_LITERAL,
        JavaTokenType.FLOAT_LITERAL, JavaTokenType.INTEGER_LITERAL)));
  private static final ElementPattern<PsiElement> IMPORT_REFERENCE = psiElement().withParent(psiElement(PsiJavaCodeReferenceElement.class).withParent(PsiImportStatementBase.class));
  static final PsiElementPattern.Capture<PsiElement> IN_TYPE_ARGS = PlatformPatterns.psiElement().inside(PlatformPatterns.psiElement(PsiReferenceParameterList.class));

  static {
    ourCompletionData = new LinkedHashMap<LanguageLevel, JavaCompletionData>();
    ourCompletionData.put(LanguageLevel.JDK_1_8, new Java18CompletionData());
    ourCompletionData.put(LanguageLevel.JDK_1_5, new Java15CompletionData());
    ourCompletionData.put(LanguageLevel.JDK_1_3, new JavaCompletionData());
  }

  /**
   * clone from com.intellij.codeInsight.completion.TypeArgumentCompletionProvider
   */
  final private static class TypeArgumentCompletionProvider extends CompletionProvider<CompletionParameters> {
    private final boolean mySmart;
    @Nullable
    private final InheritorsHolder myInheritors;

    TypeArgumentCompletionProvider(boolean smart, @Nullable InheritorsHolder inheritors) {
      mySmart = smart;
      myInheritors = inheritors;
    }

    @Override
    protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext processingContext, @NotNull final CompletionResultSet resultSet) {
      final PsiElement context = parameters.getPosition();

      final Pair<PsiClass, Integer> pair = getTypeParameterInfo(context);
      if (pair == null) {
        return;
      }

      PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
      if (expression != null) {
        ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, true, false, false);
        if (types.length > 0) {
          for (ExpectedTypeInfo info : types) {
            PsiType type = info.getType();
            if (type instanceof PsiClassType && !type.equals(expression.getType())) {
              fillExpectedTypeArgs(resultSet, context, pair.first, pair.second, ((PsiClassType) type).resolveGenerics(), mySmart ? info.getTailType() : TailType.NONE);
            }
          }
          return;
        }
      }

      if (mySmart) {
        addInheritors(parameters, resultSet, pair.first, pair.second);
      }
    }

    private void fillExpectedTypeArgs(CompletionResultSet resultSet,
                                      PsiElement context,
                                      final PsiClass actualClass,
                                      final int index,
                                      PsiClassType.ClassResolveResult expectedType,
                                      TailType globalTail) {
      final PsiClass expectedClass = expectedType.getElement();

      if (!InheritanceUtil.isInheritorOrSelf(actualClass, expectedClass, true)) {
        return;
      }
      assert expectedClass != null;

      final PsiSubstitutor currentSubstitutor = TypeConversionUtil.getClassSubstitutor(expectedClass, actualClass, PsiSubstitutor.EMPTY);
      assert currentSubstitutor != null;

      PsiTypeParameter[] params = actualClass.getTypeParameters();
      final List<PsiTypeLookupItem> typeItems = new ArrayList<PsiTypeLookupItem>();
      for (int i = index; i < params.length; i++) {
        PsiType arg = getExpectedTypeArg(context, i, expectedType, currentSubstitutor, params);
        if (arg == null) {
          arg = getExpectedTypeArg(context, index, expectedType, currentSubstitutor, params);
          if (arg != null) {
            resultSet.addElement(TailTypeDecorator.withTail(PsiTypeLookupItem.createLookupItem(arg, context), getTail(index == params.length - 1)));
          }
          return;
        }
        typeItems.add(PsiTypeLookupItem.createLookupItem(arg, context));
      }

      boolean hasParameters = hasConstructorParameters(actualClass, context);
      TypeArgsLookupElement element = new TypeArgsLookupElement(typeItems, globalTail, hasParameters);
      element.registerSingleClass(myInheritors);
      resultSet.addElement(element);
    }

    static boolean hasConstructorParameters(PsiClass psiClass, @NotNull PsiElement place) {
      final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(place.getProject()).getResolveHelper();
      boolean hasParams = false;
      for (PsiMethod constructor : psiClass.getConstructors()) {
        if (!resolveHelper.isAccessible(constructor, place, null)) continue;
        if (constructor.getParameterList().getParametersCount() > 0) {
          hasParams = true;
          break;
        }
      }
      return hasParams;
    }

    @Nullable
    private static PsiType getExpectedTypeArg(PsiElement context,
                                              int index,
                                              PsiClassType.ClassResolveResult expectedType,
                                              PsiSubstitutor currentSubstitutor, PsiTypeParameter[] params) {
      PsiClass expectedClass = expectedType.getElement();
      assert expectedClass != null;
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(expectedClass)) {
        final PsiType argSubstitution = expectedType.getSubstitutor().substitute(parameter);
        final PsiType paramSubstitution = currentSubstitutor.substitute(parameter);
        final PsiType substitution = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper()
            .getSubstitutionForTypeParameter(params[index], paramSubstitution, argSubstitution, false, PsiUtil.getLanguageLevel(context));
        if (substitution != null && substitution != PsiType.NULL) {
          return substitution;
        }
      }
      return null;
    }

    private static void addInheritors(CompletionParameters parameters,
                                      final CompletionResultSet resultSet,
                                      final PsiClass referencedClass,
                                      final int parameterIndex) {
      final List<PsiClassType> typeList = Collections.singletonList((PsiClassType) TypeConversionUtil.typeParameterErasure(
          referencedClass.getTypeParameters()[parameterIndex]));
      JavaInheritorsGetter.processInheritors(parameters, typeList, resultSet.getPrefixMatcher(), new Consumer<PsiType>() {
        @Override
        public void consume(final PsiType type) {
          final PsiClass psiClass = PsiUtil.resolveClassInType(type);
          if (psiClass == null) {
            return;
          }

          resultSet.addElement(TailTypeDecorator.withTail(new JavaPsiClassReferenceElement(psiClass),
              getTail(parameterIndex == referencedClass.getTypeParameters().length - 1)));
        }
      });
    }

    private static TailType getTail(boolean last) {
      return last ? new CharTailType('>') : TailType.COMMA;
    }

    @Nullable
    static Pair<PsiClass, Integer> getTypeParameterInfo(PsiElement context) {
      final PsiReferenceParameterList parameterList = PsiTreeUtil.getContextOfType(context, PsiReferenceParameterList.class, true);
      if (parameterList == null) {
        return null;
      }

      PsiElement parent = parameterList.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        return null;
      }

      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) parent;
      final int parameterIndex;

      int index = 0;
      final PsiTypeElement typeElement = PsiTreeUtil.getContextOfType(context, PsiTypeElement.class, true);
      if (typeElement != null) {
        final PsiTypeElement[] elements = referenceElement.getParameterList().getTypeParameterElements();
        while (index < elements.length) {
          final PsiTypeElement element = elements[index++];
          if (element == typeElement) {
            break;
          }
        }
      }
      parameterIndex = index - 1;

      if (parameterIndex < 0) {
        return null;
      }
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return null;
      }

      final PsiClass referencedClass = (PsiClass) target;
      final PsiTypeParameter[] typeParameters = referencedClass.getTypeParameters();
      if (typeParameters.length <= parameterIndex) {
        return null;
      }

      return Pair.create(referencedClass, parameterIndex);
    }

    public static class TypeArgsLookupElement extends LookupElement {
      private String myLookupString;
      private final List<PsiTypeLookupItem> myTypeItems;
      private final TailType myGlobalTail;
      private final boolean myHasParameters;

      public TypeArgsLookupElement(List<PsiTypeLookupItem> typeItems, TailType globalTail, boolean hasParameters) {
        myTypeItems = typeItems;
        myGlobalTail = globalTail;
        myHasParameters = hasParameters;
        myLookupString = StringUtil.join(myTypeItems, new Function<PsiTypeLookupItem, String>() {
          @Override
          public String fun(PsiTypeLookupItem item) {
            return item.getLookupString();
          }
        }, ", ");
      }

      @NotNull
      @Override
      public Object getObject() {
        return myTypeItems.get(0).getObject();
      }

      public void registerSingleClass(@Nullable InheritorsHolder inheritors) {
        if (inheritors != null && myTypeItems.size() == 1) {
          PsiType type = myTypeItems.get(0).getPsiType();
          PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
          if (aClass != null && !aClass.hasTypeParameters()) {
            JavaCompletionUtil.setShowFQN(myTypeItems.get(0));
            inheritors.registerClass(aClass);
          }
        }
      }

      @NotNull
      @Override
      public String getLookupString() {
        return myLookupString;
      }

      @Override
      public void renderElement(LookupElementPresentation presentation) {
        myTypeItems.get(0).renderElement(presentation);
        presentation.setItemText(getLookupString());
        if (myTypeItems.size() > 1) {
          presentation.setTailText(null);
          presentation.setTypeText(null);
        }
      }

      @Override
      public void handleInsert(InsertionContext context) {
        context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        for (int i = 0; i < myTypeItems.size(); i++) {
          emulateInsertion(context, context.getTailOffset(), myTypeItems.get(i));
          context.setTailOffset(getTail(i == myTypeItems.size() - 1).processTail(context.getEditor(), context.getTailOffset()));
        }
        context.setAddCompletionChar(false);

        context.commitDocument();

        PsiElement leaf = context.getFile().findElementAt(context.getTailOffset() - 1);
        if (psiElement().withParents(PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class, PsiNewExpression.class)
            .accepts(leaf)) {
          ParenthesesInsertHandler.getInstance(myHasParameters).handleInsert(context, this);
          myGlobalTail.processTail(context.getEditor(), context.getTailOffset());
        }
      }


      private static InsertionContext newContext(InsertionContext oldContext, LookupElement forElement) {
        final Editor editor = oldContext.getEditor();
        return new InsertionContext(new OffsetMap(editor.getDocument()), Lookup.AUTO_INSERT_SELECT_CHAR, new LookupElement[]{forElement}, oldContext.getFile(), editor,
            oldContext.shouldAddCompletionChar());
      }

      static InsertionContext emulateInsertion(InsertionContext oldContext, int newStart, final LookupElement item) {
        final InsertionContext newContext = newContext(oldContext, item);
        CompletionUtil.emulateInsertion(item, newStart, newContext);
        return newContext;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (o == null || getClass() != o.getClass()) {
          return false;
        }

        TypeArgsLookupElement element = (TypeArgsLookupElement) o;

        if (!myTypeItems.equals(element.myTypeItems)) {
          return false;
        }

        return true;
      }

      @Override
      public int hashCode() {
        return myTypeItems.hashCode();
      }
    }
  }

  final public static class LombokElementFilter implements ElementFilter {
    public static final ElementFilter INSTANCE = new LombokElementFilter();

    private LombokElementFilter() {}

    @Override
    public boolean isAcceptable(Object element, @Nullable PsiElement context) {
//      if (!filterVal((PsiElement)element, context)) return false;
      if (element instanceof PsiMethod) return filterExtensionMethods((PsiMethod) element, context);
      if (element instanceof PsiField) return filterFieldDefault((PsiField) element, context);
      return true;
    }

    /**
     * also manual handle access modifiers
     */
    private boolean filterFieldDefault(@NotNull PsiField field, @Nullable PsiElement context) {
      if(context == null) return true;
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);

      if (field.hasModifierProperty(PsiModifier.PUBLIC)) return true;
      PsiClass fieldClass = field.getContainingClass();
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        if (fieldClass != null && fieldClass.equals(contextClass)) {
          return true;
        } else {
          return false;
        }
      }
      if (field.hasModifierProperty(PsiModifier.PROTECTED)) {
        if (contextClass == null || fieldClass == null) return false;
        if (hasParent(contextClass, fieldClass) || ((PsiJavaFile) field.getContainingFile()).getPackageName().equals(((PsiJavaFile) context.getContainingFile()).getPackageName())) {
          return true;
        } else {
          return false;
        }
      }

      return !LombokHighlightErrorFilter.isInaccessible(field, contextClass, context.getParent());
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
          if (paramType.isAssignableFrom(type) && ExtensionMethodBuilderProcessor.isInExtensionScope(containingClass)) {
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
    public static PsiElement getCallElement(@NotNull PsiExpression expression, @NotNull PsiClass currentClass) {
      List<PsiJavaToken> tokens = new ArrayList<PsiJavaToken>(PsiTreeUtil.findChildrenOfType(expression, PsiJavaToken.class));
      PsiJavaToken dot = null;

      for (int i = tokens.size() - 1; i >= 0; i--) {
        if (tokens.get(i).getTokenType() == JavaTokenType.DOT) {
          dot = tokens.get(i);
          break;
        }
      }
      return dot != null ? dot.getPrevSibling() : null;
    }

    @Nullable
    public static PsiType getCallType(@NotNull PsiExpression expression, @NotNull PsiClass currentClass) {
      PsiElement callSibling = getCallElement(expression, currentClass);
      if (callSibling instanceof PsiExpression) {
        return ((PsiExpression) callSibling).getType();
      }
      return PsiTypesUtil.getClassType(currentClass);
    }
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {  }

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

    final InheritorsHolder inheritors = new InheritorsHolder(position, result);
    if (IN_TYPE_ARGS.accepts(position)) {
      new TypeArgumentCompletionProvider(false, inheritors).addCompletions(parameters, new ProcessingContext(), result);
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

  private static Object callMethod(Object thisObj, @NotNull Class aClass, String name, Class[] classes, Object[] objects) {
    try {
      Method method = aClass.getDeclaredMethod(name, classes);
      method.setAccessible(true);
      return method.invoke(thisObj, objects);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private static void completeAnnotationAttributeName(CompletionResultSet result, PsiElement insertedElement,
                                                      CompletionParameters parameters) {
    PsiNameValuePair pair = PsiTreeUtil.getParentOfType(insertedElement, PsiNameValuePair.class);
    PsiAnnotationParameterList parameterList = (PsiAnnotationParameterList)pair.getParent();
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
      addAllClasses(parameters, result, new InheritorsHolder(insertedElement, result));
    }

    if (annoClass != null) {
      final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

      methods: for (PsiMethod method : annoClass.getMethods()) {
        final String attrName = method.getName();
        for (PsiNameValuePair apair : existingPairs) {
          if (Comparing.equal(apair.getName(), attrName)) continue methods;
        }
        result.addElement(new LookupItem<PsiMethod>(method, attrName).setInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            final Editor editor = context.getEditor();
            TailType.EQ.processTail(editor, editor.getCaretModel().getOffset());
            context.setAddCompletionChar(false);
          }
        }));
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

  private static JavaCompletionData getCompletionData(LanguageLevel level) {
    final Set<Map.Entry<LanguageLevel, JavaCompletionData>> entries = ourCompletionData.entrySet();
    for (Map.Entry<LanguageLevel, JavaCompletionData> entry : entries) {
      if (entry.getKey().isAtLeast(level)) return entry.getValue();
    }
    return ourCompletionData.get(LanguageLevel.JDK_1_3);
  }

  private static void addKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
    final JavaCompletionData completionData = getCompletionData(PsiUtil.getLanguageLevel(position));
    completionData.addKeywordVariants(keywordVariants, position, parameters.getOriginalFile());
    completionData.completeKeywordsBySet(lookupSet, keywordVariants, position, result.getPrefixMatcher(), parameters.getOriginalFile());
    completionData.fillCompletions(parameters, result);

    for (final LookupElement item : lookupSet) {
      result.addElement(item);
    }
  }

  private static boolean hasFieldDefaults(PsiReferenceExpression reference) {
    if (reference == null) return false;
    PsiElement callElement = reference.resolve();

    PsiClass annotatedClass = null;
    if (callElement instanceof PsiLocalVariable) annotatedClass = PsiTypesUtil.getPsiClass(((PsiLocalVariable) callElement).getType());
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
            if (qualifier instanceof PsiReferenceExpression) checkAccess = !hasFieldDefaults((PsiReferenceExpression) qualifier);   // if found annotation try see all elements
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
            try {
              Class<?> aClass = Class.forName("JavaClassNameInsertHandler");
              Object o = aClass.newInstance();
              List<JavaPsiClassReferenceElement> list = (List<JavaPsiClassReferenceElement>) callMethod((Object)null, aClass, "createClassLookupItems",
                  new Class[]{PsiClass.class, boolean.class, InsertHandler.class, Condition.class}, new Object[]{completion, isAfterNew,
                      o, new Condition<PsiClass>() {
                    @Override
                    public boolean value(PsiClass psiClass) {
                      return !inheritors.alreadyProcessed(psiClass) && JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
                    }
                  }
              });

              for (JavaPsiClassReferenceElement item : list) {
                usedWords.add(item.getLookupString());
                result.addElement(item);
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          } else {
            LookupElement element = LookupItemUtil.objectToLookupItem(completion);
            usedWords.add(element.getLookupString());
            result.addElement(element);
          }
        }
      }
    });
    return usedWords;
  }

  static boolean isAfterPrimitiveOrArrayType(PsiElement element) {
    return psiElement().withParent(
      psiReferenceExpression().withFirstChild(
        psiElement(PsiClassObjectAccessExpression.class).withLastChild(
          not(psiElement().withText(PsiKeyword.CLASS))))).accepts(element);
  }

  static void addExpectedTypeMembers(CompletionParameters parameters, final CompletionResultSet result) {
    for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
      new JavaMembersGetter(info.getDefaultType(), parameters).addMembers(parameters.getInvocationCount() > 1, result);
    }
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
      return LombokElementFilter.INSTANCE;
    }
    return filter;
  }
}
