package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;

@Component
public class RestHealRelicHandler implements RelicHandler {
    @Override
    public String id() {
        return "RestHealRelic";
    }

    /** « Vous regagnez 5 PV lorsque vous entrez dans un site de repos. » */
    @Override
    public RestOutcome onEnterRestSite(RestContext context) {
        return new RestOutcome(Math.min(context.maxHp(), context.currentHp() + 5), context.charges());
    }
}
