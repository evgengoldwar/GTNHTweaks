package gtnhtweaks.modules.minecraft.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import gtnhtweaks.modules.minecraft.client.PackCatalog.Kind;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Pack;
import gtnhtweaks.modules.minecraft.client.PackCatalog.PackFile;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Page;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Release;
import gtnhtweaks.modules.minecraft.client.PackCatalog.Source;

/**
 * The resource packs the GTNH wiki lists on its Resource Packs page, and how their files are found: the newest
 * release of their repository made for the GTNH version that is played, as the GTNHLib update notifier does.
 */
final class GtnhPacks {

    private static final String GITHUB_API = "https://api.github.com/repos/";
    private static final String UTILITY = "Accessibility and Utility";
    private static final String DARK = "Dark Mode";
    private static final String RESTYLE = "Upscaling and Restyles";
    /** The GTNH version a release is made for, at the start of its tag: {@code v2.9.X/v5.46}. */
    private static final Pattern LINE_TAG = Pattern.compile("(?i)^v?(\\d+\\.\\d+)\\.x[/_-]+");
    private static final Pattern LINE = Pattern.compile("(\\d+)\\.(\\d+)");

    private static final List<Pack> PACKS = new ArrayList<>();
    private static String playerLine;

    static {
        github(
            UTILITY,
            "BetterClick",
            "Fox",
            "All",
            "DylanTaylor1",
            "GTNH-ResourcePack",
            "Replaces the loud high-pitched clicking sound with a softer low-pitched clicking sound.");
        github(
            UTILITY,
            "Colored GregTech Screwdriver and File",
            "jack_glows, LunarLambda",
            "2.8.X, 2.9.X",
            "LunarLambda",
            "colored_GT_tools",
            "Simple resource pack changing the color of the screwdriver handle to blue and the handle of the file to "
                + "red. You'll never confuse them again.");
        add(
            UTILITY,
            "GTNH Colored Machines",
            "imTheSupremeOne",
            "2.8.X",
            Kind.GITHUB,
            "imTheSupremeOne",
            "GTNH-Textures-Usernm",
            "machines",
            null,
            null,
            "",
            "GT6-like colored textures for each tier for better visibility (only patches select textures - "
                + "everything else is vanilla).");
        add(
            UTILITY,
            "GTNH Machine Textures",
            "xXjudeXx",
            "2.8.X",
            Kind.GITHUB,
            "jude123412",
            "GTNH-Machine-Textures",
            null,
            null,
            "https://www.curseforge.com/minecraft/texture-packs/gtnh-16x-machine-textures",
            "",
            "Overhauls machine textures to reflect their actual material colors, ensuring visual consistency across "
                + "all machines, including those from GT++, GoodGenerator, KekzTech, and Electro-Magic Tools.");
        add(
            UTILITY,
            "Invisible Healing Axe",
            "Ranzu",
            "All",
            Kind.GITHUB,
            "Ranzuu",
            "Shadow-UI",
            "Just-InvisHealingAxe",
            null,
            null,
            "",
            "Makes the Healing Axe nearly invisible to prevent it from blocking the screen when held in off-hand.");
        github(
            UTILITY,
            "GTNH-OutlinedOres",
            "schneid3306, Ranzu",
            "All",
            "Ranzuu",
            "GTNH-OutlinedOres",
            "Adds a solid border that matches the color of the core. Two versions available: one for vanilla GTNH and "
                + "one for Modernity (ABKQPO).");
        github(
            UTILITY,
            "gtnh-quieter-chests",
            "SkilledAlpaca",
            "All",
            "SkilledAlpaca",
            "gtnh-quieter-chests",
            "Lowers the volume of the chest open and close sounds.");

        github(
            DARK,
            "Dark GTNH Reimagined",
            "Kuliiuu",
            "2.8.X",
            "Kuliiuu",
            "Dark-GTNH-Reimagined",
            "Dark-themed UI resource pack focusing on readability, visual consistency, and a clean modern interface. "
                + "Works well with Modernity (ABKQPO).");
        add(
            DARK,
            "Scuffed GTNH Dark",
            "Albi, Kuliiuu",
            "2.8.X",
            Kind.GITHUB,
            "Kuliiuu",
            "Scuffed-GTNH-Dark-Continued",
            null,
            null,
            null,
            "No longer updated: see Dark GTNH Reimagined instead.",
            "Dark Mode for GTNH with custom progress bars. Recommended to use with Unity and Zedarrian Technology.");
        add(
            DARK,
            "Shadow-UI",
            "Ranzu",
            "2.8.X, 2.9.X",
            Kind.GITHUB,
            "Ranzuu",
            "Shadow-UI",
            null,
            // the repository keeps other packs too, under their own tags
            "(?i)v?\\d.*",
            null,
            "",
            "Darker user interfaces. Two versions available: one for vanilla GTNH and one for Modernity (ABKQPO). "
                + "Includes additional tweaks and fixes in separate packs, and supports GTNH addon mods.");
        github(DARK, "Glass-UI", "S4mpsa", "2.8.X, 2.9.X", "S4mpsa", "Glass-UI", "Adds a transparent dark UI.");

        github(
            RESTYLE,
            "AE2 Dark Mode",
            "Ranzu",
            "2.8.X, 2.9.X",
            "Ranzuu",
            "AE2-Dark-Mode",
            "Darker AE2 blocks. Includes textures for AE2 additions and GTNH addon mods.");
        github(
            RESTYLE,
            "Borosillycat Glass Pack",
            "Angry3vilbot",
            "2.8.X, 2.9.X",
            "Angry3vilbot",
            "BorosillycatGlassPack",
            "Replaces the texture of borosilicate glass block variants with a semi-transparent profile picture of "
                + "sillycat4725.");
        add(
            RESTYLE,
            "Dynamic Duo GTNH",
            "Alexdoru",
            "2.8.X",
            Kind.PAGE,
            null,
            null,
            null,
            null,
            "https://discord.com/channels/181078474394566657/224191655375273985/889345648204316673",
            "Shared on the GTNH Discord only: download it from the message there.",
            "Smooth vanilla textures, 32x textures for crafting items, and clean fonts. Composite of GT and Faithful "
                + "textures, among others.");
        add(
            RESTYLE,
            "Slightly Improved Font (32x)",
            "Lat",
            "Untested",
            Kind.GITHUB,
            "latvian-dev",
            "Slightly-Improved-Font",
            null,
            null,
            "https://www.curseforge.com/minecraft/texture-packs/slightly-improved-font",
            "",
            "This resource pack adds a slightly improved version of vanilla Minecraft font (32x version).");
        github(
            RESTYLE,
            "GTNH Faithful Textures",
            "Ethryan",
            "2.8.X, 2.9.X",
            "Ethryan",
            "GTNH-Faithful-Textures",
            "Large kitchen-sink texture pack with everything needed to scale it to x32.");
        github(
            RESTYLE,
            "GTNH FTI Faithful ResourcePack",
            "Eldrinn-Elantey",
            "2.8.X, 2.9.X",
            "Eldrinn-Elantey",
            "GTNH-FTI-Faithful-ResourcePack",
            "Faithful textures with some differences.");
        github(
            RESTYLE,
            "GTNH Textures Usernm",
            "Usernm0",
            "2.8.X",
            "Usernm0",
            "GTNH-Textures-Usernm",
            "Circuits and other components, casings, coils, coins, etc. 32x32 (only patches select textures).");
        github(
            RESTYLE,
            "GTNHRefreshed",
            "BlueHero233",
            "2.8.X",
            "BlueHero233",
            "GTNHRefreshed",
            "Backport of ULSTICK's GregTech Refreshed, with some additions to fit GTNH items. Recommended to use with "
                + "Modernity.");
        add(
            RESTYLE,
            "Ivelitex",
            "Ivelieu",
            "2.8.X",
            Kind.GITLAB,
            "ivelieu",
            "ivelitex",
            null,
            null,
            "https://gitlab.com/ivelieu/ivelitex",
            "",
            "Applied Energistics 2 GUIs, some other GUIs, and minor tool changes. Fractal decorative art backgrounds.");
        github(
            RESTYLE,
            "John Smith: Alk-GTNH",
            "Alkalus",
            "2.7.X",
            "alkcorp",
            "JSTR-Alk-Modded-1.7.10",
            "GTNH fork of John Smith: Technician's Remix.");
        github(
            RESTYLE,
            "Minimalist Technology",
            "Pyrolusite, XxinsanityxX",
            "2.8.X",
            "Fogy-F",
            "Minimalistic-GTNH-repair",
            "GregTech 5 & IC2. 16x clean, smooth textures.");
        github(
            RESTYLE,
            "Modernity (GTNH)",
            "ABKQPO and Modernity team",
            "2.8.X, 2.9.X (WIP)",
            "ModernityGTNH",
            "Modernity-GTNH",
            "Updates all GTNH textures to the style of Minecraft 1.20.1+.");
        add(
            RESTYLE,
            "Modernity",
            "AstroTibs",
            "2.8.X",
            Kind.MODRINTH,
            null,
            "modernity",
            null,
            null,
            "https://modrinth.com/resourcepack/modernity",
            "",
            "Updates most non-GTNH textures to the style of modern Minecraft.");
        add(
            RESTYLE,
            "Stellar Fusion",
            "Magentanide",
            "2.7.X",
            Kind.GITHUB,
            "Slurpassassin",
            "StellarFusion",
            null,
            null,
            null,
            "Needs Classic Faithful 32x (Programmer Art) for 1.7.10 under it.",
            "Faithful resource pack supplement that adds 32x32 textures for GregTech, GT++, and the GTNH Core Mod.");
        github(
            RESTYLE,
            "UU-Tex",
            "Revurii (original: Jimbno)",
            "2.8.X",
            "revurii",
            "UU-Tex",
            "Retextures multiblock machines, circuit tiers, and parts. Overall darker appearance and extra "
                + "animations.");
        github(
            RESTYLE,
            "Wily-bit's-GTNH-tex",
            "Wily bit",
            "2.8.X",
            "Wilyb",
            "Wily-bit-s-GTNH-tex",
            "A resource pack that changes GTNH textures.");
        add(
            RESTYLE,
            "Zedtech-GTNH",
            "origami2147",
            "2.8.X, 2.9.X",
            Kind.PAGE,
            null,
            null,
            null,
            null,
            "https://www.curseforge.com/minecraft/texture-packs/zedtech-gtnh",
            "Published on CurseForge only: download it from its page there.",
            "A modern, Zedtech-style resource pack for GTNH.");
    }

