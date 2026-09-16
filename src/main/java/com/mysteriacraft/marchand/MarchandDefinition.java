package com.mysteriacraft.marchand;

import java.util.List;

/**
 * Definition immuable d'un type de PNJ Marchand (id, item d'invocation, monnaie, offres),
 * charge depuis marchand.yml. Plusieurs marchands peuvent coexister (ex: "marchand"/"forgeron"),
 * chacun avec son propre item custom d'invocation et son propre pool d'offres.
 *
 * @param offresActivesParJour si > 0, seules N offres (tirage deterministe, identique pour tous
 *                             les joueurs, renouvele chaque jour) parmi "offres" sont proposees.
 *                             0 = toutes les offres sont toujours proposees.
 * @param fideliteSeuil nombre d'echanges (tous historique confondu, avec CE marchand) pour
 *                       chaque palier de reduction fidelite. 0 = fidelite desactivee.
 * @param fideliteReductionPourcent reduction (%) accordee par palier de fidelite atteint.
 * @param fideliteReductionMaxPourcent plafond (%) de la reduction fidelite cumulee.
 */
public record MarchandDefinition(
        String id,
        String oeufItemId,
        String pieceItemId,
        String npcName,
        int offresActivesParJour,
        int fideliteSeuil,
        double fideliteReductionPourcent,
        double fideliteReductionMaxPourcent,
        List<MarchandOffer> offres,
        List<MarchandRachat> rachats
) {

    public MarchandOffer getOffer(String offerId) {
        for (MarchandOffer offer : offres) {
            if (offer.id().equalsIgnoreCase(offerId)) {
                return offer;
            }
        }
        return null;
    }

    public MarchandRachat getRachat(String rachatId) {
        for (MarchandRachat rachat : rachats) {
            if (rachat.id().equalsIgnoreCase(rachatId)) {
                return rachat;
            }
        }
        return null;
    }
}
