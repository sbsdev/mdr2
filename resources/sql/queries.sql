-----------------
-- Productions --
-----------------

-- :name get-productions :? :*
-- :doc Retrieve all productions given a `limit`, an `offset` and an optional `search`. If `state` is given filter by that, otherwise return all productions that are not 'archived' or 'deleted'
SELECT *
FROM production
WHERE
--~ (if (:state params) "state = :state" "state NOT IN ('archived', 'deleted')")
--~ (when (:search params) "AND (LOWER(title) LIKE LOWER(:search) OR LOWER(creator) LIKE LOWER(:search))")
ORDER BY id DESC
LIMIT :limit OFFSET :offset

-- :name get-production :? :1
-- :doc Retrieve a production record given an `id`
SELECT *
FROM production
WHERE id = :id

-- :name find-production :? :1
-- :doc Retrieve a production record given a `product_number`
SELECT *
FROM production
WHERE 
--~ (string/join " AND " (for [field [:product_number] :when (contains? params field)] (str (identifier-param-quote (name field) options) " = " ":v:" (name field))))

-- :name insert-raw :i! :raw
-- :doc Insert a production.
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
INSERT INTO production (
       title,
       date,
       identifier,
       state,
--~ (string/join "," (for [field [:id :creator :subject :description :publisher :type :format :source :language :rights :source_date :source_edition :source_publisher :source_rights :multimedia_type :multimedia_content :narrator :producer :produced_date :revision :revision_date :revision_description :total_time :audio_format :depth :volumes :product_number :production_type :periodical_number :library_number :library_signature :library_record_id] :when (contains? params field)] (identifier-param-quote (name field) options)))
       )
VALUES (
       :title,
       :date,
       :identifier,
       :state,
--~ (string/join "," (for [field [:id :creator :subject :description :publisher :type :format :source :language :rights :source_date :source_edition :source_publisher :source_rights :multimedia_type :multimedia_content :narrator :producer :produced_date :revision :revision_date :revision_description :total_time :audio_format :depth :volumes :product_number :production_type :periodical_number :library_number :library_signature :library_record_id] :when (contains? params field)] (str ":v:" (name field))))
       )

-- :name update-production :! :n
-- :doc Update a production given by `id`, `library_number` or `product_number`
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
UPDATE production
SET
--~ (string/join "," (for [field [:title :creator :subject :description :publisher :date :type :format :identifier :source :language :rights :source_date :source_edition :source_publisher :source_rights :multimedia_type :multimedia_content :narrator :producer :produced_date :revision :revision_date :revision_description :total_time :audio_format :depth :volumes :state :product_number :production_type :periodical_number :library_number :library_signature :library_record_id] :when (contains? params field)] (str (identifier-param-quote (name field) options) " = :v:" (name field))))
WHERE
--~ (cond (:id params) "id = :id" (:library_number params) "library_number = :library_number" (:product_number params) "product_number = :product_number")

-- :name delete-production :! :n
-- :doc Remove the production with the given `id`
DELETE FROM production WHERE id = :id
