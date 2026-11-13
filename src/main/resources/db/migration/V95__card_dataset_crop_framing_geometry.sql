-- Garder la question, pas seulement la reponse.
--
-- Une decoupe cadree a la main est exactement un echec de la segmentation, corrige par un
-- humain. C'est l'exemple le plus utile qui existe pour reparer -- ou remplacer -- l'heuristique
-- de cadrage : rien que des cas durs, avec la verite terrain. Jusqu'ici on stockait le contenu
-- de la boite et la taille de la photo, jamais la position de la boite ni la photo elle-meme.
-- La reponse de l'operateur etait conservee, la question jetee, et les 45 % de cadrages manuels
-- d'une seance partaient avec.
--
-- Les colonnes ci-dessous sont toutes facultatives : les outils deja en place n'envoient rien de
-- tout cela et doivent continuer de fonctionner. Une decoupe sans region est une decoupe qu'on
-- ne pourra pas utiliser pour la detection, pas une decoupe invalide.
--
-- La region est exprimee dans les coordonnees de l'image *stockee* -- celle que designe
-- frame_object_key -- et non dans celles du flux ni du capteur. Un seul repere, celui de
-- l'image qu'on garde a cote : c'est la seule facon d'eviter qu'une mise a l'echelle oubliee
-- decale chaque boite du jeu sans que rien ne le signale.

ALTER TABLE card_dataset_crops
    ADD COLUMN frame_object_key   varchar(512),
    ADD COLUMN frame_content_type varchar(100),
    ADD COLUMN frame_byte_size    bigint,
    ADD COLUMN frame_width_px     integer,
    ADD COLUMN frame_height_px    integer,
    ADD COLUMN region_x_px        integer,
    ADD COLUMN region_y_px        integer,
    ADD COLUMN region_width_px    integer,
    ADD COLUMN region_height_px   integer,
    ADD COLUMN region_angle_deg   double precision;

-- Une region a quatre nombres ou n'en a aucun. Trois sur quatre ne decrivent pas une boite, et
-- completer le quatrieme par zero fabriquerait une verite terrain fausse que l'entrainement
-- prendrait au serieux. Le service le refuse deja ; la base le refuse aussi, parce qu'un import
-- direct ou un script de reprise ne passent pas par le service.
ALTER TABLE card_dataset_crops
    ADD CONSTRAINT ck_card_dataset_crops_region_complete
        CHECK (
            (region_x_px IS NULL AND region_y_px IS NULL
                AND region_width_px IS NULL AND region_height_px IS NULL)
            OR (region_x_px IS NOT NULL AND region_y_px IS NOT NULL
                AND region_width_px IS NOT NULL AND region_height_px IS NOT NULL
                AND region_width_px > 0 AND region_height_px > 0)
        );
