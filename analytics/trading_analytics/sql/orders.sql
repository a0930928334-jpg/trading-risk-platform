SELECT id, account_id, instrument_id, side, price, quantity, filled_quantity, status,
       reject_code, reject_reason, created_at, updated_at
FROM orders
ORDER BY id