    private GtnhPacks() {}

    private static void github(String category, String title, String team, String versions, String owner,
        String repo, String description) {
        add(category, title, team, versions, Kind.GITHUB, owner, repo, null, null, null, "", description);
    }

    private static void add(String category, String title, String team, String versions, Kind kind, String owner,
        String repo, String tag, String tags, String page, String note, String description) {
        String id = title.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-");
        String icon = kind == Kind.GITHUB ? "https://github.com/" + owner + ".png?size=64" : "";
        if (page == null) page = kind == Kind.GITHUB ? "https://github.com/" + owner + "/" + repo : "";
        PACKS.add(
            new Pack(
                Source.GTNH,
                id,
                title,
                team,
                description,
                0,
                icon,
                page,
                category,
                versions,
                note,
                kind,
                owner,
                repo,
                tag,
                tags == null ? null : Pattern.compile(tags)));
    }

    /** The packs of the list matching the query, marked when they seem to be in the folder already. */
    static Page search(String query, File folder) {
        String lower = query.toLowerCase(Locale.ROOT);
        List<Pack> found = new ArrayList<>();
        for (Pack pack : PACKS) {
            String text = (pack.title + " " + pack.author + " " + pack.description + " " + pack.category)
                .toLowerCase(Locale.ROOT);
            if (lower.isEmpty() || text.contains(lower)) found.add(pack);
        }
        InstalledPacks.markLikely(found, folder);
        return new Page(found, found.size());
    }

