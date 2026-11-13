package fr.insastronaute.webapi.sts.combat.model;

import java.util.List;

public record CardPiles(
        List<CardInstance> draw,
        List<CardInstance> hand,
        List<CardInstance> discard,
        List<CardInstance> exhaust
) {

    public CardPiles {
        draw = List.copyOf(draw);
        hand = List.copyOf(hand);
        discard = List.copyOf(discard);
        exhaust = List.copyOf(exhaust);
    }

    public CardPiles withoutTemporaryModifiers() {
        return new CardPiles(
                draw.stream().map(CardInstance::withoutTemporaryModifiers).toList(),
                hand.stream().map(CardInstance::withoutTemporaryModifiers).toList(),
                discard.stream().map(CardInstance::withoutTemporaryModifiers).toList(),
                exhaust.stream().map(CardInstance::withoutTemporaryModifiers).toList());
    }
}
