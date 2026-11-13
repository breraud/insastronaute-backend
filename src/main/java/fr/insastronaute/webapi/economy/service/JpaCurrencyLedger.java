package fr.insastronaute.webapi.economy.service;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.economy.domain.CurrencyMovement;
import fr.insastronaute.webapi.economy.domain.CurrencySource;
import fr.insastronaute.webapi.economy.repository.CurrencyMovementRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ecrit le registre dans la meme transaction que le mouvement.
 *
 * <p>Si l'ecriture echoue, le mouvement echoue avec elle : un gain de tokens
 * absent du registre fausserait exactement les chiffres qu'on consulte pour
 * reperer une anomalie.</p>
 */
@Service
public class JpaCurrencyLedger implements CurrencyLedger {

    private final CurrencyMovementRepository repository;

    public JpaCurrencyLedger(CurrencyMovementRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(AppUser user, CurrencySource source, long tokenDelta, long eventPointsDelta, UUID referenceId) {
        if (user == null || user.getId() == null || (tokenDelta == 0 && eventPointsDelta == 0)) {
            return;
        }
        repository.save(new CurrencyMovement(
                user.getId(), source, tokenDelta, eventPointsDelta, referenceId, TimeTravelContext.instantNow()));
    }
}
