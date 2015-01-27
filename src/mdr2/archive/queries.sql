-- name: production-id-to-archive-id
-- Return an archive id for a given production id, i.e. dam number
SELECT id
FROM container
WHERE verzeichnis = :id
