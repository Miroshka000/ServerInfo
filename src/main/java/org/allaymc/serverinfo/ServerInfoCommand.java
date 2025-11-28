package org.allaymc.serverinfo;

import org.allaymc.api.command.Command;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.player.Player;

/**
 * @author daoge_cmd
 */
public class ServerInfoCommand extends Command {
    public ServerInfoCommand() {
        super("serverinfo", "Control the server info", null);
    }

    @Override
    public void prepareCommandTree(CommandTree tree) {
        tree.getRoot()
                .key("msptbar")
                .bool("show")
                .exec(context -> {
                    if (!(context.getSender() instanceof Player player)) {
                        return context.fail();
                    }
                    boolean show = context.getResult(1);
                    var entity = player.getControlledEntity();
                    var bossBar = ServerInfo.INSTANCE.getOrCreateWorldMSPTBar(entity.getWorld());
                    if (show) {
                        bossBar.addViewer(player);
                    } else {
                        bossBar.removeViewer(player);
                    }
                    return context.success();
                })
                .root()
                .key("scoreboard")
                .bool("show")
                .exec(context -> {
                    if (!(context.getSender() instanceof Player player)) {
                        return context.fail();
                    }
                    boolean show = context.getResult(1);
                    var entity = player.getControlledEntity();
                    ServerInfo.INSTANCE.setScoreboardDisabled(entity, !show);
                    return context.success();
                });
    }
}
