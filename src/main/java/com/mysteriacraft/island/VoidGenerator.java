package com.mysteriacraft.island;

import org.bukkit.generator.ChunkGenerator;

/**
 * Generateur de chunk entierement vide (aucun terrain, aucune structure, aucun mob a la
 * generation) pour le monde dedie aux Iles. Desactiver ces 6 etapes suffit sur Paper/Bukkit
 * moderne : pas besoin de surcharger generateChunkData().
 */
public class VoidGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
