-- Erkek/kadın futbolu ayrımı (EA verisinde gender: 0 = Men's, 1 = Women's)
ALTER TABLE base_player ADD COLUMN gender SMALLINT NOT NULL DEFAULT 0 CHECK (gender IN (0, 1));
ALTER TABLE club ADD COLUMN gender SMALLINT CHECK (gender IN (0, 1));

CREATE INDEX ix_base_player_gender ON base_player(gender);
CREATE INDEX ix_base_player_dob ON base_player(dob);
CREATE INDEX ix_club_league ON club(league_id);
CREATE INDEX ix_rating_club ON player_rating(club_id);
CREATE INDEX ix_rating_pot_filter ON player_rating(game_version_id, overall);

-- Kariyer takma adı (kullanıcı birden fazla kariyeri ayırt edebilsin)
ALTER TABLE career ADD COLUMN name TEXT;
