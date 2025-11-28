package org.allaymc.serverinfo;

import lombok.Getter;
import org.allaymc.api.bossbar.BossBar;
import org.allaymc.api.bossbar.BossBarColor;
import org.allaymc.api.entity.component.EntityPlayerBaseComponent;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.entity.EntityTeleportEvent;
import org.allaymc.api.eventbus.event.server.PlayerJoinEvent;
import org.allaymc.api.eventbus.event.server.PlayerQuitEvent;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.player.Player;
import org.allaymc.api.plugin.Plugin;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.scoreboard.Scoreboard;
import org.allaymc.api.scoreboard.data.DisplaySlot;
import org.allaymc.api.server.Server;
import org.allaymc.api.utils.config.Config;
import org.allaymc.api.world.World;
import org.joml.Vector3d;

import java.util.*;

@Getter
public final class ServerInfo extends Plugin {

    public static ServerInfo INSTANCE;

    private final Map<World, BossBar> BOSS_BARS = new HashMap<>();
    private final Set<EntityPlayer> SCOREBOARD_DISABLED = new HashSet<>();
    private Settings SETTINGS;

    @Override
    public void onLoad() {
        INSTANCE = this;
        getPluginLogger().info("ServerInfo loaded!");
        var configFile = pluginContainer.dataFolder().resolve("config.yml").toFile();
        var config = new Config(configFile, Config.YAML);
        SETTINGS = new Settings();
        SETTINGS.showWorldInfo = config.getBoolean("show-world-info", true);
        SETTINGS.showPlayerInfo = config.getBoolean("show-player-info", true);
        SETTINGS.showChunkInfo = config.getBoolean("show-chunk-info", true);
        SETTINGS.showLightInfo = config.getBoolean("show-light-info", true);
        SETTINGS.showMiscInfo = config.getBoolean("show-misc-info", true);
        SETTINGS.showMSPTBar = config.getBoolean("show-mspt-bar", true);
    }

    @Override
    public void onEnable() {
        getPluginLogger().info("ServerInfo enabled!");
        Server.getInstance().getEventBus().registerListener(this);
        Registries.COMMANDS.register(new ServerInfoCommand());
        if (SETTINGS.showMSPTBar) {
            Server.getInstance().getScheduler().scheduleRepeating(this, () -> {
                updateMSPTBars();
                return true;
            }, 20);
        }
    }

    @Override
    public void onDisable() {
        getPluginLogger().info("ServerInfo disabled!");
        Server.getInstance().getEventBus().unregisterListener(this);
    }

    public BossBar getOrCreateWorldMSPTBar(World world) {
        return BOSS_BARS.computeIfAbsent(world, name -> BossBar.create());
    }

    public boolean isScoreboardDisabled(EntityPlayer player) {
        return SCOREBOARD_DISABLED.contains(player);
    }

    public void setScoreboardDisabled(EntityPlayer player, boolean disabled) {
        if (disabled) {
            SCOREBOARD_DISABLED.add(player);
        } else {
            SCOREBOARD_DISABLED.remove(player);
        }
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var entity = player.getControlledEntity();

        if (SETTINGS.showMSPTBar) {
            getOrCreateWorldMSPTBar(entity.getWorld()).addViewer(player);
        }

        var scoreboard = new Scoreboard("Dashboard");
        scoreboard.addViewer(player, DisplaySlot.SIDEBAR);

        Server.getInstance().getScheduler().scheduleRepeating(this, () -> {
            if (player.isDisconnected()) {
                return false;
            }
            if (!isScoreboardDisabled(entity)) {
                scoreboard.addViewer(player, DisplaySlot.SIDEBAR);
                updateScoreboard(entity, scoreboard);
            } else {
                scoreboard.removeViewer(player, DisplaySlot.SIDEBAR);
            }
            return true;
        }, 20);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var entity = player.getControlledEntity();
        if (SETTINGS.showMSPTBar) {
            getOrCreateWorldMSPTBar(entity.getWorld()).removeViewer(player);
        }
        SCOREBOARD_DISABLED.remove(entity);
    }

    @EventHandler
    private void onPlayerTeleport(EntityTeleportEvent event) {
        if (!event.isTeleportBetweenWorlds()) {
            return;
        }

        if (!(event.getEntity() instanceof EntityPlayer player)) {
            return;
        }

        var controller = player.getController();
        if (controller != null) {
            getOrCreateWorldMSPTBar(event.getFrom().dimension().getWorld()).removeViewer(controller);
            getOrCreateWorldMSPTBar(event.getTo().dimension().getWorld()).addViewer(controller);
        }
    }

