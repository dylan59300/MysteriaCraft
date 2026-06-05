# Vanadia - Plugin RPG Minecraft Paper 1.20.1

Plugin RPG complet pour serveur Minecraft Paper 1.20.1.

## Fonctionnalites

### Systeme de Classes
- **Guerrier** : Tank avec haute defense et PV, competences Berserk et Mur de Bouclier
- **Mage** : DPS magique avec mana, competences Boule de Feu et Soin
- **Archer** : DPS a distance, competences Pluie de Fleches et Tir de Precision
- **Assassin** : DPS burst furtif, competences Coup dans le Dos et Ecran de Fumee

### Systeme de Niveaux & XP
- Gain d'XP en tuant des mobs, completant des quetes et donjons
- Formule d'XP progressive (base 100, multiplicateur x1.5)
- Bonus de stats par niveau

### Quetes
- 7 quetes variees (tuer des mobs, collecter, crafter)
- Progression en temps reel
- Recompenses en XP et items
- Maximum 3 quetes actives simultanement

### Mobs Custom
- Zombie Sorcier, Squelette Maudit, Araignee Venimeuse, Golem Ancien
- Spawn naturel aleatoire avec loots speciaux
- Stats augmentees et noms colores

### Donjons
- Crypte Obscure (Niv.5) et Foret Maudite (Niv.10)
- Vagues de mobs avec boss final
- Recompenses en XP et items rares

### Items Custom
- Armes de classe (Epee du Guerrier, Baton Arcanique, Arc de Precision, Dague de l'Ombre)
- Equipements speciaux (Bouclier Ancien, Amulette de Vie)
- Attributs RPG (degats, defense, PV bonus)

## Commandes

| Commande | Description |
|----------|-------------|
| `/class [nom]` | Choisir ou voir sa classe |
| `/skills [id]` | Voir ou utiliser ses competences |
| `/quest [list\|accept\|abandon\|info]` | Gerer ses quetes |
| `/profile [joueur]` | Voir le profil RPG |
| `/dungeon [nom\|leave]` | Entrer/quitter un donjon |
| `/vanadia [reload\|give\|setlevel]` | Commandes admin |

## Installation

1. Compiler avec Maven : `mvn package`
2. Copier `target/Vanadia-1.0.0.jar` dans le dossier `plugins/` du serveur
3. Redemarrer le serveur
4. Configurer dans `plugins/Vanadia/config.yml`

## Configuration

- `config.yml` : Parametres du plugin (classes, mobs, donjons, niveaux)
- `messages.yml` : Tous les messages (personnalisables)

## Prerequis

- Java 17+
- Paper 1.20.1
- Maven 3.6+ (pour compiler)
