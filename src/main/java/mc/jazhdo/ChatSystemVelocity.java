package mc.jazhdo;

import java.util.Comparator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;

@Plugin(id = "chat-system-velocity", name = "ChatSystemVelocity", version = "0.1.0", dependencies = { @Dependency(id = "luckperms") })
public class ChatSystemVelocity {
    private final ProxyServer proxy;
    private final MinecraftChannelIdentifier id;
    private final LuckPerms lp;
    private final Logger logger;

    @Inject
    public ChatSystemVelocity(ProxyServer proxy, Logger logger, LuckPerms lp) {
        this.proxy = proxy;
        this.id = MinecraftChannelIdentifier.from("chatsystem:global");
        this.logger = logger;
        this.lp = lp;
    }

    @Subscribe
    public void onProxyEnable(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(id);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        // Only continue if its the right plugin messaging channel
        if (event.getIdentifier() != id) return;

        // Get and extract the data
        ByteArrayDataInput dataStream = event.dataAsDataStream();
        String playerName = dataStream.readUTF(), msg = dataStream.readUTF();

        // Get the player to format their message
        Optional<Player> player = proxy.getPlayer(playerName);
        if (player.isEmpty()) {
            logger.log(Level.WARNING, "Player {0} could not be found by the proxy. Message \"{1}\" will not be sent to global chat.", new String[]{playerName, msg});
            return;
        }
        User user = lp.getPlayerAdapter(Player.class).getUser(player.get());

        // Get the prefix with highest priority
        Optional<PrefixNode> prefix = user.getNodes(NodeType.PREFIX).stream().max(Comparator.comparingInt(PrefixNode::getPriority));

        // Create new data stream to send to each plugin
        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        data.writeUTF((prefix.isPresent() ? ("[" + prefix.get().getKey() + "] ") : "") + "<" + playerName + ">: " + msg);

        // Broadcast chat message globally
        if (event.getSource() instanceof ServerConnection) for (RegisteredServer server : proxy.getAllServers()) if (!server.getPlayersConnected().isEmpty()) server.sendPluginMessage(id, data.toByteArray());
        else logger.log(Level.WARNING, "A plugin messaging event's source ({0}) is not a server connection.", event.getSource().toString());
    }
}