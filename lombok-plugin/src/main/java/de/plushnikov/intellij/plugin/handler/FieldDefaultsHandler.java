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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  final private static class Field {
    private PsiField psiField;
    private PsiReference reference;

    public Field(PsiField field, PsiReference reference) {
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

  public static void handleMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
    if (method.isConstructor()) return;                          // handle only in methods

    for (Field field : getFields(method)) {
      if (!field.getPsiField().hasModifierProperty(PsiModifier.FINAL) && PsiFieldUtil.isFinal(field.getPsiField())) {
        holder.registerProblem((PsiElement)field.getReference(), String.format(MESSAGE2, field.getPsiField().getName()), ProblemHighlightType.GENERIC_ERROR);
      }
    }
  }

  public static void handleClass(@NotNull final PsiClass psiClass, @NotNull ProblemsHolder holder) {
    PsiField[] fields = psiClass.getFields();
    Set<PsiField> alreadyInitedFields = new HashSet<PsiField>();
    for (PsiField field : fields) {
      if (field.getInitializer() != null || field.hasModifierProperty(PsiModifier.FINAL)) alreadyInitedFields.add(field);       // field was initialized (hasInitializer() sometime return false when must - true)
    }

    Set<PsiField> errorField = new HashSet<PsiField>();
    List<PsiMethod> constructors = new ArrayList<PsiMethod>();
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.isConstructor()) constructors.add(method);
    }

    if (constructors.isEmpty()) {
        for (PsiField field : fields) {
          if (!alreadyInitedFields.contains(field) && isFinal(field)) errorField.add(field);
        }
    }
    for (PsiMethod method : constructors) {
        Set<PsiField> initedFields = new HashSet<PsiField>();
        for (Field field : getFields(method)) initedFields.add(field.getPsiField());
        for (PsiField field : fields) {
          if (!initedFields.contains(field) && !alreadyInitedFields.contains(field) && isFinal(field)) errorField.add(field);
        }
    }

    for (final PsiField field : errorField) {
      holder.registerProblem(field, String.format(MESSAGE1, field.getName()), ProblemHighlightType.GENERIC_ERROR,
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
  private static String createInitializer(@NotNull PsiField field) {
    PsiType type = field.getType();
    String initializer;
    if (TypeConversionUtil.isBooleanType(type)) {
      initializer = "false";
    } else if (TypeConversionUtil.isPrimitive(type.getCanonicalText())) {
      initializer = "0";
    } else {
      initializer = "new " + type.getCanonicalText() + "()";
    }

    return initializer;
  }

  @NotNull
  private static Set<Field> getFields(@NotNull PsiMethod constructor) {
    Set<Field> innerInitFields = new HashSet<Field>();
    PsiElement codeBlock = null;
    for (PsiElement psiElement : constructor.getChildren()) {
      if (psiElement instanceof PsiCodeBlock) {
        codeBlock = psiElement;
      }
    }
    if (codeBlock == null) return innerInitFields;
    return filterFields(codeBlock, innerInitFields);
  }

  @NotNull
  private static Set<Field> filterFields(@NotNull PsiElement parent, @NotNull Set<Field> innerInitFields) {
    for (PsiElement child : parent.getChildren()) {
      if (child.getReference() == null) {
        filterFields(child, innerInitFields);
      } else {
        if (child instanceof PsiReference) {
          PsiElement element = ((PsiReference) child).resolve();
          if (element instanceof PsiField && child.getParent() instanceof PsiAssignmentExpression) {
            innerInitFields.add(new Field((PsiField) element, (PsiReference) child));
          }
        }
      }
    }
    return innerInitFields;
  }
}
