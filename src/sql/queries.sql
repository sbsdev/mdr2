-- name: find
-- Return production for given `id`
SELECT * FROM production WHERE id = :id

-- name: find-all
-- Return all productions
SELECT * FROM production

-- name: find-by-productnumber
-- Return the production for given `product_number`
SELECT * FROM production WHERE product_number = :product_number

-- name: find-by-state
-- Return the production for given `state`
SELECT * FROM production WHERE state_id = :state

-- name: find-volumes
-- Return all volumes for a given production `id`
SELECT * FROM volume WHERE production_id = :id

-- name: delete!
-- Remove the production with the given `id`
DELETE FROM production WHERE id = :id

-- name: find-state
-- Return the state for given `id`
SELECT * FROM state WHERE id = :id

-- name: find-user
-- Return the user with the given `id`
SELECT * FROM user WHERE id = :id

-- name: find-user-roles
-- Return all roles for the user with the given `id`
SELECT role_id
FROM user_role
WHERE user_id = :id
