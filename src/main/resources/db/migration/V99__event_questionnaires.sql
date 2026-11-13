-- Some events need to ask their registrants something: walking or running to the
-- picnic, vegetarian or not for the dinner. Questions belong to the event, and
-- answers to the registration, so unregistering takes its answers with it.

CREATE TABLE event_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    label VARCHAR(300) NOT NULL,
    -- SINGLE_CHOICE is countable and drives the chart; FREE_TEXT is read as a list.
    type VARCHAR(30) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT false,
    position INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_event_questions_type CHECK (type IN ('SINGLE_CHOICE', 'FREE_TEXT')),
    CONSTRAINT ck_event_questions_position CHECK (position >= 0)
);

CREATE INDEX idx_event_questions_event_position
    ON event_questions (event_id, position ASC);

CREATE TABLE event_question_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES event_questions(id) ON DELETE CASCADE,
    label VARCHAR(200) NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_event_question_options_position CHECK (position >= 0)
);

CREATE INDEX idx_event_question_options_question_position
    ON event_question_options (question_id, position ASC);

-- One answer per question per registration. The unique constraint is what makes
-- re-answering an update rather than a second row.
CREATE TABLE event_registration_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    registration_id UUID NOT NULL REFERENCES event_registrations(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES event_questions(id) ON DELETE CASCADE,
    -- Removing an option the admin no longer offers clears the answers that
    -- pointed at it rather than leaving them dangling.
    option_id UUID REFERENCES event_question_options(id) ON DELETE CASCADE,
    text_value VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_event_registration_answers UNIQUE (registration_id, question_id),
    -- An answer carries exactly one of the two shapes, never both, never neither.
    CONSTRAINT ck_event_registration_answers_shape CHECK (
        (option_id IS NOT NULL AND text_value IS NULL)
        OR (option_id IS NULL AND text_value IS NOT NULL)
    )
);

CREATE INDEX idx_event_registration_answers_question
    ON event_registration_answers (question_id, option_id);
