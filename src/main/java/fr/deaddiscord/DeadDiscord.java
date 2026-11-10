package fr.deaddiscord;

import fr.deaddiscord.command.AnnonceCommand;
import fr.deaddiscord.command.DiscordCommand;
import fr.deaddiscord.command.LinkCommand;
import fr.deaddiscord.command.UnlinkCommand;
import fr.deaddiscord.discord.AdminConsole;
import fr.deaddiscord.discord.DiscordBot;
import fr.deaddiscord.link.LinkStore;
import fr.deaddiscord.listener.GameListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * DeadDiscord : pont Discord ⇄ Minecraft pour Dead SMP.
 * Chat dans les deux sens, salon admin (journal des commandes + exécution), /annonce, liaison de comptes
 * (/link discord) et messages colorés sur Discord.
 */
public final class DeadDiscord extends JavaPlugin {

    private volatile Settings settings;
    private LinkStore links;
    private DiscordBot bot;
    private AdminConsole admin;
    private Announcer announcer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());

        links = new LinkStore(getDataFolder().toPath().resolve("links.tsv"), System::currentTimeMillis,
                ex -> getLogger().log(Level.SEVERE, "Impossible d'enregistrer links.tsv", ex));
        try {
            links.load();
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Impossible de lire links.tsv", ex);
        }

        announcer = new Announcer(this);
        admin = new AdminConsole(this);
        bot = new DiscordBot(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        register("link", new LinkCommand(this));
        register("unlink", new UnlinkCommand(this));
        register("discord", new DiscordCommand(this));
        PluginCommand annonce = getCommand("annonce");
        if (annonce != null) annonce.setExecutor(new AnnonceCommand(this));

        admin.start();
        bot.start();
        // la présence (« 3/20 joueurs ») est aussi rafraîchie à chaque connexion/déconnexion
        getServer().getScheduler().runTaskTimer(this, () -> bot.updatePresence(), 200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (admin != null) admin.stop();
        if (bot != null) bot.stop();
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    /** Recharge config.yml (sauf le token et le serveur Discord, qui demandent un redémarrage). */
    public void reloadAll() {
        reloadConfig();
        settings = new Settings(getConfig());
        admin.start();
        bot.updatePresence();
    }

    public Settings settings() {
        return settings;
    }

    public LinkStore links() {
        return links;
    }

    public DiscordBot bot() {
        return bot;
    }

    public AdminConsole admin() {
        return admin;
    }

    public Announcer announcer() {
        return announcer;
    }

    /**
     * Nom Discord lié à ce joueur, ou null. Appelé par réflexion par BountyCraft pour le profil
     * (shift + clic droit sur un joueur) : ne pas renommer ni changer la signature.
     */
    public String linkedDiscordName(UUID uuid, String playerName) {
        LinkStore.Link l = playerName == null ? null : links.byName(playerName);
        if (l == null) return null;
        String name = l.discordName();
        return name == null || name.isBlank() ? null : name;
    }
}
