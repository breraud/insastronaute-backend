-- ITI passe au bleu clair franc.
--
-- Son bleu et celui de GM se ressemblaient trop : cote a cote dans un
-- classement, un liseré de six pixels ne separe pas deux cyans voisins. La
-- planete d'ITI a change dans le meme mouvement — silhouette a anneau, teinte
-- claire — et la couleur servie par l'API doit dire la meme chose que
-- l'application, sinon le departement change de couleur d'un ecran a l'autre.
UPDATE departments SET color = '#38BDF8' WHERE UPPER(short_name) = 'ITI';
