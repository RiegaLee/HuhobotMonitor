package cn.huohuas001.bot;

/** Test fixture for HuHoBot's private Kotlin lateinit starter field. */
public final class QClient {
    private static Object starter;

    private QClient() {
    }

    public static void setStarter(Object value) {
        starter = value;
    }
}
