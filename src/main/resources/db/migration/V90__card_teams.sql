ALTER TABLE card_categories
    ADD COLUMN extension BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE card_teams (
    id UUID PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    creator_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_card_teams_lower_name ON card_teams (LOWER(name));

CREATE TABLE card_team_members (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES card_teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_card_team_members_user UNIQUE (user_id),
    CONSTRAINT ux_card_team_members_team_user UNIQUE (team_id, user_id)
);

CREATE INDEX ix_card_team_members_team ON card_team_members(team_id, joined_at);

CREATE TABLE card_team_join_requests (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES card_teams(id) ON DELETE CASCADE,
    requester_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    decided_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_card_team_join_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

CREATE UNIQUE INDEX ux_card_team_join_requests_pending_user
    ON card_team_join_requests(requester_user_id)
    WHERE status = 'PENDING';

CREATE TABLE card_team_invitations (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES card_teams(id) ON DELETE CASCADE,
    inviter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_card_team_invitations_distinct_users
        CHECK (inviter_user_id <> invited_user_id),
    CONSTRAINT ck_card_team_invitations_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);

CREATE UNIQUE INDEX ux_card_team_invitations_pending_user
    ON card_team_invitations(invited_user_id)
    WHERE status = 'PENDING';
