package cn.huohuas001.bot.events.commands;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-only ABI stub for HuHoBot's runtime command annotation. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Commands {
    String command();
    String describe();
    boolean onlyAdmin() default false;
}