    // ---- the GTNH version

    /** The GTNH version line that is played, as {@code 2.8.X}, or null outside GTNH. */
    static synchronized String playerLine() {
        if (playerLine == null) {
            playerLine = "";
            try {
                Object version = Class.forName("com.dreammaster.lib.Refstrings")
                    .getField("MODPACKPACK_VERSION")
                    .get(null);
                Matcher matcher = LINE.matcher(String.valueOf(version));
                if (matcher.find()) playerLine = matcher.group(1) + "." + matcher.group(2) + ".X";
            } catch (Throwable ignored) {
                // not GTNH
            }
        }
        return playerLine.isEmpty() ? null : playerLine;
    }

    private static String lineOf(String tag) {
        Matcher matcher = LINE_TAG.matcher(tag);
        return matcher.find() ? matcher.group(1) + ".X" : null;
    }

    /** The version in a tag, without the GTNH version before it: {@code v2.9.X/v5.46} is 5.46. */
    private static String versionOf(String tag) {
        String version = LINE_TAG.matcher(tag)
            .replaceFirst("");
        return version.replaceFirst("^[vV.]+", "");
    }

    // ---- GitHub

    static Release github(Pack pack) throws IOException {
        String base = GITHUB_API + pack.owner + "/" + pack.repo;
        List<JsonObject> releases = new ArrayList<>();
        if (pack.tag != null) {
            releases.add(
                PackCatalog.get(base + "/releases/tags/" + PackCatalog.encode(pack.tag))
                    .getAsJsonObject());
        } else {
            for (JsonElement element : PackCatalog.get(base + "/releases?per_page=30")
                .getAsJsonArray()) {
                JsonObject release = element.getAsJsonObject();
                String tag = PackCatalog.string(release, "tag_name");
                if (PackCatalog.bool(release, "draft") || pack.tags != null && !pack.tags.matcher(tag)
                    .matches()) continue;
                releases.add(release);
            }
        }
        if (releases.isEmpty()) return branch(pack, base);

        // newest first: a stable release for the GTNH version that is played before a pre-release, and one for
        // another version only when there is none for it
        String line = playerLine();
        JsonObject chosen = null;
        for (int pass = 0; pass < 3 && chosen == null; pass++) {
            for (JsonObject release : releases) {
                String releaseLine = lineOf(PackCatalog.string(release, "tag_name"));
                boolean fits = line == null || releaseLine == null || releaseLine.equalsIgnoreCase(line);
                if (pass < 2 && !fits || pass == 0 && PackCatalog.bool(release, "prerelease")) continue;
                chosen = release;
                break;
            }
        }
        String tag = PackCatalog.string(chosen, "tag_name");
        String releaseLine = lineOf(tag);
        String note = line != null && releaseLine != null && !releaseLine.equalsIgnoreCase(line)
            ? "The newest release is made for GTNH " + releaseLine + ", not " + line + "."
            : PackCatalog.bool(chosen, "prerelease") ? "The newest release is a pre-release." : "";

        String version = versionOf(tag);
        List<PackFile> files = new ArrayList<>();
        for (JsonElement element : PackCatalog.array(chosen, "assets")) {
            JsonObject asset = element.getAsJsonObject();
            String name = PackCatalog.string(asset, "name");
            if (!name.toLowerCase(Locale.ROOT)
                .endsWith(".zip")) continue;
            // a release kept under one tag and replaced in place is told by the date of its file
            String fileVersion = version.matches(".*\\d.*") ? version
                : PackCatalog.string(asset, "updated_at")
                    .replaceFirst("T.*", "");
            files.add(
                new PackFile(
                    pack,
                    PackCatalog.string(asset, "browser_download_url"),
                    name,
                    fileVersion,
                    name.substring(0, name.length() - 4),
                    false));
        }
        if (files.isEmpty()) {
            // no file of its own: the sources of the release are the pack
            String safe = tag.replaceAll("[^A-Za-z0-9._-]+", "-");
            files.add(
                new PackFile(
                    pack,
                    "https://github.com/" + pack.owner
                        + "/"
                        + pack.repo
                        + "/archive/refs/tags/"
                        // a tag with a slash, as v2.9.X/v5.46, keeps it in the path
                        + PackCatalog.encode(tag)
                            .replace("%2F", "/")
                        + ".zip",
                    pack.repo + "-" + safe + ".zip",
                    version.matches(".*\\d.*") ? version
                        : PackCatalog.string(chosen, "published_at")
                            .replaceFirst("T.*", ""),
                    pack.title,
                    false));
        }
        return new Release(files, note);
    }

