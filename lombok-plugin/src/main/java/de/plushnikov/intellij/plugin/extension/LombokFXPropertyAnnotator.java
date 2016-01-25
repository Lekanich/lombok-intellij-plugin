package de.plushnikov.intellij.plugin.extension;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.action.RemoveIntentionAction;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Getter;
import lombok.experimental.FXProperty;
import org.jetbrains.annotations.NotNull;


public class LombokFXPropertyAnnotator implements Annotator {
	public static final String MESSAGE = "'%s' is not available with @FXProperty";

	@Override
	public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
		if (!(element instanceof PsiField)) return;

		PsiAnnotation fxPropertyAnnotation = PsiAnnotationUtil.findAnnotation((PsiField) element, FXProperty.class);
		PsiAnnotation getterAnnotation = PsiAnnotationUtil.findAnnotation((PsiField) element, Getter.class);
		if (fxPropertyAnnotation == null || getterAnnotation == null) return;

		holder.createErrorAnnotation(getterAnnotation, String.format(MESSAGE, getterAnnotation.getText())).registerFix(new RemoveIntentionAction(getterAnnotation));
	}
}
