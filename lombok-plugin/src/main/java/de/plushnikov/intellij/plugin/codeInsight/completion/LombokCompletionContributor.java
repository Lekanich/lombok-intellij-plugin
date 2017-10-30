package de.plushnikov.intellij.plugin.codeInsight.completion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeInfoImpl;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.BasicExpressionCompletionContributor;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.ConstructorInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.codeInsight.completion.JavaClassNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionSession;
import com.intellij.codeInsight.completion.JavaCompletionSorting;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.completion.JavaGenerateMemberCompletionContributor;
import com.intellij.codeInsight.completion.JavaInheritorsGetter;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.JavaStaticMemberProcessor;
import com.intellij.codeInsight.completion.LegacyCompletionContributor;
import com.intellij.codeInsight.completion.OffsetKey;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.completion.WordCompletionContributor;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.completion.simple.RParenthTailType;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiQualifiedExpression;
import com.intellij.psi.PsiQualifiedNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementExtractorFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.tree.java.MethodReferenceResolver;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Consumer;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static com.intellij.codeInsight.completion.BasicExpressionCompletionContributor.createKeywordLookupItem;
import static com.intellij.patterns.PsiJavaPatterns.elementType;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpressionStatement;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.patterns.PsiJavaPatterns.psiNameValuePair;
import static com.intellij.patterns.PsiJavaPatterns.psiReferenceExpression;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_SET;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_STREAM;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED;
import static com.intellij.psi.codeStyle.JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT;
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
                if (expression == null) {
                    return false;
                }
                PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
                return aClass != null && aClass.isEnum();
            }
        })
    ));
    private static final ElementPattern<PsiElement> INSIDE_CONSTRUCTOR = psiElement().inside(psiMethod().constructor(true));
    private static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT =
        PlatformPatterns.psiElement().beforeLeaf(PlatformPatterns.psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(
            PlatformPatterns.psiElement(PsiReferenceExpression.class).withParent(
                PlatformPatterns.psiElement(PsiExpressionList.class).withParent(PsiCall.class)));

    private static class JavaModuleCompletion {
        static boolean isModuleFile(@NotNull PsiFile file) {
            return PsiJavaModule.MODULE_INFO_FILE.equals(file.getName()) && PsiUtil.isLanguageLevel9OrHigher(file);
        }

        static void addVariants(@NotNull PsiElement position, @NotNull CompletionResultSet resultSet) {
            Consumer<LookupElement> result = element -> {
                if (element.getLookupString().startsWith(resultSet.getPrefixMatcher().getPrefix())) {
                    resultSet.addElement(element);
                }
            };

            if (position instanceof PsiIdentifier) {
                PsiElement context = position.getParent();
                if (context instanceof PsiErrorElement) {
                    context = context.getParent();
                }

                if (context instanceof PsiJavaFile) {
                    addFileHeaderKeywords(position, result);
                } else if (context instanceof PsiJavaModule) {
                    addModuleStatementKeywords(position, result);
                } else if (context instanceof PsiProvidesStatement) {
                    addProvidesStatementKeywords(position, result);
                } else if (context instanceof PsiJavaModuleReferenceElement) {
                    addRequiresStatementKeywords(context, position, result);
                    addModuleReferences(context, result);
                } else if (context instanceof PsiJavaCodeReferenceElement) {
                    addClassOrPackageReferences(context, result, resultSet);
                }
            }
        }

        private static void addFileHeaderKeywords(PsiElement position, Consumer<LookupElement> result) {
            PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
            if (prev == null) {
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPEN), TailType.HUMBLE_SPACE_BEFORE_WORD));
            } else if (PsiUtil.isJavaToken(prev, JavaTokenType.OPEN_KEYWORD)) {
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
            }
        }

        private static void addModuleStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.OPENS), TailType.HUMBLE_SPACE_BEFORE_WORD));
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }

        private static void addProvidesStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.WITH), TailType.HUMBLE_SPACE_BEFORE_WORD));
        }

        private static void addRequiresStatementKeywords(PsiElement context, PsiElement position, Consumer<LookupElement> result) {
            if (context.getParent() instanceof PsiRequiresStatement) {
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.TRANSITIVE), TailType.HUMBLE_SPACE_BEFORE_WORD));
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(createKeywordLookupItem(position, PsiKeyword.STATIC), TailType.HUMBLE_SPACE_BEFORE_WORD));
            }
        }

        private static void addModuleReferences(PsiElement context, Consumer<LookupElement> result) {
            PsiElement statement = context.getParent();
            if (!(statement instanceof PsiJavaModule)) {
                PsiElement host = statement.getParent();
                if (host instanceof PsiJavaModule) {
                    String hostName = ((PsiJavaModule) host).getName();
                    Project project = context.getProject();
                    JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
                    GlobalSearchScope scope = ProjectScope.getAllScope(project);
                    for (String name : index.getAllKeys(project)) {
                        if (!name.equals(hostName) && index.get(name, project, scope).size() == 1) {
                            result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(LookupElementBuilder.create(name), TailType.SEMICOLON));
                        }
                    }
                }
            }
        }

        private static void addClassOrPackageReferences(PsiElement context, Consumer<LookupElement> result, CompletionResultSet resultSet) {
            PsiElement refOwner = context.getParent();
            if (refOwner instanceof PsiPackageAccessibilityStatement) {
                Module module = ModuleUtilCore.findModuleForPsiElement(context);
                PsiPackage topPackage = JavaPsiFacade.getInstance(context.getProject()).findPackage("");
                if (module != null && topPackage != null) {
                    processPackage(topPackage, module.getModuleScope(false), result);
                }
            } else if (refOwner instanceof PsiUsesStatement) {
                processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.SEMICOLON);
            } else if (refOwner instanceof PsiProvidesStatement) {
                processClasses(context.getProject(), null, resultSet, SERVICE_FILTER, TailType.HUMBLE_SPACE_BEFORE_WORD);
            } else if (refOwner instanceof PsiReferenceList) {
                PsiElement statement = refOwner.getParent();
                if (statement instanceof PsiProvidesStatement) {
                    PsiJavaCodeReferenceElement intRef = ((PsiProvidesStatement) statement).getInterfaceReference();
                    if (intRef != null) {
                        PsiElement service = intRef.resolve();
                        Module module = ModuleUtilCore.findModuleForPsiElement(context);
                        if (service instanceof PsiClass && module != null) {
                            Predicate<PsiClass> filter = psiClass -> !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                                InheritanceUtil.isInheritorOrSelf(psiClass, (PsiClass) service, true);
                            processClasses(context.getProject(), module.getModuleScope(false), resultSet, filter, TailType.SEMICOLON);
                        }
                    }
                }
            }
        }

        private static void processPackage(PsiPackage pkg, GlobalSearchScope scope, Consumer<LookupElement> result) {
            String packageName = pkg.getQualifiedName();
            if (isQualified(packageName) && !PsiUtil.isPackageEmpty(pkg.getDirectories(scope), packageName)) {
                result.consume(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(lookupElement(pkg), TailType.SEMICOLON));
            }
            for (PsiPackage subPackage : pkg.getSubPackages(scope)) {
                processPackage(subPackage, scope, result);
            }
        }

        private static final Predicate<PsiClass> SERVICE_FILTER =
            psiClass -> !psiClass.isEnum() && psiClass.hasModifierProperty(PsiModifier.PUBLIC);

        private static void processClasses(Project project,
                                           GlobalSearchScope scope,
                                           CompletionResultSet resultSet,
                                           Predicate<PsiClass> filter,
                                           TailType tail) {
            GlobalSearchScope _scope = scope != null ? scope : ProjectScope.getAllScope(project);
            AllClassesGetter.processJavaClasses(resultSet.getPrefixMatcher(), project, _scope, psiClass -> {
                if (isQualified(psiClass.getQualifiedName()) && filter.test(psiClass)) {
                    resultSet.addElement(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(lookupElement(psiClass), tail));
                }
                return true;
            });
        }

        private static LookupElementBuilder lookupElement(PsiNamedElement e) {
            LookupElementBuilder lookup = LookupElementBuilder.create(e).withInsertHandler(FQN_INSERT_HANDLER);
            String fqn = e instanceof PsiClass ? ((PsiClass) e).getQualifiedName() : ((PsiQualifiedNamedElement) e).getQualifiedName();
            return fqn != null ? lookup.withPresentableText(fqn) : lookup;
        }

        private static boolean isQualified(String name) {
            return name != null && name.indexOf('.') > 0;
        }

        private static final InsertHandler<LookupElement> FQN_INSERT_HANDLER = new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
                Object e = item.getObject();
                String fqn = e instanceof PsiClass ? ((PsiClass) e).getQualifiedName() : ((PsiQualifiedNamedElement) e).getQualifiedName();
                if (fqn != null) {
                    int start = JavaCompletionUtil.findQualifiedNameStart(context);
                    context.getDocument().replaceString(start, context.getTailOffset(), fqn);
                }
            }
        };
    }

    public static class FunctionalExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {

        private static final InsertHandler<LookupElement> CONSTRUCTOR_REF_INSERT_HANDLER = (context, item) -> {
            int start = context.getStartOffset();
            PsiClass psiClass = PsiUtil.resolveClassInType((PsiType) item.getObject());
            if (psiClass != null) {
                String insertedName = StringUtil.trimEnd(item.getLookupString(), "::new");
                while (insertedName.endsWith("[]")) {
                    insertedName = insertedName.substring(0, insertedName.length() - 2);
                }
                JavaCompletionUtil.insertClassReference(psiClass, context.getFile(), start, start + insertedName.length());
            }
        };

        private static boolean isLambdaContext(@NotNull PsiElement element) {
            final PsiElement rulezzRef = element.getParent();
            return rulezzRef != null &&
                rulezzRef instanceof PsiReferenceExpression &&
                ((PsiReferenceExpression) rulezzRef).getQualifier() == null &&
                LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
        }

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            addFunctionalVariants(parameters, true, true, result.getPrefixMatcher(), result);
        }

        static void addFunctionalVariants(@NotNull CompletionParameters parameters, boolean smart, boolean addInheritors, PrefixMatcher matcher, Consumer<LookupElement> result) {
            if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile()) || !isLambdaContext(parameters.getPosition())) {
                return;
            }

            ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
            for (ExpectedTypeInfo expectedType : expectedTypes) {
                final PsiType defaultType = expectedType.getDefaultType();
                if (LambdaUtil.isFunctionalType(defaultType)) {
                    final PsiType functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
                    final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
                    if (functionalInterfaceMethod != null) {
                        PsiParameter[] params = PsiParameter.EMPTY_ARRAY;
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
                                params.length == 1 ? getParamName(params[0], originalPosition)
                                    : "(" + StringUtil.join(params, parameter -> getParamName(parameter, originalPosition), ",") + ")";

                            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
                            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) JavaPsiFacade.getElementFactory(project)
                                .createExpressionFromText(paramsString + " -> {}", null);
                            lambdaExpression = (PsiLambdaExpression) codeStyleManager.reformat(lambdaExpression);
                            paramsString = lambdaExpression.getParameterList().getText();
                            final LookupElementBuilder builder =
                                LookupElementBuilder.create(functionalInterfaceMethod, paramsString + " -> ")
                                    .withPresentableText(paramsString + " -> {}")
                                    .withTypeText(functionalInterfaceType.getPresentableText())
                                    .withIcon(AllIcons.Nodes.Function);
                            LookupElement lambdaElement = builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
                            result.consume(smart ? lambdaElement : PrioritizedLookupElement.withPriority(lambdaElement, 1));
                        }

                        addMethodReferenceVariants(
                            smart, addInheritors, parameters, matcher, functionalInterfaceType, functionalInterfaceMethod, params, originalPosition, substitutor,
                            element -> result.consume(smart ? JavaSmartCompletionContributor.decorate(element, Arrays.asList(expectedTypes)) : element));
                    }
                }
            }
        }

        private static void addMethodReferenceVariants(boolean smart,
                                                       boolean addInheritors,
                                                       CompletionParameters parameters,
                                                       PrefixMatcher matcher,
                                                       PsiType functionalInterfaceType,
                                                       PsiMethod functionalInterfaceMethod,
                                                       PsiParameter[] params,
                                                       PsiElement originalPosition,
                                                       PsiSubstitutor substitutor,
                                                       Consumer<LookupElement> result) {
            final PsiType expectedReturnType = substitutor.substitute(functionalInterfaceMethod.getReturnType());
            if (expectedReturnType == null) {
                return;
            }

            if (params.length > 0) {
                for (LookupElement element : collectVariantsByReceiver(!smart, functionalInterfaceType, params, originalPosition, substitutor, expectedReturnType)) {
                    result.consume(element);
                }
            }
            for (LookupElement element : collectThisVariants(functionalInterfaceType, params, originalPosition, substitutor, expectedReturnType)) {
                result.consume(element);
            }

            for (LookupElement element : collectStaticVariants(functionalInterfaceType, params, originalPosition, substitutor, expectedReturnType)) {
                result.consume(element);
            }

            Consumer<PsiType> consumer = eachReturnType -> {
                PsiClass psiClass = PsiUtil.resolveClassInType(eachReturnType);
                if (psiClass == null || !MethodReferenceResolver.canBeConstructed(psiClass)) {
                    return;
                }

                if (eachReturnType.getArrayDimensions() == 0) {
                    PsiMethod[] constructors = psiClass.getConstructors();
                    for (PsiMethod psiMethod : constructors) {
                        if (areParameterTypesAppropriate(psiMethod, params, substitutor, 0)) {
                            result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
                        }
                    }
                    if (constructors.length == 0 && params.length == 0) {
                        result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
                    }
                } else if (params.length == 1 && PsiType.INT.equals(params[0].getType())) {
                    result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
                }
            };
            if (addInheritors && expectedReturnType instanceof PsiClassType) {
                JavaInheritorsGetter.processInheritors(parameters, Collections.singletonList((PsiClassType) expectedReturnType), matcher, consumer);
            } else {
                consumer.consume(expectedReturnType);
            }
        }

        private static LookupElement createConstructorReferenceLookup(@NotNull PsiType functionalInterfaceType,
                                                                      @NotNull PsiType constructedType) {
            constructedType = TypeConversionUtil.erasure(constructedType);
            return LookupElementBuilder
                .create(constructedType, constructedType.getPresentableText() + "::new")
                .withTypeText(functionalInterfaceType.getPresentableText())
                .withIcon(AllIcons.Nodes.MethodReference)
                .withInsertHandler(CONSTRUCTOR_REF_INSERT_HANDLER)
                .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }

        @NotNull
        private static LookupElement createMethodRefOnThis(PsiType functionalInterfaceType, PsiMethod psiMethod, @Nullable PsiClass outerClass) {
            String fullString = (outerClass == null ? "" : outerClass.getName() + ".") + "this::" + psiMethod.getName();
            return LookupElementBuilder
                .create(psiMethod, fullString)
                .withLookupString(psiMethod.getName())
                .withPresentableText(fullString)
                .withTypeText(functionalInterfaceType.getPresentableText())
                .withIcon(AllIcons.Nodes.MethodReference)
                .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }

        @NotNull
        private static LookupElement createMethodRefOnClass(PsiType functionalInterfaceType, PsiMethod psiMethod, PsiClass qualifierClass) {
            String presentableText = qualifierClass.getName() + "::" + psiMethod.getName();
            return LookupElementBuilder
                .create(psiMethod)
                .withLookupString(presentableText)
                .withPresentableText(presentableText)
                .withInsertHandler((context, item) -> {
                    context.getDocument().insertString(context.getStartOffset(), "::");
                    JavaCompletionUtil.insertClassReference(qualifierClass, context.getFile(), context.getStartOffset());
                })
                .withTypeText(functionalInterfaceType.getPresentableText())
                .withIcon(AllIcons.Nodes.MethodReference)
                .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }

        private static List<LookupElement> collectThisVariants(PsiType functionalInterfaceType,
                                                               PsiParameter[] params,
                                                               PsiElement originalPosition,
                                                               PsiSubstitutor substitutor, PsiType expectedReturnType) {
            List<LookupElement> result = new ArrayList<>();

            Iterable<PsiClass> instanceClasses = JBIterable
                .generate(originalPosition, PsiElement::getParent)
                .filter(PsiMember.class)
                .takeWhile(m -> !m.hasModifierProperty(PsiModifier.STATIC))
                .filter(PsiClass.class);

            boolean first = true;
            for (PsiClass psiClass : instanceClasses) {
                if (!first && psiClass.getName() == null) {
                    continue;
                }

                for (PsiMethod psiMethod : psiClass.getMethods()) {
                    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
                        hasAppropriateReturnType(expectedReturnType, psiMethod) &&
                        areParameterTypesAppropriate(psiMethod, params, substitutor, 0)) {
                        result.add(createMethodRefOnThis(functionalInterfaceType, psiMethod, first ? null : psiClass));
                    }
                }
                first = false;
            }
            return result;
        }

        private static List<LookupElement> collectStaticVariants(PsiType functionalInterfaceType,
                                                                 PsiParameter[] params,
                                                                 PsiElement originalPosition,
                                                                 PsiSubstitutor substitutor, PsiType expectedReturnType) {
            List<LookupElement> result = new ArrayList<>();
            for (PsiClass psiClass : JBIterable.generate(PsiTreeUtil.getParentOfType(originalPosition, PsiClass.class), PsiClass::getContainingClass)) {
                for (PsiMethod psiMethod : psiClass.getMethods()) {
                    if (psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
                        hasAppropriateReturnType(expectedReturnType, psiMethod) &&
                        areParameterTypesAppropriate(psiMethod, params, substitutor, 0)) {
                        result.add(createMethodRefOnClass(functionalInterfaceType, psiMethod, psiClass));
                    }
                }
            }
            return result;
        }

        private static List<LookupElement> collectVariantsByReceiver(boolean prioritize,
                                                                     PsiType functionalInterfaceType,
                                                                     PsiParameter[] params,
                                                                     PsiElement originalPosition,
                                                                     PsiSubstitutor substitutor,
                                                                     PsiType expectedReturnType) {
            List<LookupElement> result = new ArrayList<>();
            final PsiType functionalInterfaceParamType = substitutor.substitute(params[0].getType());
            final PsiClass paramClass = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceParamType);
            if (paramClass != null && !paramClass.hasTypeParameters()) {
                final Set<String> visited = new HashSet<>();
                for (PsiMethod psiMethod : paramClass.getAllMethods()) {
                    PsiClass containingClass = psiMethod.getContainingClass();
                    PsiClass qualifierClass = containingClass != null ? containingClass : paramClass;
                    if (visited.add(psiMethod.getName()) &&
                        !psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
                        hasAppropriateReturnType(expectedReturnType, psiMethod) &&
                        areParameterTypesAppropriate(psiMethod, params, substitutor, 1) &&
                        JavaResolveUtil.isAccessible(psiMethod, null, psiMethod.getModifierList(), originalPosition, null, null)) {
                        LookupElement methodRefLookupElement = createMethodRefOnClass(functionalInterfaceType, psiMethod, qualifierClass);
                        if (prioritize && containingClass == paramClass) {
                            methodRefLookupElement = PrioritizedLookupElement.withExplicitProximity(methodRefLookupElement, 1);
                        }
                        result.add(methodRefLookupElement);
                    }
                }
            }
            return result;
        }

        private static boolean hasAppropriateReturnType(PsiType expectedReturnType, PsiMethod psiMethod) {
            PsiType returnType = psiMethod.getReturnType();
            return returnType != null && TypeConversionUtil.isAssignable(expectedReturnType, returnType);
        }

        private static boolean areParameterTypesAppropriate(PsiMethod psiMethod, PsiParameter[] params, PsiSubstitutor substitutor, int offset) {
            final PsiParameterList parameterList = psiMethod.getParameterList();
            if (parameterList.getParametersCount() == params.length - offset) {
                final PsiParameter[] referenceMethodParams = parameterList.getParameters();
                for (int i = 0; i < params.length - offset; i++) {
                    if (!Comparing.equal(referenceMethodParams[i].getType(), substitutor.substitute(params[i + offset].getType()))) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        private static String getParamName(PsiParameter param, PsiElement originalPosition) {
            return JavaCodeStyleManager.getInstance(originalPosition.getProject()).suggestUniqueVariableName(
                ObjectUtils.assertNotNull(param.getName()), originalPosition, false);
        }
    }

    public static class MethodReturnTypeProvider extends CompletionProvider<CompletionParameters> {
        protected static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE =
            psiElement().withParents(PsiJavaCodeReferenceElement.class, PsiTypeElement.class, PsiMethod.class)
                .andNot(com.intellij.codeInsight.completion.JavaKeywordCompletion.AFTER_DOT);

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      ProcessingContext context,
                                      @NotNull final CompletionResultSet result) {
            addProbableReturnTypes(parameters, result);

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
                ExpectedTypesProvider.processAllSuperTypes(type, eachProcessor, position.getProject(), new HashSet<>(), new HashSet<>());
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
    }

    public static class SmartCastProvider extends CompletionProvider<CompletionParameters> {
        static final ElementPattern<PsiElement> TYPECAST_TYPE_CANDIDATE = PlatformPatterns.psiElement().afterLeaf("(");

        static boolean shouldSuggestCast(CompletionParameters parameters) {
            PsiElement position = parameters.getPosition();
            PsiElement parent = getParenthesisOwner(position);
            if (parent instanceof PsiTypeCastExpression) {
                return true;
            }
            if (parent instanceof PsiParenthesizedExpression) {
                return parameters.getOffset() == position.getTextRange().getStartOffset();
            }
            return false;
        }

        private static PsiElement getParenthesisOwner(PsiElement position) {
            PsiElement lParen = PsiTreeUtil.prevVisibleLeaf(position);
            return lParen == null || !lParen.textMatches("(") ? null : lParen.getParent();
        }

        @Override
        protected void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
            addCastVariants(parameters, result.getPrefixMatcher(), result, false);
        }

        static void addCastVariants(@NotNull CompletionParameters parameters, PrefixMatcher matcher, @NotNull Consumer<LookupElement> result, boolean quick) {
            if (!shouldSuggestCast(parameters)) {
                return;
            }

            PsiElement position = parameters.getPosition();
            PsiElement parenthesisOwner = getParenthesisOwner(position);
            final boolean insideCast = parenthesisOwner instanceof PsiTypeCastExpression;

            if (insideCast) {
                PsiElement parent = parenthesisOwner.getParent();
                if (parent instanceof PsiParenthesizedExpression) {
                    if (parent.getParent() instanceof PsiReferenceExpression) {
                        for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiParenthesizedExpression) parent, false)) {
                            result.consume(PsiTypeLookupItem.createLookupItem(info.getType(), parent));
                        }
                    }
                    ExpectedTypeInfo info = getParenthesizedCastExpectationByOperandType(position);
                    if (info != null) {
                        addHierarchyTypes(parameters, matcher, info, type -> result.consume(PsiTypeLookupItem.createLookupItem(type, parent)), quick);
                    }
                    return;
                }
            }

            for (final ExpectedTypeInfo info : JavaSmartCompletionContributor.getExpectedTypes(parameters)) {
                PsiType type = info.getDefaultType();
                if (type instanceof PsiWildcardType) {
                    type = ((PsiWildcardType) type).getBound();
                }

                if (type == null || PsiType.VOID.equals(type)) {
                    continue;
                }

                if (type instanceof PsiPrimitiveType) {
                    final PsiType castedType = getCastedExpressionType(parenthesisOwner);
                    if (castedType != null && !(castedType instanceof PsiPrimitiveType)) {
                        final PsiClassType boxedType = ((PsiPrimitiveType) type).getBoxedType(position);
                        if (boxedType != null) {
                            type = boxedType;
                        }
                    }
                }
                result.consume(createSmartCastElement(parameters, insideCast, type));
            }
        }

        @Nullable
        static ExpectedTypeInfo getParenthesizedCastExpectationByOperandType(PsiElement position) {
            PsiElement parenthesisOwner = getParenthesisOwner(position);
            PsiExpression operand = getCastedExpression(parenthesisOwner);
            if (operand == null || !(parenthesisOwner.getParent() instanceof PsiParenthesizedExpression)) {
                return null;
            }

            PsiType dfaType = GuessManager.getInstance(operand.getProject()).getControlFlowExpressionType(operand);
            if (dfaType != null) {
                return new ExpectedTypeInfoImpl(dfaType, ExpectedTypeInfo.TYPE_OR_SUPERTYPE, dfaType, TailType.NONE, null, () -> null);
            }

            PsiType type = operand.getType();
            return type == null || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? null :
                new ExpectedTypeInfoImpl(type, ExpectedTypeInfo.TYPE_OR_SUBTYPE, type, TailType.NONE, null, () -> null);
        }

        private static void addHierarchyTypes(CompletionParameters parameters, PrefixMatcher matcher, ExpectedTypeInfo info, Consumer<PsiType> result, boolean quick) {
            PsiType infoType = info.getType();
            PsiClass infoClass = PsiUtil.resolveClassInClassTypeOnly(infoType);
            if (info.getKind() == ExpectedTypeInfo.TYPE_OR_SUPERTYPE) {
                InheritanceUtil.processSupers(infoClass, true, superClass -> {
                    if (!CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) {
                        result.consume(JavaPsiFacade.getElementFactory(superClass.getProject()).createType(superClass));
                    }
                    return true;
                });
            } else if (infoType instanceof PsiClassType && !quick) {
                JavaInheritorsGetter.processInheritors(parameters, Collections.singleton((PsiClassType) infoType), matcher, type -> {
                    if (!infoType.equals(type)) {
                        result.consume(type);
                    }
                });
            }
        }

        private static PsiType getCastedExpressionType(PsiElement parenthesisOwner) {
            PsiExpression operand = getCastedExpression(parenthesisOwner);
            return operand == null ? null : operand.getType();
        }

        private static PsiExpression getCastedExpression(PsiElement parenthesisOwner) {
            if (parenthesisOwner instanceof PsiTypeCastExpression) {
                return ((PsiTypeCastExpression) parenthesisOwner).getOperand();
            }

            if (parenthesisOwner instanceof PsiParenthesizedExpression) {
                PsiElement next = parenthesisOwner.getNextSibling();
                while (next != null && (next instanceof PsiEmptyExpressionImpl || next instanceof PsiErrorElement || next instanceof PsiWhiteSpace)) {
                    next = next.getNextSibling();
                }
                if (next instanceof PsiExpression) {
                    return (PsiExpression) next;
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
    }

    public static class CollectConversion {

        static void addCollectConversion(PsiReferenceExpression ref, Collection<ExpectedTypeInfo> expectedTypes, Consumer<LookupElement> consumer) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            PsiType component = qualifier == null ? null : PsiUtil.substituteTypeParameter(qualifier.getType(), JAVA_UTIL_STREAM_STREAM, 0, true);
            if (component == null) {
                return;
            }

            JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
            PsiElementFactory factory = facade.getElementFactory();
            GlobalSearchScope scope = ref.getResolveScope();
            PsiClass list = facade.findClass(JAVA_UTIL_LIST, scope);
            PsiClass set = facade.findClass(JAVA_UTIL_SET, scope);
            PsiClass collection = facade.findClass(JAVA_UTIL_COLLECTION, scope);
            if (facade.findClass(JAVA_UTIL_STREAM_COLLECTORS, scope) == null || list == null || set == null || collection == null) {
                return;
            }

            PsiType listType = null;
            PsiType setType = null;
            boolean hasIterable = false;
            for (ExpectedTypeInfo info : expectedTypes) {
                PsiType type = info.getDefaultType();
                PsiClass expectedClass = PsiUtil.resolveClassInClassTypeOnly(type);
                PsiType expectedComponent = PsiUtil.extractIterableTypeParameter(type, true);
                if (expectedClass == null || expectedComponent == null || !TypeConversionUtil.isAssignable(expectedComponent, component)) {
                    continue;
                }
                hasIterable = true;

                if (InheritanceUtil.isInheritorOrSelf(list, expectedClass, true)) {
                    listType = type;
                }
                if (InheritanceUtil.isInheritorOrSelf(set, expectedClass, true)) {
                    setType = type;
                }
            }

            if (expectedTypes.isEmpty()) {
                listType = factory.createType(list, component);
                setType = factory.createType(set, component);
            }

            if (listType != null) {
                consumer.consume(new MyLookupElement("toList", listType, ref));
            }
            if (setType != null) {
                consumer.consume(new MyLookupElement("toSet", setType, ref));
            }

            if (expectedTypes.isEmpty() || hasIterable) {
                consumer.consume(new MyLookupElement("toCollection", factory.createType(collection, component), ref));
            }
        }

        private static class MyLookupElement extends LookupElement implements TypedLookupItem {
            private final String myLookupString;
            private final String myTypeText;
            private final String myMethodName;
            @NotNull
            private final PsiType myExpectedType;
            private final boolean myHasImport;

            MyLookupElement(String methodName, @NotNull PsiType expectedType, @NotNull PsiElement context) {
                myMethodName = methodName;
                myExpectedType = expectedType;
                myTypeText = myExpectedType.getPresentableText();

                PsiMethodCallExpression call = (PsiMethodCallExpression)
                    JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(methodName + "()", context);
                myHasImport = ContainerUtil.or(call.getMethodExpression().multiResolve(true), result -> {
                    PsiElement element = result.getElement();
                    return element instanceof PsiMember &&
                        (JAVA_UTIL_STREAM_COLLECTORS + "." + myMethodName).equals(PsiUtil.getMemberQualifiedName((PsiMember) element));
                });

                myLookupString = "collect(" + (myHasImport ? "" : "Collectors.") + myMethodName + "())";
            }

            @NotNull
            @Override
            public String getLookupString() {
                return myLookupString;
            }

            @Override
            public Set<String> getAllLookupStrings() {
                return ContainerUtil.newHashSet(myLookupString, myMethodName);
            }

            @Override
            public void renderElement(LookupElementPresentation presentation) {
                super.renderElement(presentation);
                presentation.setTypeText(myTypeText);
                presentation.setIcon(PlatformIcons.METHOD_ICON);
            }

            @Override
            public void handleInsert(InsertionContext context) {
                context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), getInsertString());
                context.commitDocument();

                PsiMethodCallExpression call =
                    PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
                if (call == null) {
                    return;
                }

                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length != 1 || !(args[0] instanceof PsiMethodCallExpression)) {
                    return;
                }

                PsiMethodCallExpression innerCall = (PsiMethodCallExpression) args[0];
                PsiMethod collectorMethod = innerCall.resolveMethod();
                if (collectorMethod != null && collectorMethod.getParameterList().getParametersCount() > 0) {
                    context.getEditor().getCaretModel().moveToOffset(innerCall.getArgumentList().getFirstChild().getTextRange().getEndOffset());
                }

                JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(innerCall);
            }

            @NotNull
            private String getInsertString() {
                return "collect(" + (myHasImport ? "" : JAVA_UTIL_STREAM_COLLECTORS + ".") + myMethodName + "())";
            }

            @Override
            public PsiType getType() {
                return myExpectedType;
            }
        }
    }

    public static class SameSignatureCallParametersProvider extends CompletionProvider<CompletionParameters> {
        static final PsiElementPattern.Capture<PsiElement> IN_CALL_ARGUMENT =
            PlatformPatterns.psiElement().beforeLeaf(PlatformPatterns.psiElement(JavaTokenType.RPARENTH)).afterLeaf("(").withParent(
                PlatformPatterns.psiElement(PsiReferenceExpression.class).withParent(
                    PlatformPatterns.psiElement(PsiExpressionList.class).withParent(PsiCall.class)));

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            addSignatureItems(parameters, result);
        }

        void addSignatureItems(@NotNull CompletionParameters parameters, @NotNull Consumer<LookupElement> result) {
            final PsiCall methodCall = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiCall.class);
            assert methodCall != null;
            Set<Pair<PsiMethod, PsiSubstitutor>> candidates = getCallCandidates(methodCall);

            PsiMethod container = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
            while (container != null) {
                for (final Pair<PsiMethod, PsiSubstitutor> candidate : candidates) {
                    if (container.getParameterList().getParametersCount() > 1 && candidate.first.getParameterList().getParametersCount() > 1) {
                        PsiMethod from = getMethodToTakeParametersFrom(container, candidate.first, candidate.second);
                        if (from != null) {
                            result.consume(createParametersLookupElement(from, methodCall, candidate.first));
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
            icon.setIcon(PlatformIcons.PARAMETER_ICON, 0, 2 * w / 5, 0);
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
                results = ((PsiMethodCallExpression) expression).getMethodExpression().multiResolve(false);
            } else {
                results = new JavaResolveResult[]{expression.resolveMethodGenerics()};
            }

            PsiMethod toExclude = ExpressionUtils.isConstructorInvocation(expression) ? PsiTreeUtil.getParentOfType(expression, PsiMethod.class)
                : null;

            for (final JavaResolveResult candidate : results) {
                final PsiElement element = candidate.getElement();
                if (element instanceof PsiMethod) {
                    final PsiClass psiClass = ((PsiMethod) element).getContainingClass();
                    if (psiClass != null) {
                        for (Pair<PsiMethod, PsiSubstitutor> overload : psiClass.findMethodsAndTheirSubstitutorsByName(((PsiMethod) element).getName(), true)) {
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

    public static class CheckInitialized implements ElementFilter {
        private final Set<PsiField> myNonInitializedFields;
        private final boolean myInsideConstructorCall;

        CheckInitialized(@NotNull PsiElement position) {
            myNonInitializedFields = getNonInitializedFields(position);
            myInsideConstructorCall = isInsideConstructorCall(position);
        }

        static boolean isInsideConstructorCall(@NotNull PsiElement position) {
            return ExpressionUtils.isConstructorInvocation(PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class)) &&
                !JavaKeywordCompletion.AFTER_DOT.accepts(position);
        }

        private static boolean isInitializedImplicitly(PsiField field) {
            field = CompletionUtil.getOriginalOrSelf(field);
            for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensions()) {
                if (provider.isImplicitWrite(field)) {
                    return true;
                }
            }
            return false;
        }

        static Set<PsiField> getNonInitializedFields(PsiElement element) {
            final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
            //noinspection SSBasedInspection
            final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
            if (statement == null || method == null || !method.isConstructor()) {
                return Collections.emptySet();
            }

            PsiElement parent = element.getParent();
            if (parent instanceof PsiReferenceExpression && !DfaValueFactory.isEffectivelyUnqualified((PsiReferenceExpression) parent)) {
                return Collections.emptySet();
            }

            while (parent != statement) {
                PsiElement next = parent.getParent();
                if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression) next).getLExpression()) {
                    return Collections.emptySet();
                }
                if (parent instanceof PsiJavaCodeReferenceElement) {
                    PsiStatement psiStatement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
                    if (psiStatement != null && psiStatement.getTextRange().getStartOffset() == parent.getTextRange().getStartOffset()) {
                        return Collections.emptySet();
                    }
                }
                parent = next;
            }

            final Set<PsiField> fields = new HashSet<>();
            final PsiClass containingClass = method.getContainingClass();
            assert containingClass != null;
            for (PsiField field : containingClass.getFields()) {
                if (!field.hasModifierProperty(PsiModifier.STATIC) && field.getInitializer() == null && !isInitializedImplicitly(field)) {
                    fields.add(field);
                }
            }

            method.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitAssignmentExpression(PsiAssignmentExpression expression) {
                    if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
                        final PsiExpression lExpression = expression.getLExpression();
                        if (lExpression instanceof PsiReferenceExpression) {
                            //noinspection SuspiciousMethodCalls
                            fields.remove(((PsiReferenceExpression) lExpression).resolve());
                        }
                    }
                    super.visitAssignmentExpression(expression);
                }

                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                    if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
                        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
                        if (methodExpression.textMatches(PsiKeyword.THIS)) {
                            fields.clear();
                        }
                    }
                    super.visitMethodCallExpression(expression);
                }
            });
            return fields;
        }

        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
            if (element instanceof CandidateInfo) {
                element = ((CandidateInfo) element).getElement();
            }
            if (element instanceof PsiField) {
                return !myNonInitializedFields.contains(element);
            }
            if (element instanceof PsiMethod && myInsideConstructorCall) {
                return ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
            }

            return true;
        }

        @Override
        public boolean isClassAcceptable(Class hintClass) {
            return true;
        }
    }

    static class IndentingDecorator extends LookupElementDecorator<LookupElement> {
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

    static class LabelReferenceCompletion extends CompletionProvider<CompletionParameters> {
        static final ElementPattern<PsiElement> LABEL_REFERENCE = PlatformPatterns.psiElement().afterLeaf(PsiKeyword.BREAK, PsiKeyword.CONTINUE);

        static List<LookupElement> processLabelReference(PsiLabelReference ref) {
            return ContainerUtil.map(ref.getVariants(), s -> TailTypeDecorator.withTail(LookupElementBuilder.create(s), TailType.SEMICOLON));
        }

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiReference ref = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
            if (ref instanceof PsiLabelReference) {
                result.addAllElements(processLabelReference((PsiLabelReference) ref));
            }
        }
    }

    static class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
        static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

        @Override
        public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
            int offset = context.getTailOffset() - 1;
            final PsiFile file = context.getFile();
            if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatementBase.class, false) != null) {
                final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaCodeReferenceElement.class, false);
                final String qname = item.getQualifiedName();
                if (qname != null && (ref == null || !qname.equals(ref.getCanonicalText()))) {
                    AllClassesGetter.INSERT_FQN.handleInsert(context, item);
                }
                return;
            }

            PsiElement position = file.findElementAt(offset);
            PsiJavaCodeReferenceElement ref = position != null && position.getParent() instanceof PsiJavaCodeReferenceElement ?
                (PsiJavaCodeReferenceElement) position.getParent() : null;
            PsiClass psiClass = item.getObject();
            final Project project = context.getProject();

            final Editor editor = context.getEditor();
            final char c = context.getCompletionChar();
            if (c == '#') {
                context.setLaterRunnable(() -> new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor));
            } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
                AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
            }

            String qname = psiClass.getQualifiedName();
            if (qname != null && PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null &&
                (ref == null || !ref.isQualified()) &&
                shouldInsertFqnInJavadoc(item, file, project)) {
                context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), qname);
                return;
            }

            if (ref != null && PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(psiClass)) {
                return;
            }

            OffsetKey refEnd = context.trackOffset(context.getTailOffset(), false);

            boolean fillTypeArgs = context.getCompletionChar() == '<';
            if (fillTypeArgs) {
                context.setAddCompletionChar(false);
            }

            if (ref == null || !ref.isQualified()) {
                PsiTypeLookupItem.addImportForItem(context, psiClass);
            }
            if (!context.getOffsetMap().containsOffset(refEnd)) {
                return;
            }

            context.setTailOffset(context.getOffset(refEnd));

            context.commitDocument();
            if (item.getUserData(JavaChainLookupElement.CHAIN_QUALIFIER) == null &&
                shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
                if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
                    fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
                }
            } else if (insertingAnnotation(context, item)) {
                if (shouldHaveAnnotationParameters(psiClass)) {
                    JavaCompletionUtil.insertParentheses(context, item, false, true);
                }
                if (context.getCompletionChar() == Lookup.NORMAL_SELECT_CHAR || context.getCompletionChar() == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
                    CharSequence text = context.getDocument().getCharsSequence();
                    int tail = context.getTailOffset();
                    if (text.length() > tail && Character.isLetter(text.charAt(tail))) {
                        context.getDocument().insertString(tail, " ");
                    }
                }
            }

            if (fillTypeArgs && context.getCompletionChar() != '(') {
                JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refEnd));
            }
        }

        private static boolean shouldInsertFqnInJavadoc(@NotNull JavaPsiClassReferenceElement item,
                                                        @NotNull PsiFile file,
                                                        @NotNull Project project) {
            CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
            JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);

            switch (javaSettings.CLASS_NAMES_IN_JAVADOC) {
                case FULLY_QUALIFY_NAMES_ALWAYS:
                    return true;
                case SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT:
                    return false;
                case FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED:
                    if (file instanceof PsiJavaFile) {
                        PsiJavaFile javaFile = ((PsiJavaFile) file);
                        return item.getQualifiedName() != null && !ImportHelper.isAlreadyImported(javaFile, item.getQualifiedName());
                    }
                default:
                    return false;
            }
        }

        private static boolean shouldInsertParentheses(PsiElement position) {
            final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
            if (ref == null) {
                return false;
            }

            final PsiReferenceParameterList parameterList = ref.getParameterList();
            if (parameterList != null && parameterList.getTextLength() > 0) {
                return false;
            }

            final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
            if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
                return !isArrayTypeExpected((PsiExpression) prevElement.getParent());
            }

            return false;
        }

        static boolean isArrayTypeExpected(PsiExpression expr) {
            return ContainerUtil.exists(ExpectedTypesProvider.getExpectedTypes(expr, true),
                info -> info.getType() instanceof PsiArrayType);
        }

        private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
            final Object obj = item.getObject();
            if (!(obj instanceof PsiClass) || !((PsiClass) obj).isAnnotationType()) {
                return false;
            }

            PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
            PsiAnnotation anno = PsiTreeUtil.getParentOfType(leaf, PsiAnnotation.class);
            return anno != null && PsiTreeUtil.isAncestor(anno.getNameReferenceElement(), leaf, false);
        }

        static boolean shouldHaveAnnotationParameters(PsiClass annoClass) {
            for (PsiMethod m : annoClass.getMethods()) {
                if (!PsiUtil.isAnnotationMethod(m)) {
                    continue;
                }
                if (((PsiAnnotationMethod) m).getDefaultValue() == null) {
                    return true;
                }
            }
            return false;
        }
    }

    final public static class LombokElementFilter implements ElementFilter {
        public static final ElementFilter INSTANCE = new LombokElementFilter();

        private LombokElementFilter() {
        }

        @Override
        public boolean isAcceptable(Object element, @Nullable PsiElement context) {
            if (element instanceof PsiMethod) {
                return filterExtensionMethods((PsiMethod) element, context);
            }
            if (element instanceof PsiField) {
                return filterFieldDefault((PsiField) element, context);
            }
            return true;
        }

        /**
         * also manual handle access modifiers
         */
        private boolean filterFieldDefault(@NotNull PsiField field, @Nullable PsiElement context) {
            return context == null || isAccessible(field, context);

        }

        private boolean filterExtensionMethods(@NotNull PsiMethod method, @Nullable PsiElement context) {
            if (context == null) {
                return true;
            }
            PsiClass containingClass = ClassUtils.getContainingClass(context);
            if (containingClass == null) {
                return true;
            }

            PsiReferenceExpression expression = PsiTreeUtil.getParentOfType(context.getContainingFile().findElementAt(context.getTextOffset()), PsiReferenceExpression.class);
            if (expression == null) {
                return true;
            }

            PsiType type = getCallType(expression, containingClass);
            if (type == null) {
                return true;
            }

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
        if (context.getDummyIdentifier() == null) {
            return;
        }

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
            Method addExpectedTypeMembers = ReflectUtil.getMethod(JavaCompletionContributor.class, void.class, "addExpectedTypeMembers", CompletionParameters.class, Consumer.class);
            ReflectUtil.invokeMethod(addExpectedTypeMembers, parameters, result);
            Method addPrimitiveTypes = ReflectUtil.getMethod(com.intellij.codeInsight.completion.JavaKeywordCompletion.class, void.class, "addPrimitiveTypes", Consumer.class, PsiElement.class, JavaCompletionSession.class);
            ReflectUtil.invokeMethod(addPrimitiveTypes, result, position, session);
            completeAnnotationAttributeName(result, position, parameters);
            result.stopHere();
            return;
        }

        PrefixMatcher matcher = result.getPrefixMatcher();
        PsiElement parent = position.getParent();

        if (JavaModuleCompletion.isModuleFile(parameters.getOriginalFile())) {
            JavaModuleCompletion.addVariants(position, result);
            result.stopHere();
            return;
        }

        if (addWildcardExtendsSuper(result, position)) {
            return;
        }

        if (position instanceof PsiIdentifier) {
            addIdentifierVariants(parameters, position, result, session, matcher);
        }

        MultiMap<CompletionResultSet, LookupElement> referenceVariants = addReferenceVariants(parameters, result, session);
        Set<String> usedWords = ContainerUtil.map2Set(referenceVariants.values(), LookupElement::getLookupString);
        for (Map.Entry<CompletionResultSet, Collection<LookupElement>> entry : referenceVariants.entrySet()) {
            Method registerBatchItems = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "registerBatchItems", CompletionResultSet.class, Collection.class);
            ReflectUtil.invokeVirtual(registerBatchItems, session, entry.getKey(), entry.getValue());
        }

        final Method flushBatchItems = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "flushBatchItems");
        ReflectUtil.invokeVirtual(flushBatchItems, session);

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
            FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, true, result.getPrefixMatcher(), result);
        }

        if (position instanceof PsiIdentifier &&
            parent instanceof PsiReferenceExpression &&
            !((PsiReferenceExpression) parent).isQualified() &&
            parameters.isExtendedCompletion() &&
            StringUtil.isNotEmpty(matcher.getPrefix())) {
            new JavaStaticMemberProcessor(parameters).processStaticMethodsGlobally(matcher, result);
        }

        result.stopHere();
    }

    static boolean addWildcardExtendsSuper(CompletionResultSet result, PsiElement position) {
        if (JavaMemberNameCompletionContributor.INSIDE_TYPE_PARAMS_PATTERN.accepts(position)) {
            for (String keyword : ContainerUtil.ar(PsiKeyword.EXTENDS, PsiKeyword.SUPER)) {
                if (keyword.startsWith(result.getPrefixMatcher().getPrefix())) {
                    result.addElement(new com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace(BasicExpressionCompletionContributor.createKeywordLookupItem(position, keyword), TailType.HUMBLE_SPACE_BEFORE_WORD));
                }
            }
            return true;
        }
        return false;
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
        PsiAnnotation anno = (PsiAnnotation) parameterList.getParent();
        boolean showClasses = psiElement().afterLeaf("(").accepts(insertedElement);
        PsiClass annoClass = null;
        final PsiJavaCodeReferenceElement referenceElement = anno.getNameReferenceElement();
        if (referenceElement != null) {
            final PsiElement element = referenceElement.resolve();
            if (element instanceof PsiClass) {
                annoClass = (PsiClass) element;
                if (annoClass.findMethodsByName("value", false).length == 0) {
                    showClasses = false;
                }
            }
        }

        if (showClasses && insertedElement.getParent() instanceof PsiReferenceExpression) {
            final Set<LookupElement> set = JavaCompletionUtil.processJavaReference(
                insertedElement, (PsiJavaReference) insertedElement.getParent(), new ElementExtractorFilter(createAnnotationFilter(insertedElement)), JavaCompletionProcessor.Options.DEFAULT_OPTIONS, result.getPrefixMatcher(), parameters);
            for (final LookupElement element : set) {
                result.addElement(element);
            }
            addAllClasses(parameters, result, new JavaCompletionSession(result));
        }

        if (annoClass != null) {
            final PsiNameValuePair[] existingPairs = parameterList.getAttributes();

            methods:
            for (PsiMethod method : annoClass.getMethods()) {
                if (!(method instanceof PsiAnnotationMethod)) {
                    continue;
                }

                final String attrName = method.getName();
                for (PsiNameValuePair existingAttr : existingPairs) {
                    if (PsiTreeUtil.isAncestor(existingAttr, insertedElement, false)) {
                        break;
                    }
                    if (Comparing.equal(existingAttr.getName(), attrName) ||
                        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(attrName) && existingAttr.getName() == null) {
                        continue methods;
                    }
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

                PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod) method).getDefaultValue();
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
                    return element instanceof PsiAnnotationMethod && PsiUtil.isAnnotationMethod((PsiElement) element);
                }
            });
        }
        return orFilter;
    }

    private static boolean hasFieldDefaults(PsiReferenceExpression reference) {
        if (reference == null) {
            return false;
        }
        PsiElement callElement = reference.resolve();

        PsiClass annotatedClass = null;
        if (callElement instanceof PsiLocalVariable) {
            annotatedClass = getPsiClass(((PsiLocalVariable) callElement).getType());
        }
        if (callElement instanceof PsiField) {
            annotatedClass = ((PsiField) callElement).getContainingClass();
        }
        if (callElement instanceof PsiClass) {
            annotatedClass = (PsiClass) callElement;
        }

        return annotatedClass != null && PsiAnnotationUtil.findAnnotation(annotatedClass, FieldDefaults.class) != null;
    }

    private static MultiMap<CompletionResultSet, LookupElement> addReferenceVariants(final CompletionParameters parameters, CompletionResultSet result, final JavaCompletionSession session) {
        MultiMap<CompletionResultSet, LookupElement> items = MultiMap.create();
        final PsiElement position = parameters.getPosition();
        final boolean first = parameters.getInvocationCount() <= 1;
        final boolean isSwitchLabel = SWITCH_LABEL.accepts(position);
        final boolean isAfterNew = JavaClassNameCompletionContributor.AFTER_NEW.accepts(position);
        final boolean pkgContext = JavaCompletionUtil.inSomePackage(position);
        final PsiType[] expectedTypes = ExpectedTypesGetter.getExpectedTypes(parameters.getPosition(), true);
        LegacyCompletionContributor.processReferences(parameters, result, (reference, result1) -> {
            if (reference instanceof PsiJavaReference) {
                // check access (SS support field defaults)
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
                                if (checkAccess != first) {
                                    break;
                                }
                            }
                        }

                    }
                }

                ElementFilter filter = getReferenceFilter(position);
                if (filter != null) {
                    if (INSIDE_CONSTRUCTOR.accepts(position) &&
                        (parameters.getInvocationCount() <= 1 || CheckInitialized.isInsideConstructorCall(position))) {
                        filter = new AndFilter(filter, new CheckInitialized(position));
                    }
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
                        result1.getPrefixMatcher(), parameters)) {
                        if (session.alreadyProcessed(element)) {
                            continue;
                        }

                        if (isSwitchLabel) {
                            items.putValue(result1, new IndentingDecorator(TailTypeDecorator.withTail(element, TailType.createSimpleTailType(':'))));
                        } else {
                            final LookupItem item = element.as(LookupItem.CLASS_CONDITION_KEY);
                            if (originalFile instanceof PsiJavaCodeReferenceCodeFragment &&
                                !((PsiJavaCodeReferenceCodeFragment) originalFile).isClassesAccepted() && item != null) {
                                item.setTailType(TailType.NONE);
                            }
                            if (item instanceof JavaMethodCallElement) {
                                JavaMethodCallElement call = (JavaMethodCallElement) item;
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

                            items.putValue(result1, element);
                        }
                    }
                }
                return;
            }
            if (reference instanceof PsiLabelReference) {
                items.putValues(result1, LabelReferenceCompletion.processLabelReference((PsiLabelReference) reference));
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
                    items.putValue(result1, (LookupElement) completion);
                } else if (completion instanceof PsiClass) {
                    Condition<PsiClass> condition = psiClass -> !session.alreadyProcessed(psiClass) &&
                        JavaCompletionUtil.isSourceLevelAccessible(position, psiClass, pkgContext);
                    items.putValues(result1, JavaClassNameCompletionContributor.createClassLookupItems(
                        (PsiClass) completion,
                        isAfterNew,
                        JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER,
                        condition));
                } else {
                    //noinspection deprecation
                    items.putValue(result1, LookupItemUtil.objectToLookupItem(completion));
                }
            }
        });
        return items;
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
                                              JavaCompletionSession session, PrefixMatcher matcher) {
        Method registerBatchItems = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "registerBatchItems", CompletionResultSet.class, Collection.class);
        registerBatchItems.setAccessible(true);
        try{
            registerBatchItems.invoke(session, result, getFastIdentifierVariants(parameters, position, matcher, position.getParent(), session));
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error(e);
        }

        if (JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
            final Method flushBatchItems = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "flushBatchItems");
            ReflectUtil.invokeVirtual(flushBatchItems, session);
            new JavaInheritorsGetter(ConstructorInsertHandler.BASIC_INSTANCE).generateVariants(parameters, matcher, session::addClassItem);
        }

        suggestSmartCast(parameters, session, false, result);
    }

    private static void suggestSmartCast(CompletionParameters parameters, JavaCompletionSession session, boolean quick, Consumer<LookupElement> result) {
        if (SmartCastProvider.shouldSuggestCast(parameters)) {
            final Method flushBatchItems = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "flushBatchItems");
            ReflectUtil.invokeVirtual(flushBatchItems, session);

            final Method getMatcher = ReflectUtil.getMethod(JavaCompletionSession.class, PrefixMatcher.class, "getMatcher");
            SmartCastProvider.addCastVariants(parameters, ReflectUtil.invokeVirtual(getMatcher, session), element -> {
                registerClassFromTypeElement(element, session);
                result.consume(PrioritizedLookupElement.withPriority(element, 1));
            }, quick);
        }
    }


    private static List<LookupElement> getFastIdentifierVariants(@NotNull CompletionParameters parameters,
                                                                 PsiElement position,
                                                                 PrefixMatcher matcher,
                                                                 PsiElement parent,
                                                                 @NotNull JavaCompletionSession session) {
        List<LookupElement> items = new ArrayList<>();
        if (TypeArgumentCompletionProviderEx.IN_TYPE_ARGS.accepts(position)) {
            new TypeArgumentCompletionProviderEx(false, session).addTypeArgumentVariants(parameters, items::add, matcher);
        }

        FunctionalExpressionCompletionProvider.addFunctionalVariants(parameters, false, false, matcher, items::add);

        if (MethodReturnTypeProvider.IN_METHOD_RETURN_TYPE.accepts(position)) {
            MethodReturnTypeProvider.addProbableReturnTypes(parameters, element -> {
                registerClassFromTypeElement(element, session);
                items.add(element);
            });
        }

        suggestSmartCast(parameters, session, true, items::add);

        if (parent instanceof PsiReferenceExpression) {
            final List<ExpectedTypeInfo> expected = Arrays.asList(ExpectedTypesProvider.getExpectedTypes((PsiExpression) parent, true));
            CollectConversion.addCollectConversion((PsiReferenceExpression) parent, expected,
                lookupElement -> items.add(JavaSmartCompletionContributor.decorate(lookupElement, expected)));
        }

        if (IMPORT_REFERENCE.accepts(position)) {
            items.add(LookupElementBuilder.create("*"));
        }

        try{
            final Constructor<com.intellij.codeInsight.completion.JavaKeywordCompletion> declaredConstructor = com.intellij.codeInsight.completion.JavaKeywordCompletion.class.getDeclaredConstructor(CompletionParameters.class, JavaCompletionSession.class);
            declaredConstructor.setAccessible(true);
            com.intellij.codeInsight.completion.JavaKeywordCompletion completion = declaredConstructor.newInstance(parameters, session);
            final Method getResults = ReflectUtil.getMethod(com.intellij.codeInsight.completion.JavaKeywordCompletion.class, List.class, "getResults");
            getResults.setAccessible(true);
            items.addAll((List<LookupElement>) getResults.invoke(completion));
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            LOG.error(e);
        }

        addExpressionVariants(parameters, position, items::add);
        return items;
    }

    private static void registerClassFromTypeElement(LookupElement element, JavaCompletionSession session) {
        PsiType type = assertNotNull(element.as(PsiTypeLookupItem.CLASS_CONDITION_KEY)).getType();
        if (type instanceof PsiPrimitiveType) {
            final Method registerKeyword = ReflectUtil.getMethod(JavaCompletionSession.class, void.class, "registerKeyword", String.class);
            ReflectUtil.invokeVirtual(registerKeyword, session, type.getCanonicalText(false));
            return;
        }

        PsiClass aClass =
            type instanceof PsiClassType && ((PsiClassType) type).getParameterCount() == 0 ? ((PsiClassType) type).resolve() : null;
        if (aClass != null) {
            session.registerClass(aClass);
        }
    }

    private static void addExpressionVariants(@NotNull CompletionParameters parameters, PsiElement position, Consumer<LookupElement> result) {
        if (JavaSmartCompletionContributor.INSIDE_EXPRESSION.accepts(position) &&
            !com.intellij.codeInsight.completion.JavaKeywordCompletion.AFTER_DOT.accepts(position) && !SmartCastProvider.shouldSuggestCast(parameters)) {

            Method addExpectedTypeMembers = ReflectUtil.getMethod(JavaCompletionContributor.class, void.class, "addExpectedTypeMembers", CompletionParameters.class, Consumer.class);
            ReflectUtil.invokeMethod(addExpectedTypeMembers, parameters, result);

            if (IN_CALL_ARGUMENT.accepts(position)) {
                new SameSignatureCallParametersProvider().addSignatureItems(parameters, result);
            }
        }
    }
}
