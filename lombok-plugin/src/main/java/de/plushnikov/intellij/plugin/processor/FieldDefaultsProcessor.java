package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.experimental.PackagePrivate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Logger;

/**
 * Inspect and validate @FieldDefaults lombok annotation on a field.
 *
 * @author William Delanoue
 * @author Suburban Squirrel
 */
public class FieldDefaultsProcessor extends AbstractClassProcessor {
  private static final Logger LOG = Logger.getLogger(FieldDefaultsProcessor.class.getSimpleName());

  public FieldDefaultsProcessor() {
    super(FieldDefaults.class, PsiField.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull de.plushnikov.intellij.plugin.problem.ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRigthType(psiClass, builder);

    if (result) {
      final AccessLevel level = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "level", AccessLevel.class);
      final boolean makeFinal = Boolean.TRUE.equals(PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "makeFinal", Boolean.class));
      if (level == AccessLevel.NONE && !makeFinal) {
        builder.addError("This does nothing; provide either level or makeFinal or both.");
        return false;
      }

      if (level == AccessLevel.PACKAGE) {
        builder.addWarning("Setting 'level' to PACKAGE does nothing. To force fields as package private, use the @PackagePrivate annotation on the field.");
      }

      if (!makeFinal && PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "makeFinal", Boolean.class) != null) {
        builder.addWarning("Setting 'makeFinal' to false does nothing. To force fields to be non-final, use the @NonFinal annotation on the field.");
      }
    }
    return result;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = getModifier(psiAnnotation);
    final Boolean makeFinal = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "makeFinal", Boolean.class);

    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      if (!psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER)) {
        recreateField(psiField, methodVisibility, Boolean.TRUE.equals(makeFinal));
      }
    }
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("'@Getter' is only supported on a class, enum or field type");
      result = false;
    }
    return result;
  }

  @NotNull
  public void recreateField(@NotNull final PsiField psiField, final String modifier, final boolean mustBeFinal) {
    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    final boolean mustBePrivate = PsiAnnotationUtil.isAnnotatedWith(psiField, PackagePrivate.class);

    ApplicationManager.getApplication().invokeLater(
        new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                CommandProcessor.getInstance().executeCommand(psiField.getProject(), new Runnable() {
                  @Override
                  public void run() {
                    PsiDocumentManager manager = PsiDocumentManager.getInstance(psiField.getProject());
                    Document document = manager.getDocument(psiField.getContainingFile());
                    if (document == null) {
                      return;
                    }
                    manager.commitDocument(document);

                    setModifier(PsiModifier.FINAL, mustBeFinal && !PsiAnnotationUtil.isAnnotatedWith(psiField, NonFinal.class));
                    if (modifier != null) {
                      setModifier(mustBePrivate ? PsiModifier.PRIVATE : modifier, true);
                    }
                  }

                  private void setModifier(@NotNull String modifier, boolean value) {
                    psiField.getModifierList().setModifierProperty(modifier, value);
                  }
                }, "lombok FieldsDefaults", ActionGroup.EMPTY_GROUP);
              }
            });
          }
        }
    );
  }

  @Nullable
  private static String getModifier(@NotNull PsiAnnotation psiAnnotation) {
    String value = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "level", String.class);
    if (null == value || value.isEmpty() || value.equals("NONE")) return null;
    if (value.equals("PROTECTED")) {
      return PsiModifier.PROTECTED;
    } else if (value.equals("PRIVATE")) {
      return PsiModifier.PRIVATE;
    } else if (value.equals("PUBLIC")) {
      return PsiModifier.PUBLIC;
    }
    return PsiModifier.PACKAGE_LOCAL;
  }
}