SELECT id, account_id, instrument_id, quantity, reserved_quantity, updated_at
FROM positions
ORDER BY account_id, instrument_id
