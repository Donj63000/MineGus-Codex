package org.example.village;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/**
 * Gère l’apparition (et le nettoyage) des PNJ, PNJ spécialisés et golems.
 */
public final class VillageEntityManager {

    public static final String TAG = "MINEGUS_VILLAGE_ID";
    private VillageEntityManager() {}

    private static final List<Villager.Profession> GENERIC = List.of(
            Villager.Profession.FARMER,
            Villager.Profession.TOOLSMITH,
            Villager.Profession.CLERIC,
            Villager.Profession.FLETCHER,
            Villager.Profession.LIBRARIAN,
            Villager.Profession.MASON
    );

    /* ========== SPAWN INITIAL ========== */
    public static void spawnInitial(Plugin plugin,
                                    Location center,
                                    int villageId,
                                    int ttlTicks) {

        World w = center.getWorld();
        Random R = new Random();

        // 6 PNJ génériques
        for (int i = 0; i < 6; i++) {
            Villager v = (Villager) w.spawnEntity(randAround(center), EntityType.VILLAGER);
            v.setProfession(GENERIC.get(R.nextInt(GENERIC.size())));
            tagEntity(v, plugin, villageId);
        }

        // PNJ spécialisés
        spawnNamed(w, plugin, randAround(center), "§eMineur",      Villager.Profession.TOOLSMITH, villageId);
        spawnNamed(w, plugin, randAround(center), "§6Bûcheron",    Villager.Profession.FLETCHER,  villageId);
        spawnNamed(w, plugin, randAround(center), "§aAgriculteur", Villager.Profession.FARMER,    villageId);
        spawnNamed(w, plugin, randAround(center), "§dÉleveur",     Villager.Profession.SHEPHERD,  villageId);

        // golems
        for (int i = 0; i < 2; i++) {
            IronGolem g = (IronGolem) w.spawnEntity(randAround(center), EntityType.IRON_GOLEM);
            g.setPlayerCreated(true);
            tagEntity(g, plugin, villageId);
        }

        // auto‑cleanup
        new BukkitRunnable() {
            @Override public void run() { cleanup(plugin, villageId); }
        }.runTaskLater(plugin, ttlTicks);
    }

    /* ========== PUBLIC UTIL ========== */
    public static void cleanup(Plugin p, int villageId) {
        for (World w : p.getServer().getWorlds()) {
            w.getEntities().stream()
                    .filter(e -> e.hasMetadata(TAG)
                            && e.getMetadata(TAG).get(0).asInt() == villageId)
                    .forEach(Entity::remove);
        }
    }

    public static void tagEntity(Entity e, Plugin p, int id) {
        e.setPersistent(true);
        e.setMetadata(TAG, new FixedMetadataValue(p, id));
    }

    /* ========== PRIVÉS ========== */
    private static Location randAround(Location c) {
        Random R = new Random();
        return c.clone().add(R.nextInt(10) - 5, 1, R.nextInt(10) - 5);
    }

    private static void spawnNamed(World w, Plugin p, Location l,
                                   String name, Villager.Profession prof, int id) {
        Villager v = (Villager) w.spawnEntity(l, EntityType.VILLAGER);
        v.setCustomName(name);
        v.setCustomNameVisible(true);
        v.setProfession(prof);
        tagEntity(v, p, id);
    }
}
