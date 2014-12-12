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

-- -- name: insert<!
-- -- Insert the given `production`
-- INSERT INTO production (
--        title, creator, subject, description, publisher, date, type, format, identifier, source, language, rights, source_date, source_edition, source_publisher, source_rights, multimedia_type, multimedia_content, narrator, producer, produced_date, revision, revision_date, revision_description, total_time, audio_format, depth, state_id, product_number, library_number
-- ) VALUES (
--        :title, :creator, :subject, :description, :publisher, :date, :type, :format, :identifier, :source, :language, :rights, :source_date, :source_edition, :source_publisher, :source_rights, :multimedia_type, :multimedia_content, :narrator, :producer, :produced_date, :revision, :revision_date, :revision_description, :total_time, :audio_format, :depth, :state_id, :product_number, :library_number
-- )

-- --name: update!
-- UPDATE production
-- SET
-- title = title
-- creator = :creator
-- subject = :subject
-- description = :description
-- publisher = :publisher
-- date = :date
-- type = :type
-- format = :format
-- identifier = :identifier
-- source = :source
-- language = :language
-- rights = :rights
-- source_date = :source_date
-- source_edition = :source_edition
-- source_publisher = :source_publisher
-- source_rights = :source_rights
-- multimedia_type = :multimedia_type
-- multimedia_content = :multimedia_content
-- narrator = :narrator
-- producer = :producer
-- produced_date = :produced_date
-- revision = :revision
-- revision_date = :revision_date
-- revision_description = :revision_description
-- total_time = :total_time
-- audio_format = :audio_format
-- depth = :depth
-- state_id = :state_id
-- product_number = :product_number
-- library_number = :library_number
-- WHERE
-- id = :id

-- name: find-user
-- Return the user with the given `id`
SELECT * FROM user WHERE id = :id

-- name: find-user-roles
-- Return all roles for the user with the given `id`
SELECT role_id
FROM user_role
WHERE user_id = :id
