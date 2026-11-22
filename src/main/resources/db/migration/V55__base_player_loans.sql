SET LOCAL lock_timeout = '10s';
ALTER TABLE player_rating ADD COLUMN loan_from_team_id INT, ADD COLUMN loan_until DATE;
ALTER TABLE player_rating_archive ADD COLUMN loan_from_team_id INT, ADD COLUMN loan_until DATE;
