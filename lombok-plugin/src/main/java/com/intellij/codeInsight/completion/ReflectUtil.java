package com.intellij.codeInsight.completion;

import java.lang.reflect.Method;
import com.intellij.openapi.progress.ProcessCanceledException;


/**
 * @author Suburban Squirrel
 * @version 1.1.7
 * @since 1.1.7
 */
final public class ReflectUtil {

  public static Method getMethod(Class<?> parentClass, Class<?> returnType, String methodName, Class<?>... types) {
    for (Method method : parentClass.getDeclaredMethods()) {
      if (equalsTo(method, returnType, methodName, types)) return method;
    }

    return null;
  }

  private static boolean equalsTo(Method otherMethod, Class<?> returnType, String methodName, Class<?>... methodTypes) {
    if (methodName != null && !methodName.equals(otherMethod.getName())) return false;

    if (otherMethod.getParameterCount() != methodTypes.length) return false;			// not equal methods

    if (!otherMethod.getReturnType().equals(returnType)) return false;

    for (int i = 0; i < otherMethod.getParameterTypes().length; i++) {
      if (otherMethod.getParameterTypes()[i] != methodTypes[i]) return false;
    }

    return true;
  }

  public static Object invokeMethod(Method method, Object... params) {
    try {
      method.setAccessible(true);
      return method.invoke(null, params);
    } catch (Exception e) {
      if (e.getCause() != null && e.getCause().getClass() == ProcessCanceledException.class) throw (ProcessCanceledException) e.getCause();
    }

    return null;
  }
}
