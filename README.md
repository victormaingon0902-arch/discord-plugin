# DeadDiscord

Pont **Discord ⇄ Minecraft** pour Dead SMP (Paper 26.3, Java 25).

| Fonction | Détail |
|---|---|
| **Chat dans les deux sens** | Ce qui s'écrit en jeu apparaît sur Discord, et inversement. Connexions, déconnexions, morts, succès, démarrage/arrêt du serveur. |
| **Salon admin** | Journal de **toutes les commandes** tapées en jeu (mots de passe AuthMe masqués) + **exécution de commandes depuis Discord** (`!list`, `/cmd list`). |
| **/annonce** | Titre + sous-titre en grand sur l'écran de tous les joueurs (en jeu, ou depuis le salon admin). |
| **/link discord** | Lie un pseudo Minecraft à un compte Discord (code à usage unique). Le profil BountyCraft (shift + clic droit sur un joueur) affiche le Discord lié. |
| **Messages colorés sur Discord** | `/couleur` ou `!c &cBonjour &aà tous` : texte rouge, vert, gras, souligné, fond coloré… |

---

## 1. Créer le bot Discord (5 minutes)

1. Va sur <https://discord.com/developers/applications> → **New Application** (nom : *Dead SMP* par exemple).
2. Onglet **Bot** :
   - **Reset Token** → copie le token (il ne s'affiche qu'une fois). ⚠️ C'est un mot de passe : ne le partage jamais, ne le poste jamais sur Discord.
   - Descends à **Privileged Gateway Intents** et active **MESSAGE CONTENT INTENT** (obligatoire pour lire le chat Discord). *Server Members Intent* et *Presence Intent* : **inutiles**, laisse désactivés.
   - Sauvegarde.
3. Onglet **General Information** : copie l'**Application ID**, puis invite le bot avec ce lien (remplace `APPLICATION_ID`) :

   ```
   https://discord.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot+applications.commands&permissions=93184
   ```

   `permissions=93184` = voir les salons, écrire, intégrer des liens, lire l'historique, **gérer les messages** (pour supprimer le `!c …` d'origine).
   Si tu veux le rôle automatique « compte lié », ajoute aussi *Gérer les rôles* : `permissions=268528640`.
   Le scope `applications.commands` est **indispensable** pour les commandes slash (`/link`, `/couleur`, `/cmd`, `/annonce`).
4. Dans Discord : **Paramètres utilisateur › Avancés › Mode développeur** ✅. Ensuite clic droit → **Copier l'identifiant** sur : ton serveur, le salon de chat, le salon admin, et (si besoin) les rôles du staff.
5. Crée un salon **#admin** (ou utilise l'existant) **visible uniquement du staff**. Le bot doit pouvoir y lire/écrire.

## 2. Installer le plugin

