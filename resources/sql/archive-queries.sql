-- :name production-id-to-archive-id :? :1
-- :doc Return an archive id for a given production `id`, i.e. dam number
SELECT id
FROM container
WHERE verzeichnis = :id
