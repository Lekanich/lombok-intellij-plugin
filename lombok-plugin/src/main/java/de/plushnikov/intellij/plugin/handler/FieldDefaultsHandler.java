package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorParameterFromFieldFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InitializeFinalFieldInConstructorFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.plushnikov.intellij.plugin.util.PsiFieldUtil.isFinal;

/**
 * @author Suburban Squirrel
 * @version 1.0.5
 * @since 1.0.4
 */
public class FieldDefaultsHandler {
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

  abstract private static class BaseLocalFix implements LocalQuickFix {
    private String name;

    public BaseLocalFix(String name) {
      this.name = name;
    }

    @NotNull @Override public String getName() { return name; }
    @NotNull @Override public String getFamilyName() { return getName(); }
  }

  public static void handleClass(@NotNull final PsiClass psiClass, @NotNull ProblemsHolder holder) {
    PsiField[] fields = psiClass.getFields();

  // collect inited fields
    Set<FieldRef> errorFieldRef = new HashSet<FieldRef>();
    Set<PsiField> alreadyInitedFields = getAlreadyInitedFieldsFindErrorRefs(psiClass, errorFieldRef);

    List<PsiField> errorFields = new ArrayList<PsiField>();
    for (PsiField field : fields) {
      if (!alreadyInitedFields.contains(field)) errorFields.add(field);
    }

  // register already initialized field
    for (FieldRef fieldRef : errorFieldRef) {
      holder.registerProblem((PsiElement)fieldRef.getReference(), String.format(MESSAGE2, fieldRef.getPsiField().getName()), ProblemHighlightType.GENERIC_ERROR);
    }

    for (final PsiField field : errorFields) {
      if (isFinal(field) && !field.hasModifierProperty(PsiModifier.FINAL)) holder.registerProblem(field, String.format(MESSAGE1, field.getName()), ProblemHighlightType.GENERIC_ERROR,
          new BaseLocalFix(MESSAGE1_FIX) {
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

              @Override protected boolean isGlobalUndoAction() { return true; }
            }.execute();
          }
        }
      }, new BaseLocalFix(MESSAGE2_FIX) {
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

              @Override protected boolean isGlobalUndoAction() {
                return true;
              }
            }.execute();
          }
        }
      }, new BaseLocalFix(String.format(MESSAGE3_FIX, field.getName())) {
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
      });
    }
  }

  @NotNull
  private static Set<PsiField> getAlreadyInitedFieldsFindErrorRefs(@NotNull PsiClass psiClass, @NotNull Set<FieldRef> errorFieldRef) {
    PsiField[] fields = psiClass.getFields();

  // collect inited fields
    Set<PsiField> alreadyInitedFields = new HashSet<PsiField>();
    for (PsiField field : fields) {
      if (field.getInitializer() != null || field.hasModifierProperty(PsiModifier.FINAL)) alreadyInitedFields.add(field);       // field was initialized (hasInitializer() sometime return false when must - true)  --SS
    }

  // collect inited fields from class initializers and find some error references into them
    for (PsiClassInitializer classInitializer : psiClass.getInitializers()) {
      for (FieldRef field : getFields(classInitializer, new ArrayList<FieldRef>())) {
        if (!field.getPsiField().hasModifierProperty(PsiModifier.FINAL) && PsiFieldUtil.isFinal(field.getPsiField())) {
          PsiClass fieldContainingClass = field.getPsiField().getContainingClass();
          if (fieldContainingClass != null && !fieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT) && psiClass.getQualifiedName() != null
              && !psiClass.getQualifiedName().equals(fieldContainingClass.getQualifiedName())) {
          // check the fields of the parent
            Set<PsiField> parentInitedFields = getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>());
            if (parentInitedFields.contains(field.getPsiField())) {
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
        }
      }
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

        for (FieldRef fieldRef : getFields(constructors.get(i))) {
          if (isFinal(fieldRef.getPsiField()) && !fieldRef.getPsiField().hasModifierProperty(PsiModifier.FINAL)) {
            PsiClass fieldContainingClass = fieldRef.getPsiField().getContainingClass();
            if (fieldContainingClass != null && !fieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT) && psiClass.getQualifiedName() != null
                && !psiClass.getQualifiedName().equals(fieldContainingClass.getQualifiedName())) {
            // check the fields of the parent
              Set<PsiField> parentInitedFields = getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>());
              if (parentInitedFields.contains(fieldRef.getPsiField())) {
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
          }
        }

      // remove constructor from list and offset iterator
        initInConstructors.put(constructors.get(i), initedFields);
        constructors.remove(i);
        i--;
      }
    }

  // find final not initialized fields
    List<PsiField> errorFields = new ArrayList<PsiField>();     // collect there error fields
    for (PsiField field : fields) {
      if (!alreadyInitedFields.contains(field)) errorFields.add(field);
    }

  // add inited in constructors fields and remove initialized fields from error list
    constructorInitializedLoop:
    for (int i = 0; i < errorFields.size(); i++) {
      for (PsiMethod constructor : initInConstructors.keySet()) {
        if (!initInConstructors.get(constructor).contains(errorFields.get(i))) continue constructorInitializedLoop;
      }

    // field initialized in all constructors, fix mistake
      alreadyInitedFields.add(errorFields.get(i));
      errorFields.remove(i);
      i--;
    }

  // find error references in methods;
    for (PsiMethod method : methods) {
      for (FieldRef fieldRef : getFields(method)) {
        if (isFinal(fieldRef.getPsiField()) && !fieldRef.getPsiField().hasModifierProperty(PsiModifier.FINAL)) {
          PsiClass fieldContainingClass = fieldRef.getPsiField().getContainingClass();
          if (fieldContainingClass != null && !fieldContainingClass.hasModifierProperty(PsiModifier.ABSTRACT) && psiClass.getQualifiedName() != null
              && !psiClass.getQualifiedName().equals(fieldContainingClass.getQualifiedName())) {
          // check the fields of the parent
            Set<PsiField> parentInitedFields = getAlreadyInitedFieldsFindErrorRefs(fieldContainingClass, new HashSet<FieldRef>());
            if (parentInitedFields.contains(fieldRef.getPsiField())) {
              errorFieldRef.add(fieldRef);
            } else {
              alreadyInitedFields.add(fieldRef.getPsiField());
            }
          } else {
            if (alreadyInitedFields.contains(fieldRef.getPsiField())) errorFieldRef.add(fieldRef);
          }
        }
      }
    }

    return alreadyInitedFields;
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
  private static List<FieldRef> getFields(@NotNull PsiMethod method) {
    return getFields(method, new ArrayList<FieldRef>());
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
}
