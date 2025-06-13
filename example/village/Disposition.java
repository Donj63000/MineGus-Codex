package org.example.village;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * Décide qui est construit où : grilles, routes, décorations, lampadaires, etc.
 * Ne contient AUCUN code de géométrie interne des bâtiments : tout est délégué
 * à {@link HouseBuilder}.
 */
public final class Disposition {

    private Disposition() { /* utilitaire : pas d’instanciation */ }

    /* ===================================================== */
    /* PUBLIC API                                            */
    /* ===================================================== */

    /**
     * Génère un village complet (hors terrassement et murs périphériques).
     */
    public static void buildVillage(Plugin plugin,
                                    Location center,
                                    int rows, int cols, int baseY,
                                    int small, int big, int spacing, int roadHalf,
                                    List<Material> logs,   List<Material> planks,
                                    List<Material> roofs,  List<Material> roadPalette,
                                    List<Material> crops,
                                    Queue<Runnable> q,
                                    TerrainManager.SetBlock sb,
                                    int villageId) {

        Random rng = new Random();

        /* --- puits + cloche au centre --- */
        q.addAll(HouseBuilder.buildWell(center, sb));
        q.addAll(HouseBuilder.buildBell(center.clone().add(1, 1, 0), sb));

        /* --- origine (coin nord‑ouest) --- */
        int originX = center.getBlockX() - ((cols - 1) * spacing) / 2;
        int originZ = center.getBlockZ() - ((rows - 1) * spacing) / 2;

        /* --- boucle principale : chaque case du quadrillage --- */
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {

                boolean bigHouse = rng.nextBoolean();
                int size = bigHouse ? big : small;
                int hx = originX + c * spacing;
                int hz = originZ + r * spacing;

                /* façade tournée vers la route la plus proche */
                int rot =
                        (r == 0)               ? 180 :
                                (r == rows - 1)        ?   0 :
                                        (c < cols / 2)         ?  90 : 270;

                Location base = new Location(center.getWorld(), hx, baseY, hz);

                /* maison */
                q.addAll(HouseBuilder.buildHouse(plugin, base, size, rot,
                        logs, planks, roofs, sb, rng, villageId));

                /* route de la maison → centre */
                q.addAll(HouseBuilder.buildRoad(
                        center.getBlockX(), center.getBlockZ(),
                        hx, hz, baseY, roadHalf, roadPalette, sb));

                /* décor latéral (champ ou enclos) */
                Location side = base.clone().add(
                        rot == 90  ? -size - 4 : rot == 270 ?  size + 4 : 0,
                        0,
                        rot ==   0 ? -size - 4 : rot == 180 ?  size + 4 : 0);

                double roll = rng.nextDouble();
                if (roll < 0.25) {                 // 25 % champs
                    q.addAll(HouseBuilder.buildFarm(side, crops, sb));
                } else if (roll < 0.40) {          // 15 % enclos
                    q.addAll(HouseBuilder.buildPen(plugin, side, villageId, sb));
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
