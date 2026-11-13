package fr.insastronaute.webapi.push.service;

import com.interaso.webpush.VapidKeys;
import fr.insastronaute.webapi.push.dto.PushPublicKeyDto;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * La cle publique, sous la forme que le navigateur attend.
 *
 * <p>Ce n'est pas la cle X509 : {@code applicationServerKey} veut les 65 octets bruts du
 * point de courbe, en base64url sans remplissage. Servir l'autre forme donne un
 * abonnement que le navigateur accepte et que le service de push refusera ensuite,
 * silencieusement.</p>
 *
 * <p>Le bean existe meme push eteint : le front demande la cle avant tout, et une
 * reponse {@code enabled: false} lui evite de demander une permission qu'un refus
 * rendrait definitive.</p>
 */
@Component
public class PushKeyProvider {

    private final PushProperties properties;

    public PushKeyProvider(PushProperties properties) {
        this.properties = properties;
    }

    public PushPublicKeyDto publicKey() {
        if (!properties.isConfigured()) {
            return new PushPublicKeyDto(false, null);
        }
        VapidKeys keys = VapidKeys.create(properties.getVapidPublicKey(), properties.getVapidPrivateKey());
        return new PushPublicKeyDto(true,
                Base64.getUrlEncoder().withoutPadding().encodeToString(keys.getApplicationServerKey()));
    }
}
