package mc.jazhdo;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelMessageSource;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextManager;

@Plugin(id = "chat-system-velocity", name = "ChatSystemVelocity", version = "0.1.5", dependencies = { @Dependency(id = "luckperms") })
public class ChatSystemVelocity {
    private final ProxyServer proxy;
    private final MinecraftChannelIdentifier global, single;
    private LuckPerms lp;
    private ContextManager cm;
    private final Logger logger;

    @Inject
    public ChatSystemVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.global = MinecraftChannelIdentifier.from("chatsystem:global");
        this.single = MinecraftChannelIdentifier.from("chatsystem:single");
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(global);
        proxy.getChannelRegistrar().register(single);
        lp = LuckPermsProvider.get();
        cm = lp.getContextManager();
    }

    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent event) {
        // Send join/transfer message to all players on the proxy
        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        broadcastGlobal("&e" + event.getPlayer().getUsername() + " has joined " + event.getServer().getServerInfo().getName() + " from " + (previousServer.isPresent() ? (previousServer.get().getServerInfo().getName() + ".") : "the server list"));
    }

    @Subscribe
    public void onPlayerLeave(DisconnectEvent event) {
        // Send leaving message to all players on the proxy
        Player player = event.getPlayer();
        Optional<ServerConnection> currentServer = player.getCurrentServer();
        broadcastGlobal("&e" + player.getUsername() + " has left the proxy from " + (currentServer.isPresent() ? currentServer.get().getServerInfo().getName() : "unknown") + ".");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only continue if its the right plugin messaging channel
        if (event.getIdentifier() != global) return;

        // Get and extract the data
        ByteArrayDataInput dataStream = event.dataAsDataStream();
        String playerName, msg;
        try {
            playerName = dataStream.readUTF();
            msg = dataStream.readUTF();
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "Data stream returned a Illegal State Exception: {0}", e.getMessage());
            return;
        }

        // Get the player to format it with their prefix
        Optional<Player> player = proxy.getPlayer(playerName);
        if (player.isPresent()) {
            ChannelMessageSource source = event.getSource();
            if (source instanceof ServerConnection connection) {
                Player p = player.get();
                broadcastGlobalWithoutProfanity(p, "[" + connection.getServerInfo().getName() + "] " + lp.getPlayerAdapter(Player.class).getUser(p).getCachedData().getMetaData(cm.getQueryOptions(p)).getPrefix() + "&f <" + playerName + ">: " + msg);
            } else logger.log(Level.WARNING, "PluginMessageEvent source {0} was not a ServerConnection.", source.toString());
        } else logger.log(Level.WARNING, "Player {0} could not be found by the proxy. Message \"{1}\" will not be sent to global chat.", new String[]{playerName, msg});

        // Prevent packet bouncing
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    private void broadcastGlobalWithoutProfanity(Player player, String msg) {
        if (!hasProfanity(player, msg)) broadcastGlobal(msg);
    }

    private void broadcastGlobal(String msg) {
        // Create new data stream to send to each plugin
        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        data.writeUTF(msg);

        // Broadcast chat message globally
        byte[] byteArray = data.toByteArray();
        for (RegisteredServer server : proxy.getAllServers())
            if (!server.getPlayersConnected().isEmpty()) server.sendPluginMessage(global, byteArray);
    }

    private void sendSingle(Player player, String msg) {
        // Build data stream
        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        data.writeUTF(player.getUsername());
        data.writeUTF(msg);

        // Send message
        player.sendPluginMessage(single, data.toByteArray());
    }

    private boolean hasProfanity(Player player, String msg) {
        // Forbidden word list from https://github.com/Hesham-Elbadawi/list-of-banned-words/blob/master/en
        List<String> forbidden = List.of("2g1c", "2 girls 1 cup", "acrotomophilia", "alabama hot pocket", "alaskan pipeline", "anal", "anilingus", "anus", "apeshit", "arsehole", "ass", "asshole", "assmunch", "auto erotic", "autoerotic", "babeland", "baby batter", "baby juice", "ball gag", "ball gravy", "ball kicking", "ball licking", "ball sack", "ball sucking", "bangbros", "bareback", "barely legal", "barenaked", "bastard", "bastardo", "bastinado", "bbw", "bdsm", "beaner", "beaners", "beaver cleaver", "beaver lips", "bestiality", "big black", "big breasts", "big knockers", "big tits", "bimbos", "birdlock", "bitch", "bitches", "black cock", "blonde action", "blonde on blonde action", "blowjob", "blow job", "blow your load", "blue waffle", "blumpkin", "bollocks", "bondage", "boner", "boob", "boobs", "booty call", "brown showers", "brunette action", "bukkake", "bulldyke", "bullet vibe", "bullshit", "bung hole", "bunghole", "busty", "butt", "buttcheeks", "butthole", "camel toe", "camgirl", "camslut", "camwhore", "carpet muncher", "carpetmuncher", "chocolate rosebuds", "circlejerk", "cleveland steamer", "clit", "clitoris", "clover clamps", "clusterfuck", "cock", "cocks", "coprolagnia", "coprophilia", "cornhole", "coon", "coons", "creampie", "cum", "cumming", "cunnilingus", "cunt", "darkie", "date rape", "daterape", "deep throat", "deepthroat", "dendrophilia", "dick", "dildo", "dingleberry", "dingleberries", "dirty pillows", "dirty sanchez", "doggie style", "doggiestyle", "doggy style", "doggystyle", "dog style", "dolcett", "domination", "dominatrix", "dommes", "donkey punch", "double dong", "double penetration", "dp action", "dry hump", "dvda", "eat my ass", "ecchi", "ejaculation", "erotic", "erotism", "escort", "eunuch", "faggot", "fecal", "felch", "fellatio", "feltch", "female squirting", "femdom", "figging", "fingerbang", "fingering", "fisting", "foot fetish", "footjob", "frotting", "fuck", "fuck buttons", "fuckin", "fucking", "fucktards", "fudge packer", "fudgepacker", "futanari", "gang bang", "gay sex", "genitals", "giant cock", "girl on", "girl on top", "girls gone wild", "goatcx", "goatse", "god damn", "gokkun", "golden shower", "goodpoop", "goo girl", "goregasm", "grope", "group sex", "g-spot", "guro", "hand job", "handjob", "hard core", "hardcore", "hentai", "homoerotic", "honkey", "hooker", "hot carl", "hot chick", "how to kill", "how to murder", "huge fat", "humping", "incest", "intercourse", "jack off", "jail bait", "jailbait", "jelly donut", "jerk off", "jigaboo", "jiggaboo", "jiggerboo", "jizz", "juggs", "kike", "kinbaku", "kinkster", "kinky", "knobbing", "leather restraint", "leather straight jacket", "lemon party", "lolita", "lovemaking", "make me come", "male squirting", "masturbate", "menage a trois", "milf", "missionary position", "motherfucker", "mound of venus", "mr hands", "muff diver", "muffdiving", "nambla", "nawashi", "negro", "neonazi", "nigga", "nigger", "nig nog", "nimphomania", "nipple", "nipples", "nsfw images", "nude", "nudity", "nympho", "nymphomania", "octopussy", "omorashi", "one cup two girls", "one guy one jar", "orgasm", "orgy", "paedophile", "paki", "panties", "panty", "pedobear", "pedophile", "pegging", "penis", "phone sex", "piece of shit", "pissing", "piss pig", "pisspig", "playboy", "pleasure chest", "pole smoker", "ponyplay", "poof", "poon", "poontang", "punany", "poop chute", "poopchute", "porn", "porno", "pornography", "prince albert piercing", "pthc", "pubes", "pussy", "queaf", "queef", "quim", "raghead", "raging boner", "rape", "raping", "rapist", "rectum", "reverse cowgirl", "rimjob", "rimming", "rosy palm", "rosy palm and her 5 sisters", "rusty trombone", "sadism", "santorum", "scat", "schlong", "scissoring", "semen", "sex", "sexo", "sexy", "shaved beaver", "shaved pussy", "shemale", "shibari", "shit", "shitblimp", "shitty", "shota", "shrimping", "skeet", "slanteye", "slut", "s&m", "smut", "snatch", "snowballing", "sodomize", "sodomy", "spic", "splooge", "splooge moose", "spooge", "spread legs", "spunk", "strap on", "strapon", "strappado", "strip club", "style doggy", "suck", "sucks", "suicide girls", "sultry women", "swastika", "swinger", "tainted love", "taste my", "tea bagging", "threesome", "throating", "tied up", "tight white", "tit", "tits", "titties", "titty", "tongue in a", "topless", "tosser", "towelhead", "tranny", "tribadism", "tub girl", "tubgirl", "tushy", "twat", "twink", "twinkie", "two girls one cup", "undressing", "upskirt", "urethra play", "urophilia", "vagina", "venus mound", "vibrator", "violet wand", "vorarephilia", "voyeur", "vulva", "wank", "wetback", "wet dream", "white power", "wrapping men", "wrinkled starfish", "yaoi", "yellow showers", "yiffy", "zoophilia", "🖕");
        
        // Iterate through all the words
        for (String word : forbidden)
            if (msg.contains(word)) {
                sendSingle(player, "&cYour message contained the forbidden word \"" + word + "\". It has not been sent.");
                return true;
            }
        return false;
    }
}