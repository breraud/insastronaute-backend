-- Création de la table principale des défis
CREATE TABLE challenges (
                            id UUID PRIMARY KEY,
                            title VARCHAR(255) NOT NULL,
                            description TEXT,
                            points_reward INT NOT NULL,
                            category VARCHAR(50),
                            start_date TIMESTAMP WITH TIME ZONE,
                            end_date TIMESTAMP WITH TIME ZONE,
                            global_quota INT,

    -- Nouvelles mécaniques
                            validation_mode VARCHAR(20) NOT NULL, -- 'ADMIN' ou 'AUTO'
                            max_completions_per_user INT DEFAULT 1,
                            is_group_challenge BOOLEAN DEFAULT FALSE,
                            max_group_size INT,

                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Table de liaison : Ciblage des départements
-- Si un défi n'a aucune ligne ici, il est pour TOUT LE MONDE.
-- S'il a des lignes, seuls les départements listés le verront.
CREATE TABLE challenge_target_departments (
                                              challenge_id UUID NOT NULL,
                                              department_id UUID NOT NULL,
                                              PRIMARY KEY (challenge_id, department_id),
                                              CONSTRAINT fk_challenge_target_chal FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
                                              CONSTRAINT fk_challenge_target_dep FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
);