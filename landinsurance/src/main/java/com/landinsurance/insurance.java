package com.landinsurance;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class insurance extends JavaPlugin implements CommandExecutor, Listener {

    @Override
    public void onEnable() {
        if (this.getCommand("insurance") != null) {
            this.getCommand("insurance").setExecutor(this);
        }
        // register the event listener
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        org.bukkit.entity.Item itemDrop = event.getItemDrop();
        ItemStack droppedItem = itemDrop.getItemStack();
        
        if (droppedItem.getType() == Material.PAPER && droppedItem.hasItemMeta()) {
            ItemMeta meta = droppedItem.getItemMeta();
            String metaString = meta.getAsString(); 
            if (metaString.contains("rollback")) {
                Player player = event.getPlayer();
                itemDrop.remove();
                String selectorString = "minecraft:nether_star[item_name='Selector',lore=['Hold Use To Record A Rollback Center'],use_cooldown={seconds:5},food={nutrition:0,saturation:0,can_always_eat:true},consumable={consume_seconds:1,animation:\"block\",sound:\"minecraft:entity.illusioner.cast_spell\"},custom_data={selector:true}]";
                ItemStack selectorItem = Bukkit.getItemFactory().createItemStack(selectorString);
                player.getInventory().addItem(selectorItem);
                // parse the location and dimension from the item's custom data
                java.util.regex.Matcher xMatch = java.util.regex.Pattern.compile("x:(-?\\d+)").matcher(metaString);
                java.util.regex.Matcher yMatch = java.util.regex.Pattern.compile("y:(-?\\d+)").matcher(metaString);
                java.util.regex.Matcher zMatch = java.util.regex.Pattern.compile("z:(-?\\d+)").matcher(metaString);
                java.util.regex.Matcher dimMatch = java.util.regex.Pattern.compile("dimension:(?:\"|')?([^\"',}]+)(?:\"|')?").matcher(metaString);

                if (xMatch.find() && yMatch.find() && zMatch.find() && dimMatch.find()) {
                    int x = Integer.parseInt(xMatch.group(1));
                    int y = Integer.parseInt(yMatch.group(1));
                    int z = Integer.parseInt(zMatch.group(1));
                    String dimension = dimMatch.group(1);

                    org.bukkit.World targetWorld = Bukkit.getWorld(dimension);
                    if (targetWorld != null) {
                        Location targetLoc = new Location(targetWorld, x, y, z);
                            
                        // remove the selected markers at the parsed location
                        for (org.bukkit.entity.Entity entity : targetWorld.getNearbyEntities(targetLoc, 1.5, 1.5, 1.5)) {
                            if (entity.getType() == org.bukkit.entity.EntityType.MARKER && 
                                entity.getScoreboardTags().contains("selected")) {
                                entity.remove();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) return false;

        // stamp: record the position, time and dimension on an item
        if (args[0].equalsIgnoreCase("stamp")) {
            player.getInventory().addItem(createRollbackPaper(
                player.getLocation().getBlockX(), 
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(),
                player.getWorld().getName()
            ));
            return true;
        }

        // summon: execute the rollback via co api
        if (args[0].equalsIgnoreCase("summon") && args.length >= 6) {
            int x = Integer.parseInt(args[1]);
            int y = Integer.parseInt(args[2]);
            int z = Integer.parseInt(args[3]);
            long timestamp = Long.parseLong(args[4]);
            String worldName = args[5];

            org.bukkit.World targetWorld = Bukkit.getWorld(worldName);
            Location loc = new Location(targetWorld, x, y, z);
            CoreProtectAPI cpAPI = getCoreProtect();
            long diff = (System.currentTimeMillis() / 1000L) - timestamp;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                List<Integer> actions = Arrays.asList(0, 1);
                List<Object> excludedBlocks = new ArrayList<>();
                excludedBlocks.add(Material.DIAMOND_BLOCK);
                excludedBlocks.add(Material.NETHERITE_BLOCK);
                excludedBlocks.add(Material.HEAVY_CORE);
                excludedBlocks.add(Material.CONDUIT);
                cpAPI.performRollback(
                    (int) diff,  // Time in seconds
                    null,        // Restrict users
                    null,        // Exclude users
                    null,        // Restrict blocks
                    excludedBlocks, // Exclude blocks
                    actions,     // Action list
                    100,         // Radius (r:100)
                    loc          // Origin location
                );

                // remove marker after operation
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (org.bukkit.entity.Entity entity : loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5)) {
                    if (entity.getType() == org.bukkit.entity.EntityType.MARKER && 
                        entity.getScoreboardTags().contains("selected")) {
                        entity.remove();
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Rollback performed successfully");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
            }, 1L);});
        }
        return false;
    }
    
    // give player the rollback paper
    private ItemStack createRollbackPaper(int x, int y, int z, String dimension) {

        long now = System.currentTimeMillis() / 1000L;
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm"));

        // setting item properties
        String itemString = String.format(
            "minecraft:paper[consumable={consume_seconds:3,animation:\"block\",sound:\"minecraft:entity.illusioner.cast_spell\"},custom_data={rollback:true,x:%d,y:%d,z:%d,timestamp:%dL,dimension:\"%s\"}]",
            x, y, z, now, dimension
        );
        ItemStack item = Bukkit.getItemFactory().createItemStack(itemString);
        ItemMeta meta = item.getItemMeta();

        // check for null meta before attempting to use it
        if (meta == null) return item;
        
        FoodComponent food = meta.getFood();

        // visual properties
        meta.setDisplayName(ChatColor.RED + "ROLLBACK");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "X: " + x);
        lore.add(ChatColor.GRAY + "Y: " + y);
        lore.add(ChatColor.GRAY + "Z: " + z);
        lore.add(ChatColor.GRAY + "Recorded at: " + date);
        lore.add(ChatColor.GRAY + "Dimension: " + ChatColor.WHITE + dimension);
        meta.setLore(lore);
        
        // set food component: nutrition 0, saturation 0, can_always_eat: true

        food.setNutrition(0);
        food.setSaturation(0);
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        item.setItemMeta(meta);
        return item;
    }
    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        // Check that CoreProtect is loaded
        if (plugin == null || !(plugin instanceof CoreProtect)) {
            return null;
        }

        // Check that the API is enabled
        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (CoreProtect.isEnabled() == false) {
            return null;
        }

        // Check that a compatible version of the API is loaded
        if (CoreProtect.APIVersion() < 11) {
            return null;
        }

        return CoreProtect;
    }  
}