package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;

@Component
public class FieldRationsRelicHandler implements RelicHandler {
    @Override
    public String id() {
        return "FieldRationsRelic";
    }

    /** « Vous regagnez 4 PV lorsque vous entrez dans un site de repos. » */
    @Override
    public RestOutcome onEnterRestSite(RestContext context) {
        return new RestOutcome(Math.min(context.maxHp(), context.currentHp() + 4), context.charges());
    }
}
