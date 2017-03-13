package de.plushnikov.intellij.plugin.extension;

import java.util.Set;
import com.google.common.collect.Sets;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.PsiConstantEvaluationHelperImpl;
import com.intellij.psi.impl.PsiExpressionEvaluator;
import de.plushnikov.intellij.plugin.util.PsiFieldUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author Suburban Squirrel
 * @version 1.1.4
 * @since 1.1.4
 */
final public class LombokConstantEvaluator extends PsiExpressionEvaluator {

	@Override
	public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
		if (expression instanceof PsiReferenceExpression) {
			PsiElement resolve = ((PsiReferenceExpression) expression).resolve();
			if (resolve instanceof PsiField && isConstant((PsiField) resolve)) {
				Set<PsiVariable> psiElements = Sets.newHashSet((PsiField) resolve);
				return PsiConstantEvaluationHelperImpl.computeCastTo(((PsiField) resolve).getInitializer(), ((PsiField) resolve).getType(), psiElements);
			}
		}

		return super.computeConstantExpression(expression, throwExceptionOnOverflow);
	}

	@Override
	public Object computeExpression(PsiElement expression, boolean throwExceptionOnOverflow, @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
		return super.computeExpression(expression, throwExceptionOnOverflow, auxEvaluator);
	}

	private static boolean isConstant(@NotNull PsiField field) {
		final PsiClass containingClass = field.getContainingClass();
		if (containingClass == null) {
			return false;
		}

		final PsiType type = field.getType();
	// javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
		if (!(type instanceof PsiPrimitiveType) && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return false;

		return field.hasModifierProperty(PsiModifier.STATIC) && PsiFieldUtil.isFinalByAnnotation(field);
	}
}
