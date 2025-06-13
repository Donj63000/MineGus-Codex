package org.example.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Classe utilitaire (toutes les méthodes sont statiques).
 */
public final class Disposition {

    private Disposition() {}

    /** Point d\u2019entr\u00e9e appel\u00e9 par {@link org.example.Village}. */
    public static void buildVillage(JavaPlugin plugin,
                                    Location center,
                                    int rows, int cols, int baseY,
                                    int smallSize, int bigSize, int spacing, int roadHalf,
                                    List<Material> wallLogs, List<Material> wallPlanks,
                                    List<Material> roofPalette,
                                    List<Material> roadPalette, List<Material> cropSeeds,
                                    Queue<Runnable> tasks,
                                    TerrainManager.SetBlock sb,
                                    int villageId) {

        scheduleLayout(plugin, center, rows, cols, baseY, villageId,
                       smallSize, spacing, roadHalf,
                       roadPalette, roofPalette, wallLogs, wallPlanks, cropSeeds,
                       tasks, sb);
    }

    /* ------------------------------------------------------------------ */
    /*  IMPL\u00c9MENTATION D\u00c9TAILL\u00c9E                                          */
    /* ------------------------------------------------------------------ */
    private static void scheduleLayout(JavaPlugin plugin,
                                       Location center,
                                       int rows, int cols, int baseY, int villageId,
                                       int smallSize, int spacing, int roadHalf,
                                       List<Material> roadPalette, List<Material> roofPalette,
                                       List<Material> wallLogs, List<Material> wallPlanks,
                                       List<Material> cropSeeds,
                                       Queue<Runnable> q, TerrainManager.SetBlock sb) {

        int originX = center.getBlockX() - (cols - 1) * spacing / 2;
        int originZ = center.getBlockZ() - (rows - 1) * spacing / 2;
        Random rng  = new Random();

        /* --- grille routes + lots --- */
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {

                /* 1) bande de route (axe N\u2011S) */
                int roadX = originX + c * spacing;
                for (int dz = -roadHalf; dz <= roadHalf; dz++) {
                    int z = originZ + r * spacing + dz;
                    HouseBuilder.paintRoad(q, roadPalette, roadX, baseY, z, sb);
                }

                /* 2) choix b\u00e2timent */
                int lotX = originX + c * spacing - smallSize / 2;
                int lotZ = originZ + r * spacing - smallSize / 2;
                double roll = rng.nextDouble();

                if (roll < 0.60) {
                    q.addAll(HouseBuilder.buildHouse(
                             plugin,
                             new Location(center.getWorld(), lotX, baseY + 1, lotZ),
                             smallSize, rng.nextInt(4),
                             wallLogs, wallPlanks, roofPalette,
                             sb, rng, villageId));
                } else if (roll < 0.85) {
                    q.addAll(HouseBuilder.buildFarm(
                             new Location(center.getWorld(), lotX, baseY + 1, lotZ),
                             cropSeeds, sb));
                } else {
                    q.addAll(HouseBuilder.buildPen(
                             plugin,
                             new Location(center.getWorld(), lotX, baseY + 1, lotZ),
                             villageId, sb));
                }
            }
        }

        /* --- lampadaires aux carrefours --- */
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = originX + c * spacing;
                int z = originZ + r * spacing;
                q.addAll(HouseBuilder.buildLampPost(x, baseY + 1, z, sb));
            }
        }
    }
}
