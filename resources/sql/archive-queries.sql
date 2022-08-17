-- :name production-id-to-archive-id :? :1
-- :doc Return an archive id for a given production "`id`", i.e. either a dam number or a library signature
SELECT id
FROM container
WHERE verzeichnis = :id

-- :name insert-archive-container-job :! :n
-- :doc Insert a container job for given `archivar`, `abholer`, `aktion`, `transaktions_status``, `container_status`, `bemerkung`, `verzeichnis`, `sektion`, and `datum`
INSERT INTO container (
       archivar, abholer, aktion,
       transaktions_status, container_status,
       bemerkung, verzeichnis, sektion, datum)
VALUES(
       :archivar, :abholer, :aktion,
       :transaktions_status, :container_status,
       :bemerkung, :verzeichnis, :sektion, :datum)

-- :name update-archive-container-job :! :n
-- :doc Update a the container job with given `archivar`, `abholer`, `aktion`, `transaktions_status``, `container_status`, `bemerkung`, `verzeichnis`, `sektion`, and `datum` for given `container-id`
UPDATE container
SET archivar = :archivar,
    abholer = :abholer,
    aktion = :aktion,
    transaktions_status = :transaktions_status,
    container_status = :container_status,
    bemerkung = :bemerkung,
    verzeichnis = :verzeichnis,
    sektion = :sektion,
    datum = :datum
WHERE id = :container-id
