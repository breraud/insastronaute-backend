-- Un troisieme mode de cadrage : le gabarit.
--
-- Mesure faite sur les 176 decoupes annotees du 31 aout : la segmentation automatique
-- trouve une boite utilisable (IoU >= 0.5) dans 20 a 26 % des cas selon sa version, quand
-- un rectangle FIXE, qui ne regarde pas l'image, en trouve 35 %. L'analyse d'image faisait
-- donc moins bien que pas d'analyse du tout : elle se verrouille sur le decor de la carte
-- plus souvent qu'elle ne trouve le code.
--
-- D'ou un gabarit affiche a l'ecran, dans lequel l'operateur aligne la bande. Ce n'est pas
-- un cadrage automatique -- aucune image n'est analysee -- ni un cadrage manuel -- aucun
-- geste de recadrage n'est fait. Les confondre avec l'un ou l'autre rendrait impossible la
-- seule question qui suivra : le gabarit a-t-il fait gagner du temps, et ses decoupes
-- valent-elles celles cadrees a la main ?

ALTER TABLE card_dataset_crops
    DROP CONSTRAINT ck_card_dataset_crops_framing;

ALTER TABLE card_dataset_crops
    ADD CONSTRAINT ck_card_dataset_crops_framing
        CHECK (framing IN ('AUTOMATIC', 'MANUAL', 'GUIDE'));
