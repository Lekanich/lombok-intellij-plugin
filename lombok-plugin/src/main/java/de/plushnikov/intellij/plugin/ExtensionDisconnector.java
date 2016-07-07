package de.plushnikov.intellij.plugin;

import com.intellij.codeInsight.completion.CompletionContributorEP;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.openapi.components.ApplicationComponent.Adapter;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;


/**
 * @author Suburban Squirrel
 * @version 1.1.7
 * @since 1.1.7
 */
public class ExtensionDisconnector extends Adapter {
  public static final String POINT_NAME = "com.intellij.completion.contributor";

  @Override
  public void initComponent() {
    ExtensionPoint<Object> extensionPoint = Extensions.getRootArea().getExtensionPoint(POINT_NAME);
    CompletionContributorEP[] extensions = (CompletionContributorEP[]) extensionPoint.getExtensions();
    for (int i = 0; i < extensions.length; i++) {
      if (extensions[i].implementationClass.equals(JavaCompletionContributor.class.getCanonicalName())) {
        extensionPoint.unregisterExtension(extensions[i]);
      }
    }
  }
}
