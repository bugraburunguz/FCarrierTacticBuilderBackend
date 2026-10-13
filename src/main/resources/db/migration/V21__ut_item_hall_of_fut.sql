-- BE-BUG-08: Hall of FUT kart tipi (capture/web 'HOF').
ALTER TABLE ut_item DROP CONSTRAINT IF EXISTS ut_item_card_type_check;
ALTER TABLE ut_item ADD CONSTRAINT ut_item_card_type_check CHECK (card_type IN ('NORMAL', 'ICON', 'HERO', 'HOF', 'EVO'));
