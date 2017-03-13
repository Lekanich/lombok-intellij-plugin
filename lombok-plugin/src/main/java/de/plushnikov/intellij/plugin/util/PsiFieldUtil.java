package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.Final;
import lombok.experimental.FinalArgs;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.isAnnotatedWith;


/**
 * @author Plushnikov Michail
 */
public class PsiFieldUtil {

  public static boolean isFinal(@NotNull PsiVariable variable) {
    return variable.hasModifierProperty(PsiModifier.FINAL) || isFinalByAnnotation(variable);
  }

	public static boolean isFinalByAnnotation(@NotNull PsiVariable variable) {
		if (isAnnotatedWith(variable, NonFinal.class)) {
			return false;
		}

	// check Final
		PsiElement parent = variable.getParent();
		PsiMethod method = PsiTreeUtil.getParentOfType(variable, PsiMethod.class);
		if (method != null && isAnnotatedWith(method, Final.class)
			&& (parent instanceof PsiParameterList || parent instanceof PsiDeclarationStatement || parent instanceof PsiCatchSection || parent instanceof PsiForeachStatement)
			&& !(parent.getParent() instanceof PsiLambdaExpression) && !(parent.getParent() instanceof PsiForStatement)) {
			return true;                                                             // for Final
		}
		if (method != null && isAnnotatedWith(method, FinalArgs.class) && parent instanceof PsiParameterList && !(parent.getParent() instanceof PsiLambdaExpression)) {
			return true;                // for FinalArgs
		}

		if (!(variable instanceof PsiField)) return false;

		PsiClass containingClass = ((PsiField) variable).getContainingClass();
		if (containingClass == null) return false;

		if (findAnnotation(containingClass, Value.class) != null) return true;

		PsiAnnotation annotation = findAnnotation(containingClass, FieldDefaults.class);
		if (annotation == null) return false;

	// if couldn't find annotation value get really final modifier of field
		return PsiAnnotationUtil.getBooleanAnnotationValue(annotation, "makeFinal", variable.hasModifierProperty(PsiModifier.FINAL));
	}
}