1. Dépose `DeadDiscord-1.0.0.jar` dans `plugins/` et démarre le serveur **une fois** : Paper télécharge la bibliothèque JDA toute seule (Maven Central, quelques secondes, nécessite l'accès internet du serveur). Puis arrête-le.
2. Ouvre `plugins/DeadDiscord/config.yml` et remplis au minimum :

   ```yaml
   discord:
     token: "ton_token"            # ou variable d'environnement DEADDISCORD_TOKEN
     guild-id: "ID_DU_SERVEUR"
     invite-url: "https://discord.gg/ton_invitation"
   channels:
     chat: "ID_DU_SALON_CHAT"
     admin: "ID_DU_SALON_ADMIN"
   admin:
     exec:
       allowed-role-ids: ["ID_DU_ROLE_STAFF"]   # qui peut exécuter des commandes (vide = personne)
   ```
3. Redémarre. Dans la console tu dois voir `Bot Discord connecté` puis `Commandes slash enregistrées`.
4. En jeu : `/discord status` (permission `deaddiscord.admin`) affiche l'état du bot et si les salons sont trouvés.

## 3. Commandes

| Où | Commande | Rôle |
|---|---|---|
| Jeu | `/link discord` | Donne un code (valable 10 min, clic pour copier) |
| Discord | `/link code:XXXXXX` | Valide la liaison |
| Jeu | `/unlink discord` | Délie le compte |
| Discord | `/unlink` | Délie tes comptes |
| Jeu | `/discord` | Affiche le lien d'invitation |
| Jeu | `/discord reload` · `/discord status` | Admin (`deaddiscord.admin`) |
| Jeu | `/annonce Titre \| sous-titre` | Annonce en grand (`deaddiscord.announce`, op par défaut) |
| Discord (salon admin) | `/annonce`, `!annonce Titre \| sous-titre` | Idem, depuis Discord |
| Discord (salon admin) | `/cmd list`, `!list`, `!gamemode creative Steve` | Exécute une commande Minecraft |
| Discord (salon admin) | `!aide` | Rappel des commandes |
| Discord (partout) | `/couleur` | Message coloré |
| Discord (salon chat) | `!c &cBonjour &aà tous` | Message coloré (le bot republie, supprime ton message d'origine) |

### Annonces
`/annonce &c&lMAINTENANCE | Redémarrage dans 5 minutes` → titre rouge gras + sous-titre blanc, son, copie dans le chat du jeu et du Discord. Sans couleur, le titre est doré en gras (`announce.default-title-color`). Durée : `announce.default-seconds` ou l'option `duree` du slash.

## 4. Salon admin : journal et commandes

* **Journal** : chaque commande tapée en jeu arrive dans le salon admin, regroupée toutes les ~2 s : `14:03:12 🎮 Steve (world) : /gamemode creative`.
* **Mots de passe masqués** : `/login`, `/register`, `/changepassword`, `/unregister`, `/authme register …`, `/email`, `/totp`… s'affichent `login ***`. Les messages privés (`/msg`) sont aussi masqués (`msg Steve ***`) ; désactivable avec `admin.hide-private-messages: false`. Liste modifiable dans `admin.censor`.
* **Exécuter une commande** : `!list`, ou `/cmd` avec `commande: list`. La réponse de la commande revient sur Discord. Exécution avec les droits de la console.
* **Qui peut le faire** : uniquement les rôles/utilisateurs de `admin.exec.allowed-role-ids` / `allowed-user-ids` (**listes vides = personne**), et **seulement dans le salon admin**.
* **Commandes bloquées** même pour les autorisés : `op`, `deop`, `stop`, `restart`, `reload`, `rl`, `plugman` (`admin.exec.blocked-commands`).
* Les commandes slash `/cmd` et `/annonce` sont invisibles pour les non-administrateurs Discord. Pour les ouvrir à un rôle staff : *Paramètres du serveur › Intégrations › Dead SMP › /cmd › ajouter le rôle*. Sans ça, le staff peut utiliser le préfixe `!` qui n'a pas cette restriction.

> ⚠️ **Sécurité** : quelqu'un qui peut écrire dans ce salon avec un rôle autorisé **contrôle ton serveur**. Active la double authentification sur les comptes Discord du staff, ne mets personne d'autre dans la liste, garde le salon privé. Si un compte staff est piraté, le serveur l'est aussi.

## 5. Messages colorés sur Discord

Discord ne permet pas la couleur dans un message normal, mais **accepte les couleurs dans un bloc de code `ansi`**. Le plugin construit ce bloc pour toi.

**Syntaxe** (comme en jeu) : `&c` rouge · `&a` vert · `&e` jaune · `&9` bleu · `&d` rose · `&b` cyan · `&7` blanc · `&8` gris · `&l` gras · `&n` souligné · `&r` annule tout · `&&` = un vrai « & ».

* `!c &cRouge &aVert &lGras &r normal` dans le salon de chat.
* `/couleur texte:"&cRouge &aVert"` + options **couleur** (8 couleurs), **fond** (8 fonds), **style** (gras/souligné), **affichage**.
* `affichage: embed` : encadré avec barre latérale colorée, **compatible mobile**.

Limites de Discord : seulement **8 couleurs de texte et 8 fonds** (les 16 couleurs Minecraft sont ramenées à ces 8) ; **pas de couleur sur l'application mobile** (le bloc s'affiche en gris, utilise `affichage: embed`) ; pas d'italique/barré ; pas de mentions dans ces messages.

## 6. Liaison de compte et profil BountyCraft

1. En jeu : `/link discord` → code. 2. Sur Discord : `/link code:ABC123`. C'est lié.
* Le **profil BountyCraft** (shift + clic droit sur un joueur, ou `/profil`) affiche le Discord lié. BountyCraft interroge DeadDiscord par réflexion : il fonctionne aussi sans lui (affiche « non lié »).
* Liaison enregistrée par **pseudo** (serveur ouvert au crack : l'UUID change selon premium/cracké) dans `plugins/DeadDiscord/links.tsv`. Un pseudo ne peut être lié qu'à un seul Discord ; un Discord peut lier `link.max-accounts-per-discord` pseudos (2 par défaut : compte premium + cracké).
* Option `link.linked-role-id` : rôle donné automatiquement (le rôle du bot doit être **au-dessus**).
* `chat.use-linked-name: true` : sur le chat du jeu, un message Discord s'affiche avec le **pseudo Minecraft** du compte lié.

## 7. Dépannage

| Symptôme | Cause / solution |
|---|---|
| `Discord a refusé la connexion : les « intents »…` | Active **MESSAGE CONTENT INTENT** dans Developer Portal › Bot, puis redémarre. |
| `token invalide` | Reset Token dans le Developer Portal, recopie-le sans espace. |
| Le chat Discord n'arrive pas en jeu | Intent manquant, mauvais `channels.chat`, ou le bot n'a pas *Voir le salon / Lire l'historique*. `/discord status` indique si le salon est trouvé. Les messages qui commencent par `!` sont ignorés (`chat.ignore-prefixes`). |
| Les slash commands n'apparaissent pas | Le bot a été invité **sans** le scope `applications.commands` : réinvite-le avec le lien du §1. Recharge Discord (Ctrl+R). |
| `/cmd` introuvable pour le staff | Normal : réservée aux administrateurs Discord par défaut, voir §4. Utilise `!commande`. |
| `!c` ne supprime pas ton message | Donne la permission *Gérer les messages* au bot dans ce salon, ou mets `colors.delete-original: false`. |
| Couleurs absentes sur téléphone | Limite de Discord mobile : utilise `affichage: embed`. |
| Erreur au démarrage sur le téléchargement de JDA | L'hébergeur bloque l'accès à Maven Central : demande-moi la version avec JDA intégré. |

## 8. Compiler

```bash
mvn clean package
```
Java 25. Le jar est dans `target/DeadDiscord-1.0.0.jar`. JDA (version `jda.version` du `pom.xml`) n'est pas dans le jar : Paper la télécharge via la section `libraries` de `plugin.yml`. Si tu changes la version de JDA, change-la **à un seul endroit** (`<jda.version>` du `pom.xml`, elle est injectée dans `plugin.yml`).

## Permissions

| Permission | Défaut | Usage |
|---|---|---|
| `deaddiscord.link` | tout le monde | `/link discord` |
| `deaddiscord.announce` | op | `/annonce` en jeu |
| `deaddiscord.admin` | op | `/discord reload`, `/discord status` |
