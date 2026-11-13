package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;

@Component
public class RestChargesRelicHandler implements RelicHandler {
    @Override
    public String id() {
        return "RestChargesRelic";
    }

    /** « Gagnez 1 charge en plus lorsque vous entrez dans un site de repos. » */
    @Override
    public RestOutcome onEnterRestSite(RestContext context) {
        return new RestOutcome(context.currentHp(), context.charges() + 1);
    }
}
