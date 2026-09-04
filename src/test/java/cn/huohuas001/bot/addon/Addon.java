package cn.huohuas001.bot.addon;

/** Test fixture matching PenguinAgent's Kotlin Addon constructor. */
public final class Addon {
    private final String name;
    private final String version;
    private final String description;
    private final String author;

    public Addon(String name, String version, String description, String author) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }
}