    /** A repository without releases: its default branch, the version its last commit. */
    private static Release branch(Pack pack, String base) throws IOException {
        String branch = PackCatalog.string(
            PackCatalog.get(base)
                .getAsJsonObject(),
            "default_branch");
        if (branch.isEmpty()) branch = "main";
        String sha = PackCatalog.string(
            PackCatalog.get(base + "/commits/" + PackCatalog.encode(branch))
                .getAsJsonObject(),
            "sha");
        String shortSha = sha.length() > 7 ? sha.substring(0, 7) : sha;
        PackFile file = new PackFile(
            pack,
            "https://github.com/" + pack.owner + "/" + pack.repo + "/archive/" + (sha.isEmpty() ? branch : sha) + ".zip",
            pack.repo + "-" + shortSha + ".zip",
            shortSha,
            pack.title,
            true);
        return new Release(
            Collections.singletonList(file),
            "No releases: the newest state of its " + branch + " branch.");
    }

    // ---- GitLab

    static Release gitlab(Pack pack) throws IOException {
        String project = PackCatalog.encode(pack.owner + "/" + pack.repo);
        JsonArray releases = PackCatalog.get("https://gitlab.com/api/v4/projects/" + project + "/releases")
            .getAsJsonArray();
        // newest first
        for (JsonElement element : releases) {
            JsonObject release = element.getAsJsonObject();
            if (PackCatalog.bool(release, "upcoming_release")) continue;
            String tag = PackCatalog.string(release, "tag_name");
            String version = versionOf(tag);
            JsonObject assets = PackCatalog.object(release, "assets");
            List<PackFile> files = new ArrayList<>();
            for (JsonElement link : PackCatalog.array(assets, "links")) {
                JsonObject object = link.getAsJsonObject();
                String url = PackCatalog.string(object, "direct_asset_url");
                if (url.isEmpty()) url = PackCatalog.string(object, "url");
                String name = PackCatalog.string(object, "name");
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                if (!fileName.toLowerCase(Locale.ROOT)
                    .endsWith(".zip")) {
                    if (!name.toLowerCase(Locale.ROOT)
                        .endsWith(".zip")) continue;
                    fileName = name;
                }
                files.add(new PackFile(pack, url, fileName, version, name.isEmpty() ? fileName : name, false));
            }
            if (files.isEmpty()) {
                for (JsonElement source : PackCatalog.array(assets, "sources")) {
                    JsonObject object = source.getAsJsonObject();
                    if (!"zip".equals(PackCatalog.string(object, "format"))) continue;
                    files.add(
                        new PackFile(
                            pack,
                            PackCatalog.string(object, "url"),
                            pack.repo + "-" + tag.replaceAll("[^A-Za-z0-9._-]+", "-") + ".zip",
                            version,
                            pack.title,
                            false));
                }
            }
            if (!files.isEmpty()) return new Release(files, "");
        }
        return new Release(Collections.<PackFile>emptyList(), "No release with a file found on GitLab.");
    }
}
