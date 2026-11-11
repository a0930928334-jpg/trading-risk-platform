-- One row per account with its user's status. SYSTEM users are internal (the dev market maker).
SELECT a.id            AS account_id,
       a.user_id,
       a.account_number,
       a.status        AS account_status,
       u.status        AS user_status,
       a.created_at
FROM accounts a
JOIN app_users u ON u.id = a.user_id
ORDER BY a.id
