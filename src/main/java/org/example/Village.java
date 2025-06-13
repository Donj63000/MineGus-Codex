package org.example;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.example.village.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commande /village – génère un village complet en s’appuyant sur les
 * sous‑modules org.example.village.* (terrain, dispositions, murs, entités).
 */
public final class Village implements CommandExecutor, TabCompleter {

    /* ========= constantes ========= */
    private static final int DFLT_ROWS = 4, DFLT_COLS = 5;
    private static final String CFG    = "village.yml";

    /* ========= état plugin ========= */
    private final JavaPlugin plugin;
    private YamlConfiguration cfg;

    private int smallSize, bigSize, spacing, roadHalf, batch, entityTTL;
    private Material wallMat;
    private List<Material> roadPalette, roofPalette, wallLogs, wallPlanks, cropSeeds;

    /* ========= undo / tp ========= */
    private record Snap(BlockState state) {}
    private final Map<Integer, List<Snap>> undoMap = new HashMap<>();
    private final Map<Integer, Location>   tpMap   = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    /* ------------------------------------------------------------ */
    public Village(JavaPlugin plugin) {
        this.plugin = plugin;

        Objects.requireNonNull(plugin.getCommand("village")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("village")).setTabCompleter(this);

        ensureConfig();
        loadConfig();
    }

