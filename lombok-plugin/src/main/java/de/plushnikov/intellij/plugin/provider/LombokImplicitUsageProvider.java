package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.processor.LombokPsiElementUsage;

import java.util.Collection;

/**
 * Provides implicit usages of lombok fields
 */
public class LombokImplicitUsageProvider implements ImplicitUsageProvider {

  private final LombokProcessorProvider processorProvider;

  public LombokImplicitUsageProvider() {
    processorProvider = LombokProcessorProvider.getInstance();
  }

  @Override
  public boolean isImplicitUsage(PsiElement element) {
    return isImplicitWrite(element) || isImplicitRead(element);
  }

  @Override
  public boolean isImplicitRead(PsiElement element) {
    return checkUsage(element, LombokPsiElementUsage.READ);
  }

  @Override
  public boolean isImplicitWrite(PsiElement element) {
    return checkUsage(element, LombokPsiElementUsage.WRITE);
  }

  private boolean checkUsage(PsiElement element, LombokPsiElementUsage elementUsage) {
    boolean result = false;
    if (element instanceof PsiField) {
      final Collection<LombokProcessorData> applicableProcessors = processorProvider.getApplicableProcessors((PsiField) element);

      for (LombokProcessorData processorData : applicableProcessors) {
        final LombokPsiElementUsage psiElementUsage = processorData.getProcessor().checkFieldUsage((PsiField) element, processorData.getPsiAnnotation());
        if (elementUsage == psiElementUsage || LombokPsiElementUsage.READ_WRITE == psiElementUsage) {
          result = true;
          break;
        }
      }

    }
//    else if (element instanceof PsiMethod) {
//  TODO    Not implemented at the moment
//      final Collection<LombokProcessorData> applicableProcessors = processorProvider.getApplicableProcessors((PsiMethod) element);
//
//      for (LombokProcessorData processorData : applicableProcessors) {
//        final LombokPsiElementUsage psiElementUsage = processorData.getProcessor().checkMethodUsage((PsiMethod) element, processorData.getPsiAnnotation());
//        if (elementUsage == psiElementUsage || LombokPsiElementUsage.READ_WRITE == psiElementUsage) {
//          result = true;
//          break;
//        }
//      }
//    }
    return result;
  }

}