    private void updateMSPTBars() {
        BOSS_BARS.forEach((world, bossbar) -> {
            bossbar.setTitle("TPS: §a" + world.getTPS());
            var mspt = world.getMSPT();
            // Progress should between 0 and 1
            bossbar.setProgress(Math.min(1.0f, mspt / 50.0f));
            if (mspt > 50) {
                bossbar.setColor(BossBarColor.RED);
            } else if (mspt > 40) {
                bossbar.setColor(BossBarColor.PINK);
            } else if (mspt > 25) {
                bossbar.setColor(BossBarColor.YELLOW);
            } else {
                bossbar.setColor(BossBarColor.GREEN);
            }
        });
    }

    private void updateScoreboard(EntityPlayer player, Scoreboard scoreboard) {
        if (!player.isInWorld()) return;

        var lines = new ArrayList<String>();
        var controller = player.getController();

        if (SETTINGS.showWorldInfo) {
            var worldInfo = "World: §a" + player.getWorld().getWorldData().getDisplayName() + "\n§f" +
                            "Time: §a" + player.getWorld().getWorldData().getTimeOfDay() + "\n§f" +
                            "TPS: §a" + MathUtils.round(player.getWorld().getTPS(), 2) + "\n§f" +
                            "MSPT: §a" + MathUtils.round(player.getWorld().getMSPT(), 2);
            lines.add(worldInfo);
        }

        if (SETTINGS.showMiscInfo) {
            var itemInHand = player.getItemInHand();

            lines.add(
                    "ItemInHand:\n§a" + itemInHand.getItemType().getIdentifier().path() + (itemInHand.getMeta() != 0 ? ":" + itemInHand.getMeta() : "") + "\n§f" +
                    "StandingOn:\n§a" + player.getBlockStateStandingOn().getBlockType().getIdentifier().path()
            );
        }

        if (SETTINGS.showChunkInfo) {
            var chunk = player.getCurrentChunk();
            var chunkManager = player.getDimension().getChunkManager();
            var chunkInfo =
                    "Chunk: §a" + chunk.getX() + "," + chunk.getZ() + "\n§f" +
                    "Loaded: §a" + chunkManager.getLoadedChunks().size() + "\n§f";
            try {
                var floorLoc = player.getLocation().floor(new Vector3d());
                chunkInfo += "Biome:\n§a" + player.getCurrentChunk().getBiome((int) floorLoc.x() & 15, (int) floorLoc.y(), (int) floorLoc.z() & 15).toString().toLowerCase();
            } catch (IllegalArgumentException e) {
                chunkInfo += "Biome: §aN/A";
            }
            lines.add(chunkInfo);
        }

        if (SETTINGS.showPlayerInfo && controller != null) {
            var playerInfo = "Ping: §a" + controller.getPing() + "\n§f" +
                             "Food: §a" + player.getFoodLevel() + "/" + EntityPlayerBaseComponent.MAX_FOOD_LEVEL + "\n§f" +
                             "Exhaustion: §a" + MathUtils.round(player.getFoodExhaustionLevel(), 2) + "/" + EntityPlayerBaseComponent.MAX_FOOD_EXHAUSTION_LEVEL + "\n§f" +
                             "Saturation: §a" + MathUtils.round(player.getFoodSaturationLevel(), 2) + "/" + EntityPlayerBaseComponent.MAX_FOOD_SATURATION_LEVEL + "\n§f" +
                             "Exp: §a" + player.getExperienceInCurrentLevel() + "/" + player.getRequiredExperienceForCurrentLevel();
            lines.add(playerInfo);
        }

        if (SETTINGS.showLightInfo) {
            var floorLoc = player.getLocation().floor(new Vector3d());
            int x = (int) floorLoc.x();
            int y = (int) floorLoc.y();
            int z = (int) floorLoc.z();
            var lightEngine = player.getDimension().getLightEngine();
            var lightInfo = "Itl: §a" + lightEngine.getInternalLight(x, y, z) + "\n§f" +
                            "Block: §a" + lightEngine.getBlockLight(x, y, z) + "\n§f" +
                            "Sky: §a" + lightEngine.getSkyLight(x, y, z) + "\n§f" +
                            "ItlSky: §a" + lightEngine.getInternalSkyLight(x, y, z);
            lines.add(lightInfo);
        }

        scoreboard.setLines(lines);
    }
}