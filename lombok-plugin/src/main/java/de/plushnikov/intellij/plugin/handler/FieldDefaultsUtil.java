package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorParameterFromFieldFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InitializeFinalFieldInConstructorFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.psi.LombokLightMethod;
import de.plushnikov.intellij.plugin.psi.LombokLightModifierList;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.AnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiFieldUtil.isFinal;

/**
 * @author Suburban Squirrel
 * @version 1.0.5
 * @since 1.0.4
 */
final public class FieldDefaultsUtil {
  private static final String MESSAGE1 = "Variable '%s' might not have been initialized...";
  private static final String MESSAGE2 = "Cannot assign a value to final variable '%s'...";
  private static final String MESSAGE1_FIX = "Add constructor parameter...";
  private static final String MESSAGE2_FIX = "Initialize in constructor...";
  private static final String MESSAGE3_FIX = "Initialize in variable '%s'...";

  final private static class FieldRef {
    private PsiField psiField;
    private PsiReference reference;

    public FieldRef(PsiField field, PsiReference reference) {
      this.psiField = field;
      this.reference = reference;
    }

    public PsiField getPsiField() { return psiField; }

    public PsiReference getReference() { return reference; }
  }
  public static void handleClass(@NotNull final PsiClass psiClass, @NotNull ProblemsHolder holder) {
    PsiField[] fields = psiClass.getFields();

  // collect inited fields
    Set<FieldRef> errorFieldRef = new HashSet<FieldRef>();
    Set<PsiField> alreadyInitedFields = getAlreadyInitedFieldsFindErrorRefs(psiClass, errorFieldRef);
    List<PsiField> errorFields = Arrays.stream(fields).filter(field -> !alreadyInitedFields.contains(field)).collect(Collectors.toList());

    // register already initialized field
    for (FieldRef fieldRef : errorFieldRef) {
      holder.registerProblem((PsiElement)fieldRef.getReference(), String.format(MESSAGE2, fieldRef.getPsiField().getName()), ProblemHighlightType.GENERIC_ERROR);
    }

    errorFields.stream().filter(field -> isFinal(field) && !field.hasModifierProperty(PsiModifier.FINAL)).forEach(field ->
        holder.registerProblem(field, String.format(MESSAGE1, field.getName()), ProblemHighlightType.GENERIC_ERROR,
        new LocalQuickFixBase(MESSAGE1_FIX) {
          @Override
          public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiFile psiFile = psiClass.getContainingFile();
            final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, psiClass.getLBrace());
            if (editor != null) {
              new WriteCommandAction(project, psiFile) {
                @Override
                protected void run(Result result) throws Throwable {
                  IntentionAction fix = new CreateConstructorParameterFromFieldFix(field);
                  if (fix.isAvailable(project, editor, psiFile)) fix.invoke(field.getProject(), editor, psiFile);
                }

                @Override
                protected boolean isGlobalUndoAction() {
                  return true;
                }
              }.execute();
            }
          }
        }, new LocalQuickFixBase(MESSAGE2_FIX) {
          @Override
          public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiFile psiFile = psiClass.getContainingFile();
            final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, psiClass.getLBrace());
            if (editor != null) {
              new WriteCommandAction(project, psiFile) {
                protected void run(@NotNull Result result) throws Throwable {
                  IntentionAction fix = new InitializeFinalFieldInConstructorFix(field);
                  if (fix.isAvailable(project, editor, psiFile)) fix.invoke(field.getProject(), editor, psiFile);
                }

                @Override
                protected boolean isGlobalUndoAction() {
                  return true;
                }
              }.execute();
            }
          }
        }, new LocalQuickFixBase(String.format(MESSAGE3_FIX, field.getName())) {
          @Override
          public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            final PsiFile psiFile = psiClass.getContainingFile();
            final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, psiClass.getLBrace());
            if (editor != null) {
              new WriteCommandAction(project, psiFile) {
                protected void run(@NotNull Result result) throws Throwable {
                  final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
                  final PsiExpression newCall = factory.createExpressionFromText(createInitializer(field), field);
                  field.setInitializer(newCall);
                }

                @Override
                protected boolean isGlobalUndoAction() {
                  return true;
                }
              }.execute();
            }
          }
        }
    ));
  }

  @NotNull
  private static Set<PsiField> getAlreadyInitedFieldsFindErrorRefs(@NotNull PsiClass psiClass, @NotNull Set<FieldRef> errorFieldRef) {
    PsiField[] fields = psiClass.getFields();

  // collect inited fields
  // field was initialized or it errors default by IDEA API (hasInitializer() sometime return false when must - true)  --SS
    Set<PsiField> alreadyInitedFields = Arrays.stream(fields).filter(field -> field.getInitializer() != null || field.hasModifierProperty(PsiModifier.FINAL)).collect(Collectors.toSet());

  // collect inited fields from class initializers and find some error references into them
    for (PsiClassInitializer classInitializer : psiClass.getInitializers()) {
      handleFieldsInitializedInInitializer(psiClass, errorFieldRef, alreadyInitedFields, classInitializer);
    }

  // find error references into constructors
    List<PsiMethod> constructors = new ArrayList<PsiMethod>();
    List<PsiMethod> methods = new ArrayList<PsiMethod>();
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.isConstructor()) {
        constructors.add(method);
      } else {
        methods.add(method);
      }
    }

    Map<PsiMethod, Set<PsiField>> initInConstructors = new HashMap<PsiMethod, Set<PsiField>>();
    while (constructors.size() != 0) {
      for (int i = 0; i < constructors.size(); i++) {
        Set<PsiField> initedFields = new HashSet<PsiField>();

        PsiMethod parentConstructor = getThisCall(constructors.get(i), constructors.get(i).getName());
        if (parentConstructor != null) {
          if (initInConstructors.containsKey(parentConstructor)) {
            initedFields.addAll(initInConstructors.get(parentConstructor));
          } else {
            continue;
          }
        }

        handleFieldInitializedInConstructor(psiClass, errorFieldRef, fields, alreadyInitedFields, constructors.get(i), initedFields);

      // remove constructor from list and offset iterator
        initInConstructors.put(constructors.get(i), initedFields);
        constructors.remove(i);
        i--;
      }
    }

  // find final not initialized fields
    List<PsiField> errorFields = new ArrayList<PsiField>();              // collect there error fields
    for (PsiField field : fields) {
      if (!alreadyInitedFields.contains(field)) errorFields.add(field);
    }

  // add inited in constructors fields and remove initialized fields from error list
    if (!initInConstructors.isEmpty()) {
      constructorInitializedLoop:
      for (int i = 0; i < errorFields.size(); i++) {
        for (PsiMethod constructor : initInConstructors.keySet()) {
          if (!initInConstructors.get(constructor).contains(errorFields.get(i))) continue constructorInitializedLoop;
        }

      // - field initialized in all constructors, fix mistake
        alreadyInitedFields.add(errorFields.remove(i--));
      }
    }

  // find error references in methods;
    for (PsiMethod method : methods) {
      handleInitializedInMethod(psiClass, errorFieldRef, alreadyInitedFields, method);
    }

    return alreadyInitedFields;
  }


  private static void handleInitializedInMethod(@NotNull PsiClass psiClass, @NotNull Set<FieldRef> errorFieldRef, @NotNull Set<PsiField> alreadyInitedFields, @NotNull PsiMethod method) {
  // check the fields of the parent
    getFields(method).stream().filter(fieldRef -> isFinalByFieldDefault(fieldRef.getPsiField())).forEach(fieldRef -> {
      PsiClass fieldContainingClass = fieldRef.getPsiField().getContainingClass();

      if (isNeededInit(fieldContainingClass, psiClass)) {
      // - check the fields of the parent
        if (getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>()).contains(fieldRef.getPsiField())) {
          errorFieldRef.add(fieldRef);
        } else {
          alreadyInitedFields.add(fieldRef.getPsiField());
        }
      } else {
        if (alreadyInitedFields.contains(fieldRef.getPsiField())) errorFieldRef.add(fieldRef);
      }
    });
  }

  private static void handleFieldInitializedInConstructor(@NotNull PsiClass psiClass, @NotNull Set<FieldRef> errorFieldRef, @NotNull PsiField[] allClassFields,
                                                          @NotNull Set<PsiField> alreadyInitedFields, @NotNull PsiMethod constructor, @NotNull Set<PsiField> initedFields) {
  // check the fields of the parent
    final List<FieldRef> fieldRefs = new ArrayList<>();
    if (constructor instanceof LombokLightMethod) {
      for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
        Arrays.stream(allClassFields).filter(field -> field.getName().equals(parameter.getName())).findAny().ifPresent(field -> fieldRefs.add(new FieldRef(field, null)));
      }
    } else {
      fieldRefs.addAll(getFields(constructor));
    }
    fieldRefs.stream().filter(fieldRef -> isFinalByFieldDefault(fieldRef.getPsiField())).forEach(fieldRef -> {
      PsiClass fieldContainingClass = fieldRef.getPsiField().getContainingClass();

      if (isNeededInit(fieldContainingClass, psiClass)) {
      // - check the fields of the parent
        if (getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>()).contains(fieldRef.getPsiField())) {
          errorFieldRef.add(fieldRef);
        } else {
          alreadyInitedFields.add(fieldRef.getPsiField());
        }
      } else {
        if (initedFields.contains(fieldRef.getPsiField()) || alreadyInitedFields.contains(fieldRef.getPsiField())) {
          errorFieldRef.add(fieldRef);
        } else {
          initedFields.add(fieldRef.getPsiField());
        }
      }
    });
  }

  private static void handleFieldsInitializedInInitializer(@NotNull PsiClass psiClass, @NotNull Set<FieldRef> errorFieldRef, @NotNull Set<PsiField> alreadyInitedFields, @NotNull PsiClassInitializer element) {
  // check the fields of the parent
    getFields(element).stream().filter(fieldRef -> isFinalByFieldDefault(fieldRef.getPsiField())).forEach(field -> {
      PsiClass fieldContainingClass = field.getPsiField().getContainingClass();

      if (isNeededInit(fieldContainingClass, psiClass)) {
      // - check the fields in the parent
        if (getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>()).contains(field.getPsiField())) {
          errorFieldRef.add(field);
        } else {
          alreadyInitedFields.add(field.getPsiField());
        }
      } else {
        if (alreadyInitedFields.contains(field.getPsiField())) {
          errorFieldRef.add(field);
        } else {
          alreadyInitedFields.add(field.getPsiField());
        }
      }
    });
  }

  private static boolean isFinalByFieldDefault(@NotNull PsiField field) {
    return !field.hasModifierProperty(PsiModifier.FINAL) && PsiFieldUtil.isFinal(field);
  }

  private static boolean isNeededInit(@Nullable PsiClass fieldContainingClass, @NotNull PsiClass psiClass) {
    return fieldContainingClass != null && !fieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT)
        && psiClass.getQualifiedName() != null && !psiClass.getQualifiedName().equals(fieldContainingClass.getQualifiedName());
  }

  @NotNull
  private static String createInitializer(@NotNull PsiField field) {
    PsiType type = field.getType();
    String initializer;
    if (TypeConversionUtil.isBooleanType(type)) {
      initializer = "false";
    } else if (TypeConversionUtil.isPrimitive(type.getCanonicalText())) {
      initializer = "0";
    } else {
      initializer = "new " + type.getCanonicalText() + "()";      // in standard realization 'null'
    }

    return initializer;
  }

  @Nullable
  private static PsiMethod getThisCall(@NotNull PsiElement parent, @NotNull String name) {
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getChildOfType(parent, PsiMethodCallExpression.class);

  // if find this() call return constructor
    if (methodCallExpression != null && methodCallExpression.getText().startsWith("this")) {
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method != null && method.isConstructor() && name.equals(method.getName())) return method;
    }

  // find in children
    for (PsiElement child : parent.getChildren()) {
      PsiMethod method = getThisCall(child, name);
      if (method != null) return method;
    }

    return null;                  // can't find
  }

  @NotNull
  private static List<FieldRef> getFields(@NotNull PsiElement element) {
    return getFields(element, new ArrayList<FieldRef>());
  }

  @NotNull
  private static List<FieldRef> getFields(@NotNull PsiElement parent, @NotNull List<FieldRef> innerInitFields) {
    for (PsiElement child : parent.getChildren()) {
      if (child instanceof PsiAssignmentExpression) {
        PsiExpression lExpression = ((PsiAssignmentExpression) child).getLExpression();
        if (lExpression instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression) lExpression).resolve();
          if (element instanceof PsiField) innerInitFields.add(new FieldRef((PsiField) element, (PsiReference) lExpression));
        }
      } else {
        getFields(child, innerInitFields);
      }
    }
    return innerInitFields;
  }

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
}
