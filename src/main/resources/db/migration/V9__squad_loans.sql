ALTER TABLE career_player_data ADD COLUMN loan_from_team_id INT;
ALTER TABLE user_player_state ADD COLUMN loaned_out BOOLEAN NOT NULL DEFAULT false;
