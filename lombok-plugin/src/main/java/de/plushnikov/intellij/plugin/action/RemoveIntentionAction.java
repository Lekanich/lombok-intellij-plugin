package de.plushnikov.intellij.plugin.action;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;


public class RemoveIntentionAction extends BaseIntentionAction {
	private PsiElement element;

	public RemoveIntentionAction(PsiElement element) {
		this.element = element;
	}

	@NotNull @Override public String getText() { return getFamilyName(); }

	@NotNull @Override public String getFamilyName() {
		return String.format("Remove '%s'", element.getText());
	}

	@Override public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
		return element != null;
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
		this.element.delete();
	}
}
