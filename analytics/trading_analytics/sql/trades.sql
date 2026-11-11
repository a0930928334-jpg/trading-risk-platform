SELECT id, instrument_id, price, quantity, buy_order_id, sell_order_id,
       buyer_account_id, seller_account_id, aggressor_side, created_at
FROM trades
ORDER BY id
