package fr.deaddiscord.command;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.Settings;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /discord} : lien d'invitation ; {@code /discord reload|status} pour les admins. */
public final class DiscordCommand implements TabExecutor {

    private final DeadDiscord plugin;

    public DiscordCommand(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("deaddiscord.admin")) {
                    sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<red>Tu n'as pas la permission.</red>"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<green>Configuration rechargée.</green> "
                        + "<gray>(un changement de token ou de serveur Discord demande un redémarrage)</gray>"));
            }
            case "status" -> {
                if (!sender.hasPermission("deaddiscord.admin")) {
                    sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<red>Tu n'as pas la permission.</red>"));
                    return true;
                }
                Settings s = plugin.settings();
                sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<gray>Bot : <white><status></white></gray>",
                        Placeholder.unparsed("status", plugin.bot().statusLine())));
                sender.sendMessage(LinkCommand.MM.deserialize("<gray> Salon chat : " + ok(plugin.bot().channelOk(s.chatChannelId))
                        + "  Salon admin : " + ok(plugin.bot().channelOk(s.adminChannelId))
                        + "  Liaisons : <white>" + plugin.links().size() + "</white></gray>"));
            }
            default -> {
                String url = plugin.settings().inviteUrl.replace("'", "").replace("<", "").replace(">", "");
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<gray>Rejoins-nous : </gray>"
                            + "<click:open_url:'" + url + "'><hover:show_text:'<gray>Ouvrir le lien'><aqua><underlined>" + url
                            + "</underlined></aqua></hover></click>"));
                } else {
                    sender.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<gray>Aucun lien Discord n'est configuré.</gray>"));
                }
            }
        }
        return true;
    }

    private static String ok(boolean b) {
        return b ? "<green>ok</green>" : "<red>introuvable</red>";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("deaddiscord.admin")) {
            for (String o : List.of("reload", "status")) if (o.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(o);
        }
        return out;
    }
}
