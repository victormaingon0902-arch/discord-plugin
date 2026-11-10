package fr.deaddiscord;

import fr.deaddiscord.util.Ansi;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/**
 * Annonce en grand (titre + sous-titre) sur l'écran de tous les joueurs.
 * Texte : {@code Titre | sous-titre}, avec les couleurs {@code &c}, {@code &l}… comme dans le jeu.
 */
public final class Announcer {

    private final DeadDiscord plugin;

    public Announcer(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    /** Nombre de joueurs qui vont la recevoir. Peut être appelé depuis n'importe quel thread. */
    public int announce(String rawText, int seconds, String author) {
        Settings s = plugin.settings();
        String text = rawText == null ? "" : rawText.replace('§', '&').strip();
        String titlePart = text;
        String subPart = "";
        int bar = text.indexOf('|');
        if (bar >= 0) {
            titlePart = text.substring(0, bar).strip();
            subPart = text.substring(bar + 1).strip();
        }
        if (titlePart.isEmpty() && !subPart.isEmpty()) {
            titlePart = subPart;
            subPart = "";
        }
        if (titlePart.isEmpty()) return 0;

        NamedTextColor named = NamedTextColor.NAMES.value(s.announceTitleColor.toLowerCase(Locale.ROOT));
        final TextColor color = named != null ? named : NamedTextColor.GOLD;

        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        Component title = legacy.deserialize(titlePart)
                .colorIfAbsent(color)
                .decorationIfAbsent(TextDecoration.BOLD, TextDecoration.State.TRUE);
        Component subtitle = subPart.isEmpty() ? Component.empty() : legacy.deserialize(subPart).colorIfAbsent(NamedTextColor.WHITE);

        int stayTicks = seconds > 0 ? Math.min(seconds, 60) * 20 : s.announceStay;
        Title.Times times = Title.Times.times(
                Duration.ofMillis(s.announceFadeIn * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(s.announceFadeOut * 50L));
        Title shown = Title.title(title, subtitle, times);

        Sound sound = null;
        if (s.announceSoundEnabled) {
            try {
                sound = Sound.sound(Key.key(s.announceSound), Sound.Source.MASTER, 1.0f, 1.0f);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("announce.sound invalide : " + s.announceSound);
            }
        }
        final Sound finalSound = sound;
        final String plainTitle = Ansi.strip(titlePart);
        final String plainSub = Ansi.strip(subPart);

        Runnable show = () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(shown);
                if (finalSound != null) p.playSound(finalSound);
            }
            if (s.announceChatCopy) {
                Component line = Component.text("📢 ", NamedTextColor.GOLD)
                        .append(title.decoration(TextDecoration.BOLD, false));
                if (!plainSub.isEmpty()) line = line.append(Component.text(" — ", NamedTextColor.GRAY)).append(subtitle);
                Bukkit.broadcast(line);
            }
            plugin.getLogger().info("Annonce de " + author + " : " + plainTitle + (plainSub.isEmpty() ? "" : " | " + plainSub));
        };
        if (Bukkit.isPrimaryThread()) show.run();
        else Bukkit.getScheduler().runTask(plugin, show);

        if (s.announceRelayToDiscord) {
            plugin.bot().sendChatLine("📢 **Annonce** : " + fr.deaddiscord.util.Texts.cleanChat(
                    plainTitle + (plainSub.isEmpty() ? "" : " — " + plainSub), 500));
        }
        return Bukkit.getOnlinePlayers().size();
    }
}
