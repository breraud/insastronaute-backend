-- Création de la table des départements
CREATE TABLE departments (
                             id UUID PRIMARY KEY,
                             name VARCHAR(255) NOT NULL UNIQUE,
                             short_name VARCHAR(50) NOT NULL UNIQUE,
                             description TEXT,
                             keycloak_group_name VARCHAR(255) UNIQUE
);

-- Ajout de la colonne dans la table existante 'users'
ALTER TABLE users
    ADD COLUMN department_id UUID;

-- Création de la contrainte de clé étrangère
ALTER TABLE users
    ADD CONSTRAINT fk_user_department
        FOREIGN KEY (department_id)
            REFERENCES departments(id)
            ON DELETE SET NULL;

-- ... (Le code de création des tables vu précédemment) ...

-- Insertion des départements par défaut (Data Seeding)
INSERT INTO departments (id, name, short_name, keycloak_group_name) VALUES
                                                                        (gen_random_uuid(), 'Génie Civil', 'GC', 'GC'),
                                                                        (gen_random_uuid(), 'Chimie Génie Chimique (CFI)', 'CGC', 'CGC'),
                                                                        (gen_random_uuid(), 'Génie Énergétique (EP)', 'EP', 'EP'),
                                                                        (gen_random_uuid(), 'Génie Mathématique', 'GM', 'GM'),
                                                                        (gen_random_uuid(), 'Informatique et Techologie de l''information', 'ITI', 'ITI'),
                                                                        (gen_random_uuid(), 'Génie des procédés et génie des risques (MRIE)', 'GPGR', 'GPGR'),
                                                                        (gen_random_uuid(), 'Mécanique', 'MECA', 'MECA');