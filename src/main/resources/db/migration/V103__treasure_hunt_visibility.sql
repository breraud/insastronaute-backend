-- Activation is volatile: editing any checkpoint content switches the hunt off
-- again, so it cannot double as "should players see this at all". Visibility is
-- its own switch, and it survives every content edit.
ALTER TABLE treasure_hunt_settings
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
