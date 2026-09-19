# MysteriaCraft
Mod Minecraft magique pour Minecraft 1.20.1 (Forge).

## Prérequis
- JDK 17
- Aucune installation de Gradle nécessaire : utilisez le wrapper (`./gradlew`)

## Lancer le mod en jeu (client de dev)
```bash
./gradlew runClient
```

## Compiler le mod (génère le .jar dans `build/libs/`)
```bash
./gradlew build
```

## Structure
- `src/main/java/com/mysteriacraft/` : code source du mod
- `src/main/resources/META-INF/mods.toml` : métadonnées du mod
- `biomes/` : notes/brouillons de biomes (à intégrer plus tard dans `src/main/resources`)

