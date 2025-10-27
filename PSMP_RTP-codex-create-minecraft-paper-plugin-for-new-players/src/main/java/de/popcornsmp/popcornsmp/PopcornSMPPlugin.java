package de.popcornsmp.popcornsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PopcornSMPPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static final int SPAWN_RADIUS = 25_000;
    private static final int MAX_SPAWN_ATTEMPTS = 40;
    private static final int RTP_COUNTDOWN_SECONDS = 3;
    private static final int TPA_REQUEST_TIMEOUT_SECONDS = 60;
    private static final String PREFIX = ChatColor.GOLD + "" + ChatColor.BOLD + "PopcornSMP" + ChatColor.RESET
            + ChatColor.DARK_GRAY + " » " + ChatColor.RESET;
    private static final double MOVEMENT_TOLERANCE = 0.5;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Sound TELEPORT_SOUND = Sound.ENTITY_ENDERMAN_TELEPORT;
    private static final float TELEPORT_SOUND_VOLUME = 1.0f;
    private static final float TELEPORT_SOUND_PITCH = 1.2f;
    private static final Set<Material> UNSAFE_BLOCKS = EnumSet.of(
            Material.LAVA,
            Material.WATER,
            Material.KELP,
            Material.KELP_PLANT,
            Material.SEAGRASS,
            Material.TALL_SEAGRASS,
            Material.MAGMA_BLOCK,
            Material.CACTUS,
            Material.FIRE,
            Material.CAMPFIRE,
            Material.SOUL_FIRE,
            Material.SOUL_CAMPFIRE
    );

    private final Map<UUID, BukkitTask> pendingTeleportTasks = new HashMap<>();
    private final Map<UUID, Location> frozenAnchors = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportRequest> incomingTeleportRequests = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportRequest> outgoingTeleportRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Location> firstSpawnLocations = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerHomeManager> playerHomes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingHomeSlot = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingHomeName = new ConcurrentHashMap<>();
    private final Set<UUID> waitingForHomeName = ConcurrentHashMap.newKeySet();
    private HomeStorageManager homeStorage;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        homeStorage = new HomeStorageManager(getDataFolder(), getLogger());

        Objects.requireNonNull(getCommand("rtp"), "Command /rtp not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("tpa"), "Command /tpa not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("tpaccept"), "Command /tpaccept not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("tpdeny"), "Command /tpdeny not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("home"), "Command /home not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("sethome"), "Command /sethome not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("delhome"), "Command /delhome not defined in plugin.yml").setExecutor(this);
        Objects.requireNonNull(getCommand("homes"), "Command /homes not defined in plugin.yml").setExecutor(this);
        getLogger().info("PopcornSMP plugin enabled.");
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, PlayerHomeManager> entry : playerHomes.entrySet()) {
            homeStorage.savePlayerHomes(entry.getKey(), entry.getValue());
        }

        pendingTeleportTasks.values().forEach(BukkitTask::cancel);
        pendingTeleportTasks.clear();
        frozenAnchors.clear();
        incomingTeleportRequests.clear();
        outgoingTeleportRequests.clear();
        firstSpawnLocations.clear();
        playerHomes.clear();
        pendingHomeSlot.clear();
        pendingHomeName.clear();
        waitingForHomeName.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(buildJoinQuitMessage(player, ChatColor.GREEN, "BETRETEN"));

        PlayerHomeManager homeManager = homeStorage.loadPlayerHomes(player.getUniqueId());
        playerHomes.put(player.getUniqueId(), homeManager);

        if (!player.hasPlayedBefore()) {
            World world = Objects.requireNonNull(Bukkit.getWorlds().get(0), "No default world loaded");
            Location spawn = findSpawnLocation(world);
            boolean randomSpawn = spawn != null;

            if (!randomSpawn) {
                spawn = world.getSpawnLocation();
            }

            player.getInventory().addItem(new ItemStack(Material.BREAD, 16));
            teleportPlayer(player, spawn, randomSpawn);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(buildJoinQuitMessage(event.getPlayer(), ChatColor.RED, "VERLASSEN"));
        UUID playerId = event.getPlayer().getUniqueId();
        firstSpawnLocations.remove(playerId);

        PlayerHomeManager homeManager = playerHomes.remove(playerId);
        if (homeManager != null) {
            homeStorage.savePlayerHomes(playerId, homeManager);
        }

        pendingHomeSlot.remove(playerId);
        pendingHomeName.remove(playerId);
        waitingForHomeName.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location firstSpawn = firstSpawnLocations.get(player.getUniqueId());

        if (firstSpawn != null && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            event.setRespawnLocation(firstSpawn);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location bedSpawn = player.getRespawnLocation();

        if (bedSpawn != null && bedSpawn.getBlock().getType() == Material.AIR) {
            Location firstSpawn = firstSpawnLocations.get(player.getUniqueId());
            if (firstSpawn != null) {
                player.setRespawnLocation(firstSpawn, true);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Location anchor = frozenAnchors.get(playerId);

        if (anchor == null) {
            return;
        }

        Location to = event.getTo();

        if (to == null) {
            return;
        }

        double distanceSquared = anchor.distanceSquared(to);

        if (distanceSquared <= MOVEMENT_TOLERANCE * MOVEMENT_TOLERANCE) {
            return;
        }

        cancelPendingTeleport(playerId);
        frozenAnchors.remove(playerId);
        player.resetTitle();
        player.sendMessage(PREFIX + ChatColor.RED + "Teleport abgebrochen, weil du dich bewegt hast.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        return switch (name) {
            case "rtp" -> handleRandomTeleportCommand(sender);
            case "tpa" -> handleTeleportRequestCommand(sender, args);
            case "tpaccept" -> handleTeleportAcceptCommand(sender);
            case "tpdeny" -> handleTeleportDenyCommand(sender);
            case "home" -> handleHomeCommand(sender, args);
            case "sethome" -> handleSetHomeCommand(sender, args);
            case "delhome" -> handleDelHomeCommand(sender, args);
            case "homes" -> handleHomesListCommand(sender);
            default -> false;
        };
    }

    private boolean handleRandomTeleportCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (pendingTeleportTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ein Teleport läuft bereits – bitte warte einen Moment.");
            return true;
        }

        startTeleportCountdown(player, () -> prepareRandomTeleport(player));
        return true;
    }

    private boolean handleTeleportRequestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(PREFIX + ChatColor.RED + "Verwendung: /tpa <Spieler>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Dieser Spieler ist nicht online.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Du kannst dich nicht zu dir selbst teleportieren.");
            return true;
        }

        TeleportRequest request = new TeleportRequest(player.getUniqueId(), target.getUniqueId(),
                System.currentTimeMillis() + TPA_REQUEST_TIMEOUT_SECONDS * 1000L);

        TeleportRequest previousIncoming = incomingTeleportRequests.put(target.getUniqueId(), request);
        if (previousIncoming != null && !previousIncoming.requesterId.equals(player.getUniqueId())) {
            Player previousRequester = Bukkit.getPlayer(previousIncoming.requesterId);
            if (previousRequester != null) {
                previousRequester.sendMessage(PREFIX + ChatColor.RED + "Deine Teleportanfrage an "
                        + ChatColor.GOLD + target.getName() + ChatColor.RED + " wurde durch eine neue Anfrage ersetzt.");
            }
            outgoingTeleportRequests.remove(previousIncoming.requesterId);
        }

        TeleportRequest previousOutgoing = outgoingTeleportRequests.put(player.getUniqueId(), request);
        if (previousOutgoing != null && !previousOutgoing.targetId.equals(target.getUniqueId())) {
            incomingTeleportRequests.remove(previousOutgoing.targetId, previousOutgoing);
            Player previousTarget = Bukkit.getPlayer(previousOutgoing.targetId);
            if (previousTarget != null) {
                previousTarget.sendMessage(PREFIX + ChatColor.RED + "Die Teleportanfrage von "
                        + ChatColor.GOLD + player.getName() + ChatColor.RED + " wurde zurückgezogen.");
            }
        }

        player.sendMessage(PREFIX + ChatColor.GRAY + "Teleportanfrage an " + ChatColor.GOLD + target.getName()
                + ChatColor.GRAY + " gesendet. Sie läuft in " + ChatColor.GOLD + TPA_REQUEST_TIMEOUT_SECONDS
                + ChatColor.GRAY + " Sekunden ab.");
        target.sendMessage(buildTeleportRequestMessage(player));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, 1.0f, 1.0f);

        new BukkitRunnable() {
            @Override
            public void run() {
                TeleportRequest current = incomingTeleportRequests.get(target.getUniqueId());
                if (current != request) {
                    return;
                }

                if (!request.isExpired()) {
                    return;
                }

                incomingTeleportRequests.remove(target.getUniqueId(), request);
                outgoingTeleportRequests.remove(player.getUniqueId(), request);

                if (player.isOnline()) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Deine Teleportanfrage an "
                            + ChatColor.GOLD + target.getName() + ChatColor.RED + " ist abgelaufen.");
                }

                if (target.isOnline()) {
                    target.sendMessage(PREFIX + ChatColor.RED + "Die Teleportanfrage von "
                            + ChatColor.GOLD + player.getName() + ChatColor.RED + " ist abgelaufen.");
                }
            }
        }.runTaskLater(this, TPA_REQUEST_TIMEOUT_SECONDS * 20L);

        return true;
    }

    private boolean handleTeleportAcceptCommand(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        TeleportRequest request = incomingTeleportRequests.get(target.getUniqueId());

        if (request == null) {
            target.sendMessage(PREFIX + ChatColor.RED + "Du hast keine ausstehenden Teleportanfragen.");
            return true;
        }

        if (request.isExpired()) {
            cleanupTeleportRequest(request);
            target.sendMessage(PREFIX + ChatColor.RED + "Die Teleportanfrage ist bereits abgelaufen.");
            return true;
        }

        Player requester = Bukkit.getPlayer(request.requesterId);

        if (requester == null || !requester.isOnline()) {
            cleanupTeleportRequest(request);
            target.sendMessage(PREFIX + ChatColor.RED + "Der anfragende Spieler ist nicht mehr online.");
            return true;
        }

        if (pendingTeleportTasks.containsKey(requester.getUniqueId())) {
            target.sendMessage(PREFIX + ChatColor.RED + "Dieser Spieler führt bereits einen Teleport aus.");
            return true;
        }

        cleanupTeleportRequest(request);

        requester.sendMessage(PREFIX + ChatColor.GRAY + "Deine Anfrage wurde von " + ChatColor.GOLD + target.getName()
                + ChatColor.GRAY + " akzeptiert.");
        target.sendMessage(PREFIX + ChatColor.GRAY + "Du hast die Teleportanfrage von " + ChatColor.GOLD + requester.getName()
                + ChatColor.GRAY + " akzeptiert.");

        startTeleportCountdown(requester, () -> teleportToPlayer(requester, target));
        return true;
    }

    private boolean handleTeleportDenyCommand(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        TeleportRequest request = incomingTeleportRequests.get(target.getUniqueId());

        if (request == null) {
            target.sendMessage(PREFIX + ChatColor.RED + "Du hast keine ausstehenden Teleportanfragen.");
            return true;
        }

        cleanupTeleportRequest(request);

        Player requester = Bukkit.getPlayer(request.requesterId);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(PREFIX + ChatColor.RED + "Deine Teleportanfrage an " + ChatColor.GOLD + target.getName()
                    + ChatColor.RED + " wurde abgelehnt.");
        }

        target.sendMessage(PREFIX + ChatColor.GRAY + "Du hast die Teleportanfrage abgelehnt.");
        return true;
    }

    private void startTeleportCountdown(Player player, Runnable onSuccess) {
        UUID playerId = player.getUniqueId();

        player.sendMessage(PREFIX + ChatColor.GRAY + "Du wirst in " + ChatColor.GOLD + RTP_COUNTDOWN_SECONDS
                + ChatColor.GRAY + " Sekunden teleportiert. Bewege dich nicht!");
        frozenAnchors.put(playerId, player.getLocation().clone());

        BukkitTask task = new BukkitRunnable() {
            private int secondsLeft = RTP_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelAndCleanup();
                    return;
                }

                if (!frozenAnchors.containsKey(playerId)) {
                    cancelAndCleanup();
                    return;
                }

                if (secondsLeft <= 0) {
                    cancel();
                    pendingTeleportTasks.remove(playerId);
                    frozenAnchors.remove(playerId);
                    player.resetTitle();
                    onSuccess.run();
                    return;
                }

                player.sendTitle(ChatColor.GOLD + "" + secondsLeft + ChatColor.GRAY + " Sekunden bis zum Teleport",
                        "", 0, 20, 0);
                float pitch = 1.0f + (RTP_COUNTDOWN_SECONDS - secondsLeft) * 0.15f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.MASTER, 1.0f, pitch);
                secondsLeft--;
            }

            private void cancelAndCleanup() {
                cancel();
                pendingTeleportTasks.remove(playerId);
                frozenAnchors.remove(playerId);
                if (player.isOnline()) {
                    player.resetTitle();
                }
            }
        }.runTaskTimer(PopcornSMPPlugin.this, 0L, 20L);

        pendingTeleportTasks.put(playerId, task);
    }

    private Component buildJoinQuitMessage(Player player, ChatColor actionColor, String actionText) {
        String legacyMessage = ChatColor.GOLD + player.getName() + ChatColor.GRAY + " hat den Server "
                + actionColor + ChatColor.BOLD + actionText + ChatColor.RESET;
        return LEGACY_SERIALIZER.deserialize(legacyMessage);
    }

    private Component buildTeleportRequestMessage(Player requester) {
        Component prefixComponent = LEGACY_SERIALIZER.deserialize(PREFIX);
        Component separator = LEGACY_SERIALIZER.deserialize(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH
                + "------------------------------");
        Component acceptButton = Component.text("[ANNEHMEN]", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport annehmen", NamedTextColor.GREEN)));
        Component denyButton = Component.text("[ABLEHNEN]", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(Component.text("Teleport ablehnen", NamedTextColor.RED)));

        Component buttonLine = Component.text()
                .append(Component.text("                         "))
                .append(acceptButton)
                .append(Component.text(" "))
                .append(denyButton)
                .build();

        return Component.text()
                .append(separator)
                .append(Component.newline())
                .append(prefixComponent)
                .append(Component.text(requester.getName(), NamedTextColor.GOLD))
                .append(Component.text(" möchte sich zu dir teleportieren.", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.newline())
                .append(buttonLine)
                .append(Component.newline())
                .append(Component.newline())
                .append(LEGACY_SERIALIZER.deserialize(PREFIX))
                .append(Component.text("Nutze ", NamedTextColor.GRAY))
                .append(Component.text("/tpaccept", NamedTextColor.GOLD))
                .append(Component.text(" oder ", NamedTextColor.GRAY))
                .append(Component.text("/tpdeny", NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(separator)
                .build();
    }

    private void prepareRandomTeleport(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Location target = findSpawnLocation(player.getWorld());

        if (target == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Es konnte aktuell keine sichere Position gefunden werden. Versuche es später erneut.");
            return;
        }

        performRandomTeleport(player, target);
    }

    private void teleportPlayer(Player player, Location location, boolean randomSpawn) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Location target = location.clone();
        world.getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(this, () -> {
                    player.teleport(target);
                    if (randomSpawn && !player.hasPlayedBefore()) {
                        firstSpawnLocations.put(player.getUniqueId(), target.clone());
                        player.setRespawnLocation(target, true);
                    }
                    sendWelcomeExperience(player, target, randomSpawn);
                })
        ).exceptionally(throwable -> {
            getLogger().warning("Failed to prepare random spawn chunk: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(this, () -> {
                player.teleport(world.getSpawnLocation());
                sendWelcomeExperience(player, world.getSpawnLocation(), false);
            });
            return null;
        });
    }

    private Location findSpawnLocation(World world) {
        Random random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            int x = random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;
            int z = random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS;

            int y;
            if (world.getEnvironment() == World.Environment.NETHER) {
                y = findSafeNetherY(world, x, z);
                if (y == -1) {
                    continue;
                }
            } else {
                y = world.getHighestBlockYAt(x, z);
            }

            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            Block blockBelow = world.getBlockAt(x, y - 1, z);

            if (isSafeBlock(blockBelow.getType())) {
                return candidate.add(0, 1, 0);
            }
        }

        getLogger().warning("No safe spawn found within attempts; using world spawn location.");
        return null;
    }

    private int findSafeNetherY(World world, int x, int z) {
        for (int y = 120; y >= 32; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block blockAbove = world.getBlockAt(x, y + 1, z);
            Block blockTwoAbove = world.getBlockAt(x, y + 2, z);
            Block blockBelow = world.getBlockAt(x, y - 1, z);

            if (isSafeBlock(blockBelow.getType()) &&
                block.getType().isAir() &&
                blockAbove.getType().isAir() &&
                blockTwoAbove.getType().isAir()) {
                return y;
            }
        }
        return -1;
    }

    private boolean isSafeBlock(Material material) {
        if (material.isAir()) {
            return false;
        }
        return !UNSAFE_BLOCKS.contains(material);
    }

    private void sendWelcomeExperience(Player player, Location location, boolean randomSpawn) {
        player.sendTitle(ChatColor.GOLD + "Willkommen auf PopcornSMP", ChatColor.YELLOW + "Viel Spaß auf PopcornSMP.de!", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        player.sendMessage(PREFIX + ChatColor.GRAY + "Willkommen " + ChatColor.GOLD + player.getName() + ChatColor.GRAY + " auf PopcornSMP.de!");
        if (randomSpawn) {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Du wurdest durch einen Random Teleport zur Position "
                    + ChatColor.GOLD + location.getBlockX() + ChatColor.GRAY + ", "
                    + ChatColor.GOLD + location.getBlockY() + ChatColor.GRAY + ", "
                    + ChatColor.GOLD + location.getBlockZ() + ChatColor.GRAY + " in der Welt gespawnt.");
        } else {
            player.sendMessage(PREFIX + ChatColor.GRAY + "Wir konnten dich sicher am Welten-Spawn platzieren ("
                    + ChatColor.GOLD + location.getBlockX() + ChatColor.GRAY + ", "
                    + ChatColor.GOLD + location.getBlockY() + ChatColor.GRAY + ", "
                    + ChatColor.GOLD + location.getBlockZ() + ChatColor.GRAY + ").");
        }

        spawnFirework(player.getLocation());
    }

    private void spawnFirework(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            Firework firework = (Firework) world.spawnEntity(location, EntityType.FIREWORK_ROCKET);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.ORANGE)
                    .withFade(Color.YELLOW)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .build());
            firework.setFireworkMeta(meta);
        });
    }

    private void performRandomTeleport(Player player, Location target) {
        World world = target.getWorld();
        if (world == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Teleport fehlgeschlagen: Welt nicht gefunden.");
            return;
        }

        world.getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(this, () -> {
                    player.teleport(target);
                    player.sendMessage(PREFIX + ChatColor.GRAY + "Du wurdest durch einen Random Teleport zur Position "
                            + ChatColor.GOLD + target.getBlockX() + ChatColor.GRAY + ", "
                            + ChatColor.GOLD + target.getBlockY() + ChatColor.GRAY + ", "
                            + ChatColor.GOLD + target.getBlockZ() + ChatColor.GRAY + " teleportiert.");
                    player.playSound(player.getLocation(), TELEPORT_SOUND, SoundCategory.MASTER,
                            TELEPORT_SOUND_VOLUME, TELEPORT_SOUND_PITCH);
                })
        ).exceptionally(throwable -> {
            getLogger().warning("Failed to prepare chunk for /rtp: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(this, () -> player.sendMessage(PREFIX + ChatColor.RED + "Teleport fehlgeschlagen. Bitte versuche es erneut."));
            return null;
        });
    }

    private void teleportToPlayer(Player requester, Player target) {
        if (!requester.isOnline()) {
            return;
        }

        if (!target.isOnline()) {
            requester.sendMessage(PREFIX + ChatColor.RED + "Teleport fehlgeschlagen: Der Zielspieler ist nicht mehr online.");
            return;
        }

        Location targetLocation = target.getLocation();
        World world = targetLocation.getWorld();

        if (world == null) {
            requester.sendMessage(PREFIX + ChatColor.RED + "Teleport fehlgeschlagen: Welt nicht gefunden.");
            return;
        }

        world.getChunkAtAsync(targetLocation.getBlockX() >> 4, targetLocation.getBlockZ() >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(this, () -> {
                    requester.teleport(targetLocation);
                    requester.sendMessage(PREFIX + ChatColor.GRAY + "Du wurdest zu " + ChatColor.GOLD + target.getName()
                            + ChatColor.GRAY + " teleportiert.");
                    requester.playSound(requester.getLocation(), TELEPORT_SOUND, SoundCategory.MASTER,
                            TELEPORT_SOUND_VOLUME, TELEPORT_SOUND_PITCH);
                })
        ).exceptionally(throwable -> {
            getLogger().warning("Failed to prepare chunk for /tpa: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(this, () -> requester.sendMessage(PREFIX + ChatColor.RED
                    + "Teleport fehlgeschlagen. Bitte versuche es erneut."));
            return null;
        });
    }

    private void cleanupTeleportRequest(TeleportRequest request) {
        incomingTeleportRequests.remove(request.targetId, request);
        outgoingTeleportRequests.remove(request.requesterId, request);
    }

    private void cancelPendingTeleport(UUID playerId) {
        BukkitTask task = pendingTeleportTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        frozenAnchors.remove(playerId);
    }

    private boolean handleHomeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        PlayerHomeManager homeManager = playerHomes.computeIfAbsent(player.getUniqueId(), k -> new PlayerHomeManager());

        if (args.length == 0) {
            Inventory homeGUI = HomeGUIManager.createHomeGUI(player, homeManager);
            player.openInventory(homeGUI);
        } else {
            String homeName = args[0];
            HomeData home = homeManager.getHomeByName(homeName);

            if (home == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "Home " + ChatColor.GOLD + homeName + ChatColor.RED + " nicht gefunden.");
                return true;
            }

            if (pendingTeleportTasks.containsKey(player.getUniqueId())) {
                player.sendMessage(PREFIX + ChatColor.RED + "Ein Teleport läuft bereits – bitte warte einen Moment.");
                return true;
            }

            startTeleportCountdown(player, () -> teleportToHome(player, home));
        }

        return true;
    }

    private boolean handleSetHomeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Verwendung: /sethome <Name>");
            return true;
        }

        String homeName = args[0];
        PlayerHomeManager homeManager = playerHomes.computeIfAbsent(player.getUniqueId(), k -> new PlayerHomeManager());

        if (homeManager.getHomeByName(homeName) != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Ein Home mit diesem Namen existiert bereits.");
            return true;
        }

        int freeSlot = homeManager.getNextFreeSlot();
        if (freeSlot == -1) {
            player.sendMessage(PREFIX + ChatColor.RED + "Du hast keine freien Home-Slots mehr. Kaufe weitere Slots mit /home.");
            return true;
        }

        HomeData home = new HomeData(homeName, player.getLocation().clone(), Material.RED_BED);
        homeManager.setHome(freeSlot, home);
        homeStorage.savePlayerHomes(player.getUniqueId(), homeManager);

        player.sendMessage(PREFIX + ChatColor.GRAY + "Home " + ChatColor.GOLD + homeName + ChatColor.GRAY + " wurde gesetzt!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);

        return true;
    }

    private boolean handleDelHomeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Verwendung: /delhome <Name>");
            return true;
        }

        String homeName = args[0];
        PlayerHomeManager homeManager = playerHomes.get(player.getUniqueId());

        if (homeManager == null || homeManager.getHomeByName(homeName) == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Home " + ChatColor.GOLD + homeName + ChatColor.RED + " nicht gefunden.");
            return true;
        }

        homeManager.removeHomeByName(homeName);
        homeStorage.savePlayerHomes(player.getUniqueId(), homeManager);

        player.sendMessage(PREFIX + ChatColor.GRAY + "Home " + ChatColor.GOLD + homeName + ChatColor.GRAY + " wurde gelöscht.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.MASTER, 1.0f, 1.0f);

        return true;
    }

    private boolean handleHomesListCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        PlayerHomeManager homeManager = playerHomes.get(player.getUniqueId());

        if (homeManager == null || homeManager.getHomeCount() == 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Du hast keine Homes gesetzt.");
            return true;
        }

        player.sendMessage(PREFIX + ChatColor.GRAY + "Deine Homes:");
        for (Map.Entry<Integer, HomeData> entry : homeManager.getAllHomes().entrySet()) {
            HomeData home = entry.getValue();
            Location loc = home.getLocation();
            player.sendMessage(ChatColor.GOLD + "  • " + home.getName() + ChatColor.GRAY + " (" +
                    ChatColor.YELLOW + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ChatColor.GRAY + ")");
        }

        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        if (title.equals("§6§lDeine Homes")) {
            event.setCancelled(true);
            handleHomeGUIClick(player, event);
        } else if (title.equals("§6§lWähle ein Icon")) {
            event.setCancelled(true);
            handleIconSelectionClick(player, event);
        }
    }

    private void handleHomeGUIClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        int slot = HomeGUIManager.getSlotFromInventorySlot(event.getSlot());
        if (slot == -1) {
            return;
        }

        PlayerHomeManager homeManager = playerHomes.get(player.getUniqueId());
        if (homeManager == null) {
            return;
        }

        if (homeManager.hasHome(slot)) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                homeManager.removeHome(slot);
                homeStorage.savePlayerHomes(player.getUniqueId(), homeManager);
                player.sendMessage(PREFIX + ChatColor.GRAY + "Home wurde gelöscht.");
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Inventory newGUI = HomeGUIManager.createHomeGUI(player, homeManager);
                    player.openInventory(newGUI);
                }, 1L);
            } else {
                player.closeInventory();
                HomeData home = homeManager.getHome(slot);
                if (pendingTeleportTasks.containsKey(player.getUniqueId())) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Ein Teleport läuft bereits – bitte warte einen Moment.");
                    return;
                }
                startTeleportCountdown(player, () -> teleportToHome(player, home));
            }
        } else if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE && homeManager.isSlotUnlocked(slot)) {
            player.closeInventory();
            pendingHomeSlot.put(player.getUniqueId(), slot);
            waitingForHomeName.add(player.getUniqueId());
            player.sendMessage(PREFIX + ChatColor.GRAY + "Gib den Namen für dein Home ein (ä, ö, ü sind erlaubt):");
        } else if (clicked.getType() == Material.RED_STAINED_GLASS_PANE && !homeManager.isSlotUnlocked(slot)) {
            if (player.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 32)) {
                player.getInventory().removeItem(new ItemStack(Material.DIAMOND, 32));
                homeManager.unlockSlot(slot);
                homeStorage.savePlayerHomes(player.getUniqueId(), homeManager);
                player.sendMessage(PREFIX + ChatColor.GRAY + "Slot erfolgreich für " + ChatColor.GOLD + "32 Diamanten" + ChatColor.GRAY + " gekauft!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Inventory newGUI = HomeGUIManager.createHomeGUI(player, homeManager);
                    player.openInventory(newGUI);
                }, 1L);
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Du benötigst 32 Diamanten um diesen Slot zu kaufen!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private void handleIconSelectionClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String homeName = pendingHomeName.remove(player.getUniqueId());
        Integer slot = pendingHomeSlot.remove(player.getUniqueId());

        if (homeName == null || slot == null) {
            player.closeInventory();
            return;
        }

        PlayerHomeManager homeManager = playerHomes.get(player.getUniqueId());
        if (homeManager == null) {
            player.closeInventory();
            return;
        }

        Material icon = clicked.getType();
        HomeData home = new HomeData(homeName, player.getLocation().clone(), icon);
        homeManager.setHome(slot, home);
        homeStorage.savePlayerHomes(player.getUniqueId(), homeManager);

        player.closeInventory();
        player.sendMessage(PREFIX + ChatColor.GRAY + "Home " + ChatColor.GOLD + homeName + ChatColor.GRAY + " wurde gesetzt!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (waitingForHomeName.contains(playerId)) {
            event.setCancelled(true);
            String homeName = event.getMessage();

            waitingForHomeName.remove(playerId);
            pendingHomeName.put(playerId, homeName);

            Bukkit.getScheduler().runTask(this, () -> {
                player.sendMessage(PREFIX + ChatColor.GRAY + "Wähle nun ein Icon für dein Home:");
                Inventory iconGUI = HomeGUIManager.createIconSelectionGUI();
                player.openInventory(iconGUI);
            });
        }
    }

    private void teleportToHome(Player player, HomeData home) {
        if (!player.isOnline()) {
            return;
        }

        Location target = home.getLocation();
        World world = target.getWorld();

        if (world == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Teleport fehlgeschlagen: Welt nicht gefunden.");
            return;
        }

        world.getChunkAtAsync(target.getBlockX() >> 4, target.getBlockZ() >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(this, () -> {
                    player.teleport(target);
                    player.sendMessage(PREFIX + ChatColor.GRAY + "Du wurdest zu deinem Home " + ChatColor.GOLD + home.getName()
                            + ChatColor.GRAY + " teleportiert.");
                    player.playSound(player.getLocation(), TELEPORT_SOUND, SoundCategory.MASTER,
                            TELEPORT_SOUND_VOLUME, TELEPORT_SOUND_PITCH);
                })
        ).exceptionally(throwable -> {
            getLogger().warning("Failed to prepare chunk for home teleport: " + throwable.getMessage());
            Bukkit.getScheduler().runTask(this, () -> player.sendMessage(PREFIX + ChatColor.RED
                    + "Teleport fehlgeschlagen. Bitte versuche es erneut."));
            return null;
        });
    }

    private static final class TeleportRequest {
        private final UUID requesterId;
        private final UUID targetId;
        private final long expiresAt;

        private TeleportRequest(UUID requesterId, UUID targetId, long expiresAt) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
