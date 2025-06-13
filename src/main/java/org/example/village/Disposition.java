package org.example.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Décide qui est construit où : grilles, routes, décorations, lampadaires, etc.
 * Ne contient AUCUN code de géométrie interne des bâtiments : tout est délégué
 * à {@link HouseBuilder}.
 */
public final class Disposition {

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

    /* ------------------------------------------------------------------ */
    /* MÉTHODES PUBLIQUES                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Place l’ensemble des constructions « statiques » (bâtiments, routes,
     * lampadaires, clôtures, etc.) dans les files de tâches {@code q}.
     */
    public void scheduleLayout(Location center,
                               int rows, int cols, int baseY, int villageId,
                               Queue<Runnable> q, TerrainManager.SetBlock sb) {

        int originX = center.getBlockX() - (cols - 1) * spacing / 2;
        int originZ = center.getBlockZ() - (rows - 1) * spacing / 2;
        Random rng  = new Random();

        /* --- grille de routes + bâtiments --- */
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {

                /* 1) route (axe N‑S) */
                int roadX = originX + c * spacing;
                for (int dz = -roadHalf; dz <= roadHalf; dz++) {
                    int z = originZ + r * spacing + dz;
                    HouseBuilder.paintRoad(q, roadPalette, roadX, baseY, z, sb);
                }

                /* 2) bâtiment */
                int lotX = originX + c * spacing - smallSize / 2;
                int lotZ = originZ + r * spacing - smallSize / 2;
                double roll = rng.nextDouble();

                if (roll < 0.60) { /* maison « small » */
                    q.addAll(HouseBuilder.buildHouse(
                            lotX, baseY + 1, lotZ,
                            smallSize, rng.nextInt(4), roadPalette,
                            roofPalette, wallLogs, wallPlanks, sb));
                } else if (roll < 0.85) { /* ferme (plantations) */
                    q.addAll(HouseBuilder.buildFarm(
                            lotX, baseY + 1, lotZ,
                            smallSize, cropSeeds, sb));
                } else {               /* enclos à animaux */
                    q.addAll(HouseBuilder.buildPen(
                            plugin, lotX, baseY + 1, lotZ,
                            smallSize, villageId, sb));
                }
            }
        }

        /* --- lampadaires sur chaque carrefour --- */
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x = originX + c * spacing;
                int z = originZ + r * spacing;
                q.addAll(HouseBuilder.buildLampPost(x, baseY + 1, z, sb));
            }
        }
    }
}
