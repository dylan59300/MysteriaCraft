# Resource Pack MysteriaCraft

Ce dossier contient les sources du resource pack qui donne leur texture aux 10 minerais
custom du module Custom Items. Un zip pret a l'emploi est fourni a la racine du depot :
`resourcepack-MysteriaCraft.zip`.

## Comment ca marche

Minecraft ne permet pas a un plugin serveur de changer l'apparence d'un item sans resource
pack : on utilise donc la technique standard `CustomModelData` sur un item de base
(`IRON_INGOT`). Le plugin donne des lingots de fer avec un `CustomModelData` different pour
chaque minerai custom (500001 a 500010) ; le resource pack redirige ces valeurs vers un
modele/texture different (`assets/minecraft/models/item/iron_ingot.json`).

Les joueurs qui n'ont pas le resource pack verront simplement... un lingot de fer normal
(avec le bon nom et lore). Le resource pack est donc recommande mais pas strictement requis
pour que le plugin fonctionne.

## Deployer le resource pack sur le serveur

1. Heberge `resourcepack-MysteriaCraft.zip` quelque part en HTTPS (ton propre serveur web,
   un CDN, ou un service comme https://mc-packs.net qui accepte les uploads).
2. Calcule son SHA-1 si ton hebergeur ne le fait pas automatiquement :
   `sha1sum resourcepack-MysteriaCraft.zip`
3. Dans `server.properties` :
   ```
   resource-pack=https://ton-domaine.com/chemin/resourcepack-MysteriaCraft.zip
   resource-pack-sha1=<le-sha1-calcule>
   require-resource-pack=false
   ```
   (mets `true` si tu veux forcer les joueurs a l'accepter pour rejoindre)

Alternative : un plugin comme "ResourcePackManager" ou une commande `/send-resource-pack`
peut envoyer le pack a la connexion sans toucher a `server.properties`, si tu preferes une
gestion plus dynamique (plusieurs packs, packs par permission, etc.).

## Modifier ou ajouter des textures

- Les textures sources (16x16, PNG avec transparence) sont dans
  `assets/mysteriacraft/textures/item/`.
- Chaque minerai a un modele dans `assets/mysteriacraft/models/item/` qui pointe vers sa
  texture.
- Le fichier `assets/minecraft/models/item/iron_ingot.json` fait le lien entre chaque
  `CustomModelData` (defini dans `custom_items.yml`, cote plugin) et son modele.

Pour ajouter un 11e minerai : ajoute sa texture, son modele, une entree `overrides` dans
`iron_ingot.json` avec un nouveau `custom_model_data` (ex: 500011), puis declare-le dans
`custom_items.yml` avec le meme `custom-model-data`.

Apres toute modification, re-zippe le contenu de ce dossier (le zip doit contenir
`pack.mcmeta` et `assets/` a sa racine, pas un sous-dossier).
