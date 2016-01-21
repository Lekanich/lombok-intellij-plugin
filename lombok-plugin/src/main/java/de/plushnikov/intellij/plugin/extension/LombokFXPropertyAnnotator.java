package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Getter;
import lombok.experimental.FXProperty;
import org.jetbrains.annotations.NotNull;


public class LombokFXPropertyAnnotator implements Annotator {
	public static final String MESSAGE = "'%s' is not available with @FXProperty";

	final private static class RemoveIntentionAction extends BaseIntentionAction {
		private PsiAnnotation annotation;

		public RemoveIntentionAction(PsiAnnotation annotation) {
			this.annotation = annotation;
		}

		@NotNull @Override public String getText() {
			return getFamilyName();
		}

		@NotNull @Override public String getFamilyName() {
			return String.format("Remove '%s' annotation", annotation.getText());
		}

		@Override
		public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
			return annotation != null;
		}

		@Override
		public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
			annotation.delete();
		}
	}

	@Override
	public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
		if (!(element instanceof PsiField)) return;
		PsiAnnotation fxPropertyAnnotation = PsiAnnotationUtil.findAnnotation((PsiField) element, FXProperty.class);
		PsiAnnotation getterAnnotation = PsiAnnotationUtil.findAnnotation((PsiField) element, Getter.class);
		if (fxPropertyAnnotation != null && getterAnnotation != null) {
			holder.createErrorAnnotation(getterAnnotation, String.format(MESSAGE, getterAnnotation.getText())).registerFix(new RemoveIntentionAction(getterAnnotation));
		}
	}
}
