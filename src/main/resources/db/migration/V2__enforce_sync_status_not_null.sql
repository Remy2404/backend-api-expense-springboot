-- Existing NULL values do not prove a completed sync. Preserve them as pending
-- before enforcing the invariant for future writes.
UPDATE budgets SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE categories SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE category_budgets SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE expenses SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE profiles SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE recurring_expenses SET sync_status = 'pending' WHERE sync_status IS NULL;
UPDATE savings_goals SET sync_status = 'pending' WHERE sync_status IS NULL;

ALTER TABLE budgets ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE categories ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE category_budgets ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE expenses ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE profiles ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE recurring_expenses ALTER COLUMN sync_status SET NOT NULL;
ALTER TABLE savings_goals ALTER COLUMN sync_status SET NOT NULL;
