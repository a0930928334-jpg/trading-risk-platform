-- Ascending id is the order in which entries were written; the replay check depends on it.
SELECT id, account_id, entry_type, amount, balance_after, reserved_after, order_id, trade_id, created_at
FROM cash_ledger_entries
ORDER BY account_id, id
