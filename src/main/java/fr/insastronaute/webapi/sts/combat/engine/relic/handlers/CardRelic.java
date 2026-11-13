package fr.insastronaute.webapi.sts.combat.engine.relic.handlers;

import fr.insastronaute.webapi.sts.combat.engine.relic.RelicHandler;
import org.springframework.stereotype.Component;

@Component
public class CardRelic implements RelicHandler {

    @Override
    public String id() {
        return "CardRelic";
    }
}
