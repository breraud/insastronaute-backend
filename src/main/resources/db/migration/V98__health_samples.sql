-- Se souvenir de l'etat du service, hors du processus qui le produit.
--
-- Micrometer tient ses compteurs en memoire : ils repartent de zero a chaque
-- deploiement, et il y en a eu une dizaine la nuit ou cette table a ete ecrite.
-- Impossible, donc, de repondre a « qu'est-ce qui s'est passe a deux heures » --
-- il fallait capturer un HAR pendant que la panne durait.
--
-- Une base de series temporelles ferait mieux. Elle demanderait deux cents
-- megaoctets de memoire vive sur une machine qui n'en a pas a donner. Postgres est
-- deja la, une ligne toutes les trente secondes pese trois cents kilooctets par
-- jour, et l'historique survit aux redemarrages : c'est le gain qu'on cherchait.
CREATE TABLE health_samples (
    id            UUID PRIMARY KEY,
    sampled_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Cumulatifs depuis le demarrage de la JVM, comme Micrometer les tient. La
    -- lecture en fait des taux par difference, et jette les differences negatives :
    -- une valeur qui recule signale un redemarrage, pas un trafic negatif.
    http_requests_total   BIGINT NOT NULL DEFAULT 0,
    http_errors_total     BIGINT NOT NULL DEFAULT 0,
    http_p95_millis       DOUBLE PRECISION,

    jvm_heap_used_bytes   BIGINT,
    jvm_heap_max_bytes    BIGINT,

    db_pool_active        INTEGER,
    db_pool_max           INTEGER,

    disk_free_bytes       BIGINT,
    disk_total_bytes      BIGINT,

    -- La file du gate, parce qu'elle a coute une nuit a comprendre faute de courbe.
    gate_queue_size       INTEGER
);

-- Toutes les lectures sont « les N dernieres heures », et la purge est « avant telle
-- date ». Le meme index sert aux deux.
CREATE INDEX idx_health_samples_sampled_at ON health_samples (sampled_at DESC);

COMMENT ON TABLE health_samples IS
    'Un releve toutes les 30 s de l''etat du service. Purge au-dela de 30 jours.';
