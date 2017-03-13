package de.plushnikov.intellij.plugin.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import com.intellij.openapi.diagnostic.Logger;


/**
 * @author Plushnikov Michail
 */
public class ReflectionUtil {
	private static final Logger LOG = Logger.getInstance(ReflectionUtil.class.getName());

	public static <T, R> void setFinalFieldPerReflection(Class<T> clazz, T instance, Class<R> fieldClass, R newValue, boolean includeStatic) {
		try {
			for (Field field : clazz.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) && !includeStatic) continue;

				if (field.getType().equals(fieldClass)) {
					field.setAccessible(true);
					field.set(instance, newValue);
					break;
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException x) {
			LOG.error(x);
		}
	}

	public static <T, R> void setFinalFieldPerReflection(Class<T> clazz, T instance, Class<R> fieldClass, R newValue) {
		setFinalFieldPerReflection(clazz, instance, fieldClass, newValue, true);
	}

	public static <T, R> R getFieldPerReflection(Class<T> clazz, T instance, Class<R> fieldClass, String fieldName) {
		R result = null;
		try {
			final Field declaredField = clazz.getDeclaredField(fieldName);
			declaredField.setAccessible(true);
			result = (R) declaredField.get(instance);
		} catch (IllegalArgumentException x) {
			LOG.error(x);
		} catch (IllegalAccessException x) {
			LOG.error(x);
		} catch (NoSuchFieldException e) {
			LOG.error(e);
		}
		return result;
	}
}
