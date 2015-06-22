package de.plushnikov.intellij.plugin.processor.handler.singular;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.List;

public class SingularMapHandler extends AbstractSingularHandler {

  private static final String KEY = "Key";
  private static final String VALUE = "Value";
  private static final String LOMBOK_KEY = "$key";
  private static final String LOMBOK_VALUE = "$value";

  public void addBuilderField(@NotNull List<PsiField> fields, @NotNull PsiVariable psiVariable, @NotNull PsiClass innerClass, @NotNull AccessorsInfo accessorsInfo) {
    final String fieldName = accessorsInfo.removePrefix(psiVariable.getName());

    final PsiManager psiManager = psiVariable.getManager();
    final PsiType[] psiTypes = PsiTypeUtil.extractTypeParameters(psiVariable.getType(), psiManager);
    if (psiTypes.length == 2) {
      final Project project = psiVariable.getProject();

      final PsiType builderFieldKeyType = getBuilderFieldType(psiTypes[0], project);
      fields.add(new LombokLightFieldBuilder(psiManager, fieldName + LOMBOK_KEY, builderFieldKeyType)
          .withModifier(PsiModifier.PRIVATE)
          .withNavigationElement(psiVariable)
          .withContainingClass(innerClass));

      final PsiType builderFieldValueType = getBuilderFieldType(psiTypes[1], project);
      fields.add(new LombokLightFieldBuilder(psiManager, fieldName + LOMBOK_VALUE, builderFieldValueType)
          .withModifier(PsiModifier.PRIVATE)
          .withNavigationElement(psiVariable)
          .withContainingClass(innerClass));
    }
  }

  @NotNull
  protected PsiType getBuilderFieldType(@NotNull PsiType psiType, @NotNull Project project) {
    return PsiTypeUtil.getGenericCollectionClassType((PsiClassType) psiType, project, CommonClassNames.JAVA_UTIL_ARRAY_LIST);
  }

  protected void addOneMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final PsiType[] psiTypes = PsiTypeUtil.extractTypeParameters(psiFieldType, methodBuilder.getManager());
    if (psiTypes.length == 2) {
      methodBuilder.withParameter(singularName + KEY, psiTypes[0]);
      methodBuilder.withParameter(singularName + VALUE, psiTypes[1]);
    }
  }

  protected void addAllMethodParameter(@NotNull String singularName, @NotNull PsiType psiFieldType, @NotNull LombokLightMethodBuilder methodBuilder) {
    final Project project = methodBuilder.getProject();

    final PsiType collectionType = PsiTypeUtil.getCollectionClassType((PsiClassType) psiFieldType, project, CommonClassNames.JAVA_UTIL_MAP);

    methodBuilder.withParameter(singularName, collectionType);
  }

  protected String getOneMethodBody(@NotNull String singularName, @NotNull String psiFieldName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
        "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList(); \n" +
        "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList(); \n" +
        "'}' \n" +
        "this.{0}" + LOMBOK_KEY + ".add({1}" + KEY + ");\n" +
        "this.{0}" + LOMBOK_VALUE + ".add({1}" + VALUE + ");" +
        "{2}";

    return MessageFormat.format(codeBlockTemplate, psiFieldName, singularName, fluentBuilder ? "\nreturn this;" : "");
  }

  protected String getAllMethodBody(@NotNull String singularName, boolean fluentBuilder) {
    final String codeBlockTemplate = "if (this.{0}" + LOMBOK_KEY + " == null) '{' \n" +
        "this.{0}" + LOMBOK_KEY + " = new java.util.ArrayList(); \n" +
        "this.{0}" + LOMBOK_VALUE + " = new java.util.ArrayList(); \n" +
        "'}' \n" +
        "for (java.util.Map.Entry<?, ?> $lombokEntry : {0}.entrySet()) '{'\n" +
        "this.{0}" + LOMBOK_KEY + ".add($lombokEntry.getKey());\n" +
        "this.{0}" + LOMBOK_VALUE + ".add($lombokEntry.getValue());\n" +
        "'}'{1}";
    return MessageFormat.format(codeBlockTemplate, singularName, fluentBuilder ? "\nreturn this;" : "");
  }
}
