-- Separer la parution de l'ouverture.
--
-- Jusqu'ici un defi apparaissait dans la liste d'un joueur pile pendant sa fenetre de
-- soumission, et nulle part ailleurs. On ne pouvait donc ni annoncer ce qui arrivait,
-- ni laisser consulter ce qui venait de se terminer : la meme date decidait des deux.
--
-- published_at ne decide que de l'affichage. start_date et end_date gardent leur role
-- unique, borner la soumission.
--
-- Facultative, et nulle veut dire « paru ». Les defis deja en base restent donc visibles
-- exactement comme avant, et un defi cree sans y penser parait tout de suite : c'est le
-- comportement qu'on avait, il reste le defaut.
ALTER TABLE challenges ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN challenges.published_at IS
    'Date de parution : le defi n''apparait pas avant. NULL = paru. Sans effet sur la soumission, bornee par start_date et end_date.';
