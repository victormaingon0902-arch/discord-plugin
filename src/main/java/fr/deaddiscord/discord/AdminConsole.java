package fr.deaddiscord.discord;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import fr.deaddiscord.util.CommandCensor;
import fr.deaddiscord.util.Texts;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Salon admin : journal des commandes tapées en jeu (envoyées par paquets toutes les ~2 secondes pour ne pas
 * saturer Discord) et exécution de commandes depuis Discord.
 */
public final class AdminConsole {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DeadDiscord plugin;
    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask flushTask;

    public AdminConsole(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ cycle de vie

    public void start() {
        stopTask();
        int ticks = plugin.settings().flushTicks;
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> flush(false), ticks, ticks);
    }

    private void stopTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    /** À l'arrêt du serveur : on envoie tout ce qui reste. */
    public void stop() {
        stopTask();
        flush(true);
    }

    // ------------------------------------------------------------------ journal

    /** Enregistre une commande de joueur (appelé depuis l'écouteur d'événements, thread principal). */
    public void logPlayer(String player, String world, String rawCommand) {
        Settings s = plugin.settings();
        if (!s.logCommands) return;
        String shown = prepare(rawCommand, s);
        if (shown == null) return;
        String line = s.logFormatPlayer
                .replace("{time}", LocalTime.now().format(TIME))
                .replace("{player}", Texts.escapeMarkdown(player))
                .replace("{world}", Texts.inlineCode(world))
                .replace("{command}", Texts.inlineCode(shown));
        queue.add(line);
    }

    public void logConsole(String rawCommand) {
        Settings s = plugin.settings();
        if (!s.logCommands || !s.logConsole) return;
        String shown = prepare(rawCommand, s);
        if (shown == null) return;
        queue.add(s.logFormatConsole
                .replace("{time}", LocalTime.now().format(TIME))
                .replace("{command}", Texts.inlineCode(shown)));
    }

    /** Commande à afficher (censurée, tronquée) ou null si elle doit être ignorée. */
    private String prepare(String raw, Settings s) {
        if (raw == null) return null;
        String stripped = raw.strip();
        while (stripped.startsWith("/")) stripped = stripped.substring(1).stripLeading();
        if (stripped.isEmpty()) return null;
        int space = stripped.indexOf(' ');
        String first = CommandCensor.base(space < 0 ? stripped : stripped.substring(0, space));
        if (s.ignoredCommands.contains(first)) return null;
        return Texts.truncate(s.censor.apply(stripped), 300);
    }

    public void flush(boolean sync) {
        if (queue.isEmpty()) return;
        Settings s = plugin.settings();
        List<String> lines = new ArrayList<>();
        String line;
        while (lines.size() < s.maxLinesPerFlush && (line = queue.poll()) != null) lines.add(line);
        int dropped = 0;
        while (queue.poll() != null) dropped++;
        if (dropped > 0) lines.add("*(+" + dropped + " commandes non affichées)*");
        if (lines.isEmpty()) return;
        for (String chunk : Texts.chunk(lines, 1900)) plugin.bot().sendAdmin(chunk, sync);
    }

    // ------------------------------------------------------------------ exécution depuis Discord

    /** Autorisé = utilisateur listé, ou possesseur d'un des rôles listés. Listes vides = personne. */
    public boolean authorized(Collection<String> roleIds, String userId) {
        Settings s = plugin.settings();
        if (s.allowedUserIds.contains(userId)) return true;
        for (String r : roleIds) {
            if (s.allowedRoleIds.contains(r)) return true;
        }
        return false;
    }

    private boolean isBlocked(String command, Settings s) {
        String[] tok = command.strip().split("\\s+");
        if (tok.length == 0) return false;
        if (s.blockedCommands.contains(CommandCensor.base(tok[0]))) return true;
        if (CommandCensor.base(tok[0]).equals("execute")) {
            for (int i = 1; i < tok.length - 1; i++) {
                if (tok[i].equalsIgnoreCase("run") && s.blockedCommands.contains(CommandCensor.base(tok[i + 1]))) return true;
            }
        }
        return false;
    }

    /**
     * Exécute une commande sur le serveur (thread principal) puis appelle {@code reply} avec le résultat.
     * Peut être appelé depuis n'importe quel thread.
     */
    public void execute(String rawCommand, String who, Consumer<String> reply) {
        Settings s = plugin.settings();
        String cmd = rawCommand == null ? "" : rawCommand.strip();
        while (cmd.startsWith("/")) cmd = cmd.substring(1).stripLeading();
        if (cmd.isEmpty()) {
            reply.accept("Indique une commande, par exemple `list`.");
            return;
        }
        if (isBlocked(cmd, s)) {
            reply.accept("⛔ Cette commande est bloquée depuis Discord (`admin.exec.blocked-commands`).");
            return;
        }
        final String command = cmd;
        Bukkit.getScheduler().runTask(plugin, () -> run(command, who, reply));
    }

    private void run(String command, String who, Consumer<String> reply) {
        Settings s = plugin.settings();
        List<String> out = Collections.synchronizedList(new ArrayList<>());
        CommandSender sender = s.execCapture
                ? Bukkit.createCommandSender(c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)))
                : Bukkit.getConsoleSender();
        boolean ok;
        try {
            ok = Bukkit.dispatchCommand(sender, command);
        } catch (Throwable t) {
            plugin.getLogger().warning("Commande Discord en erreur : /" + command + " -> " + t);
            reply.accept("❌ Erreur pendant l'exécution : `" + Texts.inlineCode(String.valueOf(t.getMessage())) + "`");
            return;
        }
        plugin.getLogger().info("[Discord] " + who + " a exécuté : /" + s.censor.apply(command));
        final boolean success = ok;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String head = (success ? "✅" : "⚠️") + " `/" + Texts.inlineCode(Texts.truncate(s.censor.apply(command), 150))
                    + "` — par **" + Texts.escapeMarkdown(who) + "**";
            if (!success && out.isEmpty()) head += "\nCommande inconnue ou refusée.";
            String body;
            synchronized (out) {
                body = String.join("\n", out).replace('`', '\'');
            }
            if (body.isBlank()) {
                if (success) head += s.execCapture ? "\n*(la commande n'a renvoyé aucun message)*" : "\n*(envoyée à la console, voir les logs du serveur)*";
                reply.accept(head);
                return;
            }
            int room = 1990 - head.length() - 10;
            reply.accept(head + "\n```\n" + Texts.truncate(body, Math.max(100, room)) + "\n```");
        }, s.feedbackTicks);
    }
}
