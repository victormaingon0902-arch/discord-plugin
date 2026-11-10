package fr.deaddiscord.command;

import fr.deaddiscord.DeadDiscord;
import fr.deaddiscord.link.LinkStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** {@code /unlink discord} : supprime la liaison du joueur. */
public final class UnlinkCommand implements TabExecutor {

    private final DeadDiscord plugin;

    public UnlinkCommand(DeadDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        LinkStore.Link old = plugin.links().unlink(p.getName());
        if (old == null) {
            p.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<gray>Ton compte n'est lié à aucun Discord.</gray>"));
            return true;
        }
        // on retire le rôle « lié » seulement s'il ne reste aucun autre compte Minecraft sur ce Discord
        if (plugin.links().byDiscord(old.discordId()).isEmpty()) plugin.bot().setLinkedRole(old.discordId(), false);
        p.sendMessage(LinkCommand.MM.deserialize(LinkCommand.PREFIX + "<green>Compte Discord délié.</green>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "discord".startsWith(args[0].toLowerCase(Locale.ROOT))) return List.of("discord");
        return List.of();
    }
}
