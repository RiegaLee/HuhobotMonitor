package org.huhobot.monitor.bridge;

import cn.huohuas001.bot.events.commands.BaseCommand;
import cn.huohuas001.bot.events.commands.Commands;

import java.util.function.Consumer;

/** Native HuHoBot command used by both the main branch and the AGENT AddonAPI. */
public final class BuiltInPerformanceCommand extends BaseCommand {
    private final Consumer<Object> handler;

    public BuiltInPerformanceCommand(Consumer<Object> handler) {
        this.handler = handler;
    }

    @Commands(command = BotCommandContext.DEFAULT_COMMAND, describe = "查看当前服务器运行状态")
    public void performance(Object groupMessageEvent) {
        handler.accept(groupMessageEvent);
    }
}
