package gtnhtweaks.modules.buttons.mods;

/** The newest release of a mod on GitHub with its jar. */
public final class Release {

    public final ModVersion version;
    /** ISO date, 2026-10-19, or an empty string. */
    public final String date;
    public final String fileName;
    public final String downloadUrl;
    public final long size;
    public final String pageUrl;

    Release(ModVersion version, String date, String fileName, String downloadUrl, long size, String pageUrl) {
        this.version = version;
        this.date = date;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.size = size;
        this.pageUrl = pageUrl;
    }
}
