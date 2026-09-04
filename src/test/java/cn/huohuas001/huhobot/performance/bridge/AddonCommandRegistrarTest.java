package cn.huohuas001.huhobot.performance.bridge;

import cn.huohuas001.bot.addon.Addon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonCommandRegistrarTest {
    @Test
    void usesAgentAddonMetadataSignatureWhenAvailable() throws Exception {
        AgentQClient qClient = new AgentQClient();
        Object command = new Object();

        boolean registered = AddonCommandRegistrar.tryRegister(
            getClass().getClassLoader(), qClient, command,
            "HuHoBotPerformance", "0.1.0", "性能监控图片", "RiegaLee, Shabby-666"
        );

        assertTrue(registered);
        assertEquals("HuHoBotPerformance", qClient.addon.getName());
        assertEquals("0.1.0", qClient.addon.getVersion());
        assertSame(command, qClient.command);
    }

    @Test
    void leavesMainBranchForLegacyRegisterCommandFallback() throws Exception {
        assertFalse(AddonCommandRegistrar.tryRegister(
            getClass().getClassLoader(), new MainBranchQClient(), new Object(),
            "HuHoBotPerformance", "0.1.0", "性能监控图片", "RiegaLee"
        ));
    }

    private static final class AgentQClient {
        private Addon addon;
        private Object command;

        public void registerCommand(Addon addon, Object command) {
            this.addon = addon;
            this.command = command;
        }
    }

    private static final class MainBranchQClient {
        public void registerCommand(Object command) {
        }
    }
}
