package com.landinsurance;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class insurance extends JavaPlugin implements CommandExecutor, Listener {
    
    private NamespacedKey rollbackKey;
    private NamespacedKey xKey;
    private NamespacedKey yKey;
    private NamespacedKey zKey;
    private NamespacedKey timeKey;
    private NamespacedKey dimKey;

    @Override
    public void onEnable() {
        if (this.getCommand("insurance") != null) {
            this.getCommand("insurance").setExecutor(this);
        }
        
        rollbackKey = new NamespacedKey(this, "is_rollback");
        xKey = new NamespacedKey(this, "x");
        yKey = new NamespacedKey(this, "y");
        zKey = new NamespacedKey(this, "z");
        timeKey = new NamespacedKey(this, "timestamp");
        dimKey = new NamespacedKey(this, "dimension");
        
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) return;
        
        Player player = event.getPlayer();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // --- SELECTOR LOGIC (Nether Star) ---
        if (item.getType() == Material.NETHER_STAR && meta.getAsString().contains("selector")) {
            Location loc = player.getLocation();
            boolean overlapFound = false;
            for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 100, 100, 100)) {
                if (entity.getType() == org.bukkit.entity.EntityType.MARKER && 
                    entity.getScoreboardTags().contains("selected")) {
                    if (entity.getLocation().distance(loc) <= 100) {
                        overlapFound = true;
                        break;
                    }
                }
            }

            if (overlapFound) {
                player.sendMessage(ChatColor.RED + "Choose A Valid Center");
                event.setCancelled(true); 
                return;
            }
            
            loc.getWorld().spawn(loc, org.bukkit.entity.Marker.class, marker -> {
                marker.addScoreboardTag("selected");
            });
            
            player.getInventory().addItem(createRollbackPaper(
                loc.getBlockX(), 
                loc.getBlockY(),
                loc.getBlockZ(),
                loc.getWorld().getName()
            ));
        }

        // --- ROLLBACK EXECUTION LOGIC (Paper) ---
        if (item.getType() == Material.PAPER && pdc.has(rollbackKey, PersistentDataType.BOOLEAN)) {
            try {
                int x = pdc.get(xKey, PersistentDataType.INTEGER);
                int y = pdc.get(yKey, PersistentDataType.INTEGER);
                int z = pdc.get(zKey, PersistentDataType.INTEGER);
                long timestamp = pdc.get(timeKey, PersistentDataType.LONG);
                String dimension = pdc.get(dimKey, PersistentDataType.STRING);

                org.bukkit.World targetWorld = Bukkit.getWorld(dimension);
                if (targetWorld == null) {
                    player.sendMessage(ChatColor.RED + "Target dimension not found!");
                    event.setCancelled(true);
                    return;
                }

                Location loc = new Location(targetWorld, x, y, z);
                CoreProtectAPI cpAPI = getCoreProtect();
                
                if (cpAPI == null) {
                    player.sendMessage(ChatColor.RED + "CoreProtect API is not available.");
                    event.setCancelled(true);
                    return;
                }

                long diff = (System.currentTimeMillis() / 1000L) - timestamp;

                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    List<Integer> actions = Arrays.asList(0, 1);
                    List<Object> excludedBlocks = new ArrayList<>();
                    excludedBlocks.add(Material.DIAMOND_BLOCK);
                    excludedBlocks.add(Material.NETHERITE_BLOCK);
                    excludedBlocks.add(Material.HEAVY_CORE);
                    excludedBlocks.add(Material.CONDUIT);
                    
                    cpAPI.performRollback(
                        (int) diff, null, null, null, excludedBlocks, actions, 100, loc
                    );

                    Bukkit.getScheduler().runTask(this, () -> {
                        for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                            if (entity.getType() == org.bukkit.entity.EntityType.MARKER && 
                                entity.getScoreboardTags().contains("selected")) {
                                entity.remove();
                            }
                        }
                        player.sendMessage(ChatColor.GREEN + "Rollback performed successfully");
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                    });
                });
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Corrupted Rollback Item.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        org.bukkit.entity.Item itemDrop = event.getItemDrop();
        ItemStack droppedItem = itemDrop.getItemStack();
        
        if (droppedItem.getType() == Material.PAPER && droppedItem.hasItemMeta()) {
            PersistentDataContainer pdc = droppedItem.getItemMeta().getPersistentDataContainer();
            
            if (pdc.has(rollbackKey, PersistentDataType.BOOLEAN)) {
                Player player = event.getPlayer();
                itemDrop.remove();
                
                String selectorString = "minecraft:nether_star[item_name='Selector',lore=['Hold Use To Record A Rollback Center'],use_cooldown={seconds:5},food={nutrition:0,saturation:0,can_always_eat:true},consumable={consume_seconds:1,animation:\"block\",sound:\"minecraft:entity.illusioner.cast_spell\"},custom_data={selector:true}]";
                ItemStack selectorItem = Bukkit.getItemFactory().createItemStack(selectorString);
                player.getInventory().addItem(selectorItem);
                
                try {
                    int x = pdc.get(xKey, PersistentDataType.INTEGER);
                    int y = pdc.get(yKey, PersistentDataType.INTEGER);
                    int z = pdc.get(zKey, PersistentDataType.INTEGER);
                    String dimension = pdc.get(dimKey, PersistentDataType.STRING);

                    org.bukkit.World targetWorld = Bukkit.getWorld(dimension);
                    if (targetWorld != null) {
                        Location targetLoc = new Location(targetWorld, x, y, z);
                        for (org.bukkit.entity.Entity entity : targetWorld.getNearbyEntities(targetLoc, 1.5, 1.5, 1.5)) {
                            if (entity.getType() == org.bukkit.entity.EntityType.MARKER && 
                                entity.getScoreboardTags().contains("selected")) {
                                entity.remove();
                            }
                        }
                    }
                } catch (Exception e) {}
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) return false;

        if (args[0].equalsIgnoreCase("stamp")) {
            player.getInventory().addItem(createRollbackPaper(
                player.getLocation().getBlockX(), 
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(),
                player.getWorld().getName()
            ));
            return true;
        }
        return false;
    }
    
    private ItemStack createRollbackPaper(int x, int y, int z, String dimension) {
        long now = System.currentTimeMillis() / 1000L;
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm"));

        String itemString = "minecraft:paper[consumable={consume_seconds:2,animation:\"block\",sound:\"minecraft:block.beacon.activate\"}]";
        
        ItemStack item = Bukkit.getItemFactory().createItemStack(itemString);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(rollbackKey, PersistentDataType.BOOLEAN, true);
        pdc.set(xKey, PersistentDataType.INTEGER, x);
        pdc.set(yKey, PersistentDataType.INTEGER, y);
        pdc.set(zKey, PersistentDataType.INTEGER, z);
        pdc.set(timeKey, PersistentDataType.LONG, now);
        pdc.set(dimKey, PersistentDataType.STRING, dimension);

        meta.setDisplayName(ChatColor.RED + "ROLLBACK");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "X: " + x);
        lore.add(ChatColor.GRAY + "Y: " + y);
        lore.add(ChatColor.GRAY + "Z: " + z);
        lore.add(ChatColor.GRAY + "Recorded at: " + date);
        lore.add(ChatColor.GRAY + "Dimension: " + ChatColor.WHITE + dimension);
        meta.setLore(lore);

        FoodComponent food = meta.getFood();
        food.setNutrition(0);
        food.setSaturation(0);
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        
        item.setItemMeta(meta);
        return item;
    }

    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (CoreProtect.isEnabled() == false) {
            return null;
        }

        if (CoreProtect.APIVersion() < 11) {
            return null;
        }

        return CoreProtect;
    }  
}