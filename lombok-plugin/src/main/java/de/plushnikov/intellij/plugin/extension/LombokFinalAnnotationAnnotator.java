package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiTreeUtilEx;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import lombok.experimental.FinalArgs;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static de.plushnikov.intellij.plugin.util.LombokProcessorUtil.convertAccessLevelToJavaModifier;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.getStringAnnotationValue;


/**
 * @author Suburban Squirrel
 * @version 1.1.1
 * @since 1.1.1
 */
final public class LombokFinalAnnotationAnnotator implements Annotator {
	private static final QuickFixFactory FACTORY = QuickFixFactory.getInstance();
	private static final String FINAL_MESSAGE = "Remove all final modifiers";
	private static final String FINALARGS_MESSAGE = "Remove final modifiers in args";
	private static final String MODIFIER_MESSAGE = "Remove current modifier from fields.";

	@FieldDefaults(makeFinal = true)
	@RequiredArgsConstructor
	final private static class MultiModifierFix extends BaseIntentionAction {
		private String message;
		private List<PsiVariable> variables;
		private String modifier;

		@Nls @NotNull @Override public String getFamilyName() { return message; }

		@NotNull @Override public String getText() { return getFamilyName(); }

		@Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) { return true; }

		@Override
		public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
			for (PsiVariable psiVariable : variables) {
				FACTORY.createModifierListFix(psiVariable, modifier, false, false).invoke(project, editor, file);
			}
		}
	}

	@Override
	public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
		if (!(element instanceof PsiAnnotation)) return;

		PsiAnnotation annotation = (PsiAnnotation) element;
		PsiModifierList modifierList = PsiTreeUtil.getParentOfType(annotation, PsiModifierList.class);
		if (modifierList == null) return;

		PsiElement parent = modifierList.getParent();

		if (isAnnotation(Final.class, annotation)) {
			if (!(parent instanceof PsiMethod)) return;

			List<PsiVariable> psiVariables = getVariablesForFix(PsiTreeUtilEx.getDeepChildrenOfType(parent, PsiVariable.class), PsiModifier.FINAL);
			if (!psiVariables.isEmpty()) regMultiFix(holder, annotation, psiVariables, FINAL_MESSAGE, PsiModifier.FINAL);
		} else if (isAnnotation(FinalArgs.class, annotation)) {
			if (!(parent instanceof PsiMethod)) return;

			List<PsiVariable> psiVariables = getVariablesForFix(PsiTreeUtilEx.getDeepChildrenOfType(((PsiMethod) parent).getParameterList(), PsiVariable.class), PsiModifier.FINAL);
			if (!psiVariables.isEmpty()) regMultiFix(holder, annotation, psiVariables, FINALARGS_MESSAGE, PsiModifier.FINAL);
		} else if (isAnnotation(FieldDefaults.class, annotation)) {
			if (!(parent instanceof PsiClass) || !PsiAnnotationUtil.getBooleanAnnotationValue(annotation, "makeFinal", false)) return;

		// - check final
			PsiField[] fields = ((PsiClass) parent).getFields();
			List<PsiVariable> psiVariables = getVariablesForFix(Arrays.asList(fields), PsiModifier.FINAL);
			if (!psiVariables.isEmpty()) regMultiFix(holder, annotation, psiVariables, FINAL_MESSAGE, PsiModifier.FINAL);

		// - check access modifier
			String level = getStringAnnotationValue(annotation, "level");
			if (level == null || "NONE".equals(level)) return;

			String accessLevelToJavaString = convertAccessLevelToJavaModifier(level);

			psiVariables = getVariablesForFix(Arrays.asList(fields), accessLevelToJavaString);
			if (!psiVariables.isEmpty()) regMultiFix(holder, annotation, psiVariables, MODIFIER_MESSAGE, accessLevelToJavaString);
		}
	}

	private List<PsiVariable> getVariablesForFix(@NotNull List<PsiVariable> variables, @NotNull String modifier) {
		return variables.stream().filter(variable -> {
			PsiModifierList list = variable.getModifierList();
			return list != null && list.hasExplicitModifier(modifier);
		}).collect(Collectors.toList());
	}

	private void regMultiFix(@NotNull AnnotationHolder holder, @NotNull PsiAnnotation annotation, @NotNull List<PsiVariable> psiVariables, @NotNull String message, @NotNull String modifier) {
		holder.createWarningAnnotation(annotation, message).registerFix(new MultiModifierFix(message, psiVariables, modifier));
	}

	private boolean isAnnotation(@NotNull Class<? extends Annotation> aClass, @NotNull PsiAnnotation annotation) {
		return aClass.getCanonicalName().equals(annotation.getQualifiedName());
	}
}
