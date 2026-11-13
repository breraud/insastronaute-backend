-- Une etape declare par quel outil son secret doit arriver.
--
-- Jusqu'ici il n'existait qu'une facon de saisir, un champ texte, et le secret suffisait
-- a lui seul. L'etabli en presente plusieurs d'apparence egale : sans cette colonne, la
-- melodie d'une etape piano se validerait en la tapant au clavier, et un outil qu'aucune
-- etape n'attend ne pourrait pas etre refuse comme les autres.
--
-- KEYPAD par defaut : les etapes deja configurees attendent le clavier, c'est-a-dire ce
-- qu'elles faisaient avant que la colonne existe.
ALTER TABLE treasure_hunt_checkpoints
    ADD COLUMN input_tool VARCHAR(32) NOT NULL DEFAULT 'KEYPAD';

COMMENT ON COLUMN treasure_hunt_checkpoints.input_tool IS
    'Outil par lequel le secret doit arriver. Un secret juste soumis par un autre outil est refuse comme un secret faux.';
