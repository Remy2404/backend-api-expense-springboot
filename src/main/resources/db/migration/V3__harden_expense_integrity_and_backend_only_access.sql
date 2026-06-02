-- Preserve the legacy invalid row for audit history while excluding it from
-- active finance calculations before enforcing the invariant for future writes.
UPDATE expenses
SET is_deleted = TRUE,
    deleted_at = COALESCE(deleted_at, NOW()),
    updated_at = NOW()
WHERE amount <= 0
   OR amount = 'NaN'::DOUBLE PRECISION
   OR amount = 'Infinity'::DOUBLE PRECISION
   OR amount = '-Infinity'::DOUBLE PRECISION;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_expenses_positive_finite_amount'
          AND conrelid = 'expenses'::REGCLASS
    ) THEN
        ALTER TABLE expenses
            ADD CONSTRAINT chk_expenses_positive_finite_amount
            CHECK (
                COALESCE(is_deleted, FALSE)
                OR (
                    amount > 0
                    AND amount < 'Infinity'::DOUBLE PRECISION
                )
            );
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_expenses_recurring_expense_id
    ON expenses (recurring_expense_id);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_tokens_replaced_by_token_id
    ON auth_refresh_tokens (replaced_by_token_id);

CREATE INDEX IF NOT EXISTS idx_bill_split_expenses_payer_participant_group
    ON bill_split_expenses (payer_participant_id, group_id);

CREATE INDEX IF NOT EXISTS idx_bill_split_settlements_participant_group
    ON bill_split_settlements (participant_id, group_id);

ALTER TABLE bill_split_participants
    DROP CONSTRAINT IF EXISTS uq_bill_split_participants_id_group;
DROP INDEX IF EXISTS idx_budgets_user;
DROP INDEX IF EXISTS idx_categories_user;

REVOKE ALL ON TABLE pending_ai_actions FROM anon, authenticated;

REVOKE EXECUTE ON FUNCTION public.firebase_uid() FROM anon, authenticated;
