package de.plushnikov.intellij.plugin.action.delombok;

import de.plushnikov.intellij.plugin.processor.field.FXPropertyProcessor;


/**
 * DelombokAction for @FXProperty.
 * Replaces annotation with standard accessors for annotated JavaFX property.
 *
 * @author Phantom Parakeet
 */
public class DelombokFXPropertyAction extends BaseDelombokAction {
	public DelombokFXPropertyAction() {
		super(new BaseDelombokHandler(new FXPropertyProcessor()));
	}
}
