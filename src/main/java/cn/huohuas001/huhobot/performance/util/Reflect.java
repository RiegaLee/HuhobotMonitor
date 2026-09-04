package cn.huohuas001.huhobot.performance.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Small reflection boundary for HuHoBot, Paper and NMS version differences. */
public final class Reflect {
    private Reflect() {
    }

    public static Object read(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) return null;
        Class<?> type = target.getClass();
        for (String name : names) {
            Method method = findCompatibleMethod(type, name);
            if (method != null) return invoke(method, target);
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            method = findCompatibleMethod(type, getter);
            if (method != null) return invoke(method, target);
            Field field = findField(type, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        }
        throw new NoSuchFieldException("No readable property on " + type.getName());
    }

    public static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(target.getClass(), name, args);
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        return invoke(method, target, args);
    }

    public static Method findCompatibleMethod(Class<?> type, String name, Object... args) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && compatible(method.getParameterTypes(), args)) {
                    trySetAccessible(method);
                    return method;
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && compatible(method.getParameterTypes(), args)) {
                trySetAccessible(method);
                return method;
            }
        }
        return null;
    }

    public static Field findField(Class<?> type, String name) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep walking.
            }
        }
        return null;
    }

    public static Object readStaticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = findField(type, name);
        if (field == null) throw new NoSuchFieldException(type.getName() + "." + name);
        field.setAccessible(true);
        Object receiver = Modifier.isStatic(field.getModifiers()) ? null : kotlinObject(type);
        return field.get(receiver);
    }

    public static Object kotlinObject(Class<?> type) throws ReflectiveOperationException {
        Field instance = type.getField("INSTANCE");
        return instance.get(null);
    }

    private static boolean compatible(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) return false;
        for (int i = 0; i < parameters.length; i++) {
            if (args[i] == null) {
                if (parameters[i].isPrimitive()) return false;
                continue;
            }
            if (!wrap(parameters[i]).isAssignableFrom(args[i].getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        trySetAccessible(method);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ReflectiveOperationException) throw (ReflectiveOperationException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause == null ? error : cause);
        }
    }

    private static void trySetAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
            // Java 9+ modules may reject deep access; public invocation can still work.
        }
    }
}
