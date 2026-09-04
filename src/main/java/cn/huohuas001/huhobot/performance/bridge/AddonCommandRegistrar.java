package cn.huohuas001.huhobot.performance.bridge;

import cn.huohuas001.huhobot.performance.util.Reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Reflective adapter for the PenguinAgent AddonAPI. */
final class AddonCommandRegistrar {
    private static final String ADDON_CLASS = "cn.huohuas001.bot.addon.Addon";

    private AddonCommandRegistrar() {
    }

    static boolean tryRegister(
        ClassLoader loader,
        Object qClient,
        Object command,
        String name,
        String version,
        String description,
        String author
    ) throws ReflectiveOperationException {
        Class<?> addonClass;
        try {
            addonClass = Class.forName(ADDON_CLASS, false, loader);
        } catch (ClassNotFoundException ignored) {
            return false;
        }

        Constructor<?> constructor;
        try {
            constructor = addonClass.getConstructor(String.class, String.class, String.class, String.class);
        } catch (NoSuchMethodException ignored) {
            return false;
        }

        Object addon = constructor.newInstance(name, version, description, author);
        Method register = Reflect.findCompatibleMethod(qClient.getClass(), "registerCommand", addon, command);
        if (register == null) return false;
        Reflect.invoke(qClient, "registerCommand", addon, command);
        return true;
    }
}
