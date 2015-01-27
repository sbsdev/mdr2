-- name: production-id-to-archive-id
-- Return an archive id for a given production id, i.e. dam number
SELECT id
FROM container
WHERE verzeichnis = :id

-- name: library-signature-to-archive-id
-- Return an archive id for a given library signature, i.e. ds number
SELECT id
FROM container
WHERE verzeichnis = (
      SELECT LOWER(REPLACE(value,' ', '')) FROM meta
      WHERE ns='sbs' AND element = 'idMaster' AND id_container IN
      	    (SELECT id FROM container WHERE verzeichnis = :signature))

