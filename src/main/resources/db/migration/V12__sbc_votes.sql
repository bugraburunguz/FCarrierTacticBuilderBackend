CREATE TABLE sbc_vote (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    set_id BIGINT NOT NULL,
    vote SMALLINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sbc_vote_user_set UNIQUE (user_id, set_id),
    CONSTRAINT ck_sbc_vote_value CHECK (vote IN (-1, 1))
);

CREATE INDEX idx_sbc_vote_set ON sbc_vote (set_id);
