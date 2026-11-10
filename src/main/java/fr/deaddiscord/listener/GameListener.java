package fr.deaddiscord.listener;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import fr.deaddiscord.util.Texts;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

/** Ce qui se passe en jeu et doit apparaître sur Discord (chat, événements, journal des commandes). */
public final class GameListener implements Listener {

    private static final int GREEN = 0x57F287;
    private static final int RED = 0xED4245;
    private static final int GREY = 0x99AAB5;
    private static final int GOLD = 0xFEE75C;

    private final DeadDiscord plugin;

    public GameListener(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static String named(String template, String player) {
        return template.replace("{player}", Texts.escapeMarkdown(player));
    }

    // ------------------------------------------------------------------ chat

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.settings().mcToDiscord) return;
        plugin.bot().sendChat(e.getPlayer().getName(), plain(e.message()));
    }

    // ------------------------------------------------------------------ événements

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Settings s = plugin.settings();
        if (s.evJoin) plugin.bot().sendEvent(named(s.fmtJoin, e.getPlayer().getName()), GREEN);
        plugin.bot().updatePresence();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Settings s = plugin.settings();
        if (s.evQuit) plugin.bot().sendEvent(named(s.fmtQuit, e.getPlayer().getName()), GREY);
        // le joueur est encore compté au moment de l'événement : on rafraîchit un tick plus tard
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.bot().updatePresence());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Settings s = plugin.settings();
        if (!s.evDeath) return;
        String msg = plain(e.deathMessage());
        if (msg.isBlank()) return;
        plugin.bot().sendEvent(s.fmtDeath.replace("{message}", Texts.neutralizeMentions(msg)).replace("{player}", Texts.escapeMarkdown(e.getPlayer().getName())), RED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        Settings s = plugin.settings();
        if (!s.evAdvancement) return;
        String msg = plain(e.message()); // null (donc vide) pour les recettes et succès cachés
        if (msg.isBlank()) return;
        plugin.bot().sendEvent(s.fmtAdvancement.replace("{message}", Texts.neutralizeMentions(msg)).replace("{player}", Texts.escapeMarkdown(e.getPlayer().getName())), GOLD);
    }

    // ------------------------------------------------------------------ journal des commandes

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        plugin.admin().logPlayer(e.getPlayer().getName(), e.getPlayer().getWorld().getName(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent e) {
        if (e.getSender() instanceof ConsoleCommandSender) plugin.admin().logConsole(e.getCommand());
    }
}
