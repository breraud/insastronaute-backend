CREATE TABLE department_verification_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verifying_department_id UUID NOT NULL,
    verified_department_id UUID NOT NULL,
    CONSTRAINT fk_department_verification_links_verifying_department
        FOREIGN KEY (verifying_department_id)
        REFERENCES departments(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_department_verification_links_verified_department
        FOREIGN KEY (verified_department_id)
        REFERENCES departments(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_department_verification_links_distinct_departments
        CHECK (verifying_department_id <> verified_department_id),
    CONSTRAINT uk_department_verification_links_departments
        UNIQUE (verifying_department_id, verified_department_id)
);
