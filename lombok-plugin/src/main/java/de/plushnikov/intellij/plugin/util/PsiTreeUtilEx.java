package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Suburban Squirrel
 * @version 0.4.0
 * @since 0.4.0
 */
final public class PsiTreeUtilEx {
	@NotNull
	public static <T extends PsiElement> List<T> getDeepChildrenOfType(@NotNull PsiElement psiElement, @NotNull Class<T> type) {
		return getDeepChildrenOfType(psiElement, type, new ArrayList<>());
	}

	@NotNull
	private static <T extends PsiElement> List<T> getDeepChildrenOfType(@NotNull PsiElement psiElement, @NotNull Class<T> type, @NotNull List<T> list) {
		PsiElement child = psiElement.getFirstChild();
		while (child != null) {
			if (type.isInstance(child)) {
				list.add((T) child);
			}

			getDeepChildrenOfType(child, type, list);
			child = child.getNextSibling();
		}

		return list;
	}
}