    /* ========= CONFIG ========= */
    private void ensureConfig() {
        File f = new File(plugin.getDataFolder(), CFG);
        if (f.exists()) return;
        YamlConfiguration def = new YamlConfiguration();
        def.set("house.small", 7);
        def.set("house.big", 9);
        def.set("grid.spacing", 16);
        def.set("road.halfWidth", 3);
        def.set("performance.batch", 250);
        def.set("performance.entityTTL", 20 * 60 * 10);
        def.set("materials.wall", "STONE_BRICKS");
        def.set("materials.road",  List.of("DIRT_PATH","GRAVEL","COARSE_DIRT"));
        def.set("materials.roof",  List.of("OAK_STAIRS","SPRUCE_STAIRS","BAMBOO_MOSAIC_STAIRS"));
        def.set("materials.logs",  List.of("OAK_LOG","SPRUCE_LOG","BIRCH_LOG"));
        def.set("materials.planks",List.of("OAK_PLANKS","SPRUCE_PLANKS","BIRCH_PLANKS"));
        def.set("farmland.crops", List.of("WHEAT_SEEDS","CARROT","POTATO"));
        try { def.save(f); } catch (IOException ignored) {}
    }
    private void loadConfig() {
        cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), CFG));

        smallSize = cfg.getInt("house.small", 7);
        bigSize   = cfg.getInt("house.big", 9);
        spacing   = cfg.getInt("grid.spacing", 16);
        roadHalf  = cfg.getInt("road.halfWidth", 3);
        batch     = cfg.getInt("performance.batch", 250);
        entityTTL = cfg.getInt("performance.entityTTL", 20 * 60 * 10);

        wallMat     = Material.matchMaterial(cfg.getString("materials.wall", "STONE_BRICKS"));
        roadPalette = mats("materials.road",
                Material.DIRT_PATH, Material.GRAVEL, Material.COARSE_DIRT);
        roofPalette = mats("materials.roof",
                Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BAMBOO_MOSAIC_STAIRS);
        wallLogs    = mats("materials.logs",
                Material.OAK_LOG,   Material.SPRUCE_LOG,   Material.BIRCH_LOG);
        wallPlanks  = mats("materials.planks",
                Material.OAK_PLANKS,Material.SPRUCE_PLANKS,Material.BIRCH_PLANKS);
        cropSeeds   = mats("farmland.crops",
                Material.WHEAT_SEEDS,Material.CARROT,Material.POTATO);
    }
    private List<Material> mats(String path, Material... def) {
        List<String> raw = cfg.getStringList(path);
        if (raw == null || raw.isEmpty()) return List.of(def);
        List<Material> list = new ArrayList<>();
        for (String s : raw) {
            Material m = Material.matchMaterial(s);
            if (m != null) list.add(m);
        }
        return list.isEmpty() ? List.of(def) : list;
    }

    /* ========= COMMANDES ========= */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Joueur uniquement.");
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "undo"   -> { undo(sender,args);  return true; }
                case "tp"     -> { tp(sender,args);    return true; }
                case "list"   -> { list(sender);       return true; }
                case "reload" -> {
                    plugin.reloadConfig();
                    loadConfig();
                    sender.sendMessage("§eVillage.yml rechargé.");
                    return true;
                }
            }
        }
        int rows = DFLT_ROWS, cols = DFLT_COLS;
        if (args.length == 1 && args[0].matches("\\d+x\\d+")) {
            String[] s = args[0].split("x");
            rows = Integer.parseInt(s[0]);
            cols = Integer.parseInt(s[1]);
        }
        generateVillage(p, rows, cols, sender);
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender s, Command c,
                                      String alias, String[] a) {
        return a.length == 1
                ? List.of("undo","tp","list","reload","4x6")
                : List.of();
    }

    /* ========= undo / tp / list ========= */
    private void undo(CommandSender s, String[] a) {
        int id = (a.length >= 2) ? Integer.parseInt(a[1]) : counter.get()-1;
        List<Snap> snaps = undoMap.remove(id);
        if (snaps == null) { s.sendMessage("§cID inconnu"); return; }

        new BukkitRunnable() {
            final Iterator<Snap> it = snaps.iterator();
            @Override public void run() {
                for (int i = 0; i < batch && it.hasNext(); i++) it.next().state.update(true,false);
                if (!it.hasNext()) { cancel(); s.sendMessage("§eVillage " + id + " annulé."); }
            }
        }.runTaskTimer(plugin, 1, 1);

        VillageEntityManager.cleanup(plugin, id);
        tpMap.remove(id);
    }
    private void tp(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) { s.sendMessage("/village tp <id>"); return; }
        Location l = tpMap.get(Integer.parseInt(a[1]));
        if (l == null) { s.sendMessage("§cID inconnu"); return; }
        p.teleport(l.clone().add(0.5,1,0.5));
    }
    private void list(CommandSender s) {
        s.sendMessage("§6Villages:");
        tpMap.forEach((id,l)-> s.sendMessage("§e#"+id+" §7"+l.getWorld().getName()+" "
                +l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ()));
    }

    /* =========  GÉNÉRATION  ========= */
    private void generateVillage(Player p, int rows, int cols, CommandSender fb) {

        World w = p.getWorld();
        Location center = p.getLocation().getBlock().getLocation();

        /* --- hauteur de référence (utiliser la surcharge int,int) --- */
        int baseY = w.getHighestBlockYAt(center.getBlockX(), center.getBlockZ());
        center.setY(baseY);

        int rx = ((cols - 1) * spacing + bigSize) / 2;
        int rz = ((rows - 1) * spacing + bigSize) / 2;

        /* aucune vérification bloquante : on terrassera la zone quoi qu’il arrive */

        int id = counter.getAndIncrement();
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<Snap> snapshots  = new ArrayList<>();
        TerrainManager.SetBlock sb = (x,y,z,m) -> {
            Block b = w.getBlockAt(x,y,z);
            snapshots.add(new Snap(b.getState()));
            b.setType(m, false);
        };

        /* 1) terrain */
        TerrainManager.prepareGround(center, rx, rz, baseY, tasks, sb);

        /* 2) structures (puits, maisons, routes, enclos, champs, lampadaires) */
        Disposition.buildVillage(
                plugin, center, rows, cols, baseY,
                smallSize, bigSize, spacing, roadHalf,
                wallLogs, wallPlanks, roofPalette,
                roadPalette, cropSeeds,
                tasks, sb, id);

        /* 3) murailles */
        WallBuilder.build(center, rx, rz, baseY, wallMat, tasks, sb);

        /* 4) entités vivantes */
        tasks.add(() -> VillageEntityManager.spawnInitial(
                plugin, center, id, entityTTL));

        /* undo / tp */
        undoMap.put(id, snapshots);
        tpMap.put(id, center.clone().add(0,1,rz+2));

        /* exécution non bloquante */
        new BukkitRunnable() {
            @Override public void run() {
                int c = 0;
                while (!tasks.isEmpty() && c < batch) {
                    tasks.poll().run();
                    c++;
                }
                if (tasks.isEmpty()) cancel();
            }
        }.runTaskTimer(plugin, 1, 1);

        fb.sendMessage("§aVillage #" + id + " en génération…");
    }
}
