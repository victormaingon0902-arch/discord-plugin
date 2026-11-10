package fr.deaddiscord;

import fr.deaddiscord.util.CommandCensor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copie immuable de la configuration, lue sur le thread principal puis consultable sans risque depuis les threads
 * de JDA. Chaque valeur a un défaut dans le code : une clé absente d'un ancien config.yml ne casse rien.
 */
public final class Settings {

    // --- discord
    public final String token;
    public final String guildId;
    public final String inviteUrl;
    public final boolean presenceEnabled;
    public final String presenceType;
    public final String presenceText;

    // --- salons
    public final String chatChannelId;
    public final String adminChannelId;

    // --- minecraft -> discord
    public final boolean mcToDiscord;
    public final String chatStyle;
    public final String chatFormat;
    public final String avatarUrl;
    public final int embedColor;
    public final boolean evJoin, evQuit, evDeath, evAdvancement, evStartStop;
    public final String fmtJoin, fmtQuit, fmtDeath, fmtAdvancement, fmtStart, fmtStop;

    // --- discord -> minecraft
    public final boolean discordToMc;
    public final String mcFormat;
    public final boolean useLinkedName;
    public final int maxIncomingLength;
    public final List<String> ignorePrefixes;

    // --- salon admin
    public final boolean logCommands;
    public final boolean logConsole;
    public final int flushTicks;
    public final int maxLinesPerFlush;
    public final String logFormatPlayer;
    public final String logFormatConsole;
    public final Set<String> ignoredCommands;
    public final CommandCensor censor;
    public final boolean execEnabled;
    public final String execPrefix;
    public final Set<String> allowedRoleIds;
    public final Set<String> allowedUserIds;
    public final Set<String> blockedCommands;
    public final int feedbackTicks;
    public final boolean execCapture;

    // --- annonces
    public final int announceFadeIn, announceStay, announceFadeOut;
    public final boolean announceSoundEnabled;
    public final String announceSound;
    public final boolean announceChatCopy;
    public final boolean announceRelayToDiscord;
    public final String announceTitleColor;

    // --- liaison
    public final boolean linkEnabled;
    public final int linkCodeMinutes;
    public final String linkedRoleId;
    public final int maxAccountsPerDiscord;

    // --- couleurs discord
    public final boolean colorsEnabled;
    public final String colorsPrefix;
    public final int colorsMaxLength;
    public final boolean colorsDeleteOriginal;
    public final boolean colorsRelayToGame;

    public Settings(FileConfiguration c) {
        String envToken = System.getenv("DEADDISCORD_TOKEN");
        String cfgToken = c.getString("discord.token", "").strip();
        this.token = !cfgToken.isEmpty() && !cfgToken.startsWith("COLLE") ? cfgToken : (envToken == null ? "" : envToken.strip());
        this.guildId = id(c.getString("discord.guild-id", ""));
        this.inviteUrl = c.getString("discord.invite-url", "").strip();
        this.presenceEnabled = c.getBoolean("discord.presence.enabled", true);
        this.presenceType = c.getString("discord.presence.type", "watching").toLowerCase(Locale.ROOT);
        this.presenceText = c.getString("discord.presence.text", "{online}/{max} joueurs");

        this.chatChannelId = id(c.getString("channels.chat", ""));
        this.adminChannelId = id(c.getString("channels.admin", ""));

        this.mcToDiscord = c.getBoolean("chat.minecraft-to-discord", true);
        this.chatStyle = c.getString("chat.style", "text").toLowerCase(Locale.ROOT);
        this.chatFormat = c.getString("chat.format", "**{player}** » {message}");
        this.avatarUrl = c.getString("chat.avatar-url", "https://mc-heads.net/avatar/{player}/64");
        this.embedColor = parseColor(c.getString("chat.embed-color", "#5865F2"));
        this.evJoin = c.getBoolean("chat.events.join", true);
        this.evQuit = c.getBoolean("chat.events.quit", true);
        this.evDeath = c.getBoolean("chat.events.death", true);
        this.evAdvancement = c.getBoolean("chat.events.advancement", true);
        this.evStartStop = c.getBoolean("chat.events.server-start-stop", true);
        this.fmtJoin = c.getString("chat.events.format.join", "➕ **{player}** a rejoint le serveur");
        this.fmtQuit = c.getString("chat.events.format.quit", "➖ **{player}** a quitté le serveur");
        this.fmtDeath = c.getString("chat.events.format.death", "💀 {message}");
        this.fmtAdvancement = c.getString("chat.events.format.advancement", "🏆 {message}");
        this.fmtStart = c.getString("chat.events.format.start", "🟢 Le serveur est **en ligne** !");
        this.fmtStop = c.getString("chat.events.format.stop", "🔴 Le serveur est **hors ligne**.");

        this.discordToMc = c.getBoolean("chat.discord-to-minecraft", true);
        this.mcFormat = c.getString("chat.minecraft-format",
                "<dark_gray>[<#5865F2>Discord</#5865F2>]</dark_gray> <gray><user></gray> <dark_gray>»</dark_gray> <white><message></white>");
        this.useLinkedName = c.getBoolean("chat.use-linked-name", true);
        this.maxIncomingLength = Math.max(20, c.getInt("chat.max-incoming-length", 300));
        this.ignorePrefixes = new ArrayList<>(c.getStringList("chat.ignore-prefixes"));

        this.logCommands = c.getBoolean("admin.log-commands", true);
        this.logConsole = c.getBoolean("admin.log-console-commands", true);
        this.flushTicks = Math.max(10, c.getInt("admin.flush-seconds", 2) * 20);
        this.maxLinesPerFlush = Math.max(5, c.getInt("admin.max-lines-per-flush", 40));
        this.logFormatPlayer = c.getString("admin.log-format.player", "`{time}` 🎮 **{player}** ({world}) : `/{command}`");
        this.logFormatConsole = c.getString("admin.log-format.console", "`{time}` 🖥️ **Console** : `/{command}`");
        this.ignoredCommands = lower(c.getStringList("admin.ignored-commands"));

        List<String> censorEntries = new ArrayList<>(c.getStringList("admin.censor"));
        if (c.getBoolean("admin.hide-private-messages", true)) {
            censorEntries.addAll(c.getStringList("admin.private-commands"));
        }
        this.censor = new CommandCensor(CommandCensor.parse(censorEntries));

        this.execEnabled = c.getBoolean("admin.exec.enabled", true);
        String p = c.getString("admin.exec.prefix", "!");
        this.execPrefix = p == null || p.isBlank() ? "!" : p.strip();
        this.allowedRoleIds = idSet(c.getStringList("admin.exec.allowed-role-ids"));
        this.allowedUserIds = idSet(c.getStringList("admin.exec.allowed-user-ids"));
        this.blockedCommands = lower(c.getStringList("admin.exec.blocked-commands"));
        this.feedbackTicks = Math.max(1, c.getInt("admin.exec.feedback-ticks", 10));
        this.execCapture = !c.getString("admin.exec.execute-as", "capture").equalsIgnoreCase("console");

        this.announceFadeIn = Math.max(0, c.getInt("announce.fade-in-ticks", 10));
        this.announceStay = Math.max(20, c.getInt("announce.default-seconds", 6) * 20);
        this.announceFadeOut = Math.max(0, c.getInt("announce.fade-out-ticks", 20));
        this.announceSound = c.getString("announce.sound", "minecraft:block.note_block.pling").strip();
        this.announceSoundEnabled = c.getBoolean("announce.play-sound", true) && !announceSound.isEmpty();
        this.announceChatCopy = c.getBoolean("announce.copy-in-chat", true);
        this.announceRelayToDiscord = c.getBoolean("announce.relay-to-discord-chat", true);
        this.announceTitleColor = c.getString("announce.default-title-color", "gold");

        this.linkEnabled = c.getBoolean("link.enabled", true);
        this.linkCodeMinutes = Math.max(1, c.getInt("link.code-minutes", 10));
        this.linkedRoleId = id(c.getString("link.linked-role-id", ""));
        this.maxAccountsPerDiscord = c.getInt("link.max-accounts-per-discord", 2);

        this.colorsEnabled = c.getBoolean("colors.enabled", true);
        String cp = c.getString("colors.chat-prefix", "!c");
        this.colorsPrefix = cp == null || cp.isBlank() ? "!c" : cp.strip();
        this.colorsMaxLength = Math.max(50, Math.min(1900, c.getInt("colors.max-length", 1500)));
        this.colorsDeleteOriginal = c.getBoolean("colors.delete-original", true);
        this.colorsRelayToGame = c.getBoolean("colors.relay-to-game", true);
    }

    private static String id(String raw) {
        if (raw == null) return "";
        String s = raw.strip();
        // les identifiants Discord sont numériques ; on ignore les valeurs d'exemple
        return s.matches("\\d{5,25}") ? s : "";
    }

    private static Set<String> idSet(List<String> raw) {
        return raw.stream().map(Settings::id).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> lower(List<String> raw) {
        return raw.stream().map(CommandCensor::base).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    private static int parseColor(String hex) {
        try {
            String h = hex == null ? "" : hex.strip();
            if (h.startsWith("#")) h = h.substring(1);
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return 0x5865F2;
        }
    }

    public boolean embedStyle() {
        return chatStyle.equals("embed");
    }
}
