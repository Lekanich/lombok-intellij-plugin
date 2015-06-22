package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UseScopeEnlarger;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static de.plushnikov.intellij.plugin.util.LombokProcessorUtil.convertAccessLevelToJavaModifier;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.findAnnotation;
import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.getStringAnnotationValue;

/**
 * For additional use scope for {@code lombok.experimental.FieldDefaults}
 * @author Suburban Squirrel
 * @version 1.0.23
 * @since 1.0.23
 */
final public class LombokUseScopeEnlarger extends UseScopeEnlarger {
  @Nullable
  @Override
  public SearchScope getAdditionalUseScope(@NotNull PsiElement element) {
    if (!(element instanceof PsiField)) return null;

    PsiField field = (PsiField) element;
    if (field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.PROTECTED) || field.hasModifierProperty(PsiModifier.PUBLIC)) return null;

    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return null;

    PsiAnnotation annotation = findAnnotation(containingClass, FieldDefaults.class);
    if (annotation == null) return null;

  // create fiction field for used method
    LombokLightFieldBuilder fieldWithLombokAccess = new LombokLightFieldBuilder(element.getManager(), ((PsiField) element).getName(), ((PsiField) element).getType()){
      @Override
      public PsiElement getParent() {
        return element.getParent();
      }
      @Override
      public PsiFile getContainingFile() {
        return element.getContainingFile();
      }
    };
    String level = getStringAnnotationValue(annotation, "level");
    String accessLevelToJavaString = convertAccessLevelToJavaModifier(level);
    if (accessLevelToJavaString != null) fieldWithLombokAccess.withModifier(accessLevelToJavaString);
    fieldWithLombokAccess.setContainingClass((field.getContainingClass()));
    if (field.hasModifierProperty(PsiModifier.STATIC)) fieldWithLombokAccess.withModifier(PsiModifier.STATIC);            // todo I think this doesn't need

    return PsiImplUtil.getMemberUseScope(fieldWithLombokAccess);
  }
}
