(ns mdr2.production.spec
  (:require [spec-tools.data-spec :as spec]
            [clojure.spec.alpha :as s]))

(s/def ::state (s/and string? #{"new" "archived" "cataloged" "deleted" "encoded" "failed" "pending-split" "recorded" "split" "structured"}))
(s/def ::production_type (s/and string? #{"book" "periodical" "other"}))
(s/def ::library_signature (s/and string? #(re-matches #"^ds\d{5,}$" %)))
(s/def ::date #(instance? java.time.LocalDate %))

(def production
  {:title string?
   (spec/opt :creator) string?
   (spec/opt :subject) string?
   (spec/opt :description) string?
   (spec/opt :publisher) string?
   :date ::date
   (spec/opt :type) string?
   (spec/opt :format) string?
   (spec/opt :id) int?
   :identifier string?
   (spec/opt :source) string?
   :language string?
   (spec/opt :rights) string?
   (spec/opt :source_date) ::date
   (spec/opt :source_edition) string?
   (spec/opt :source_publisher) string?
   (spec/opt :source_rights) string?
   (spec/opt :multimedia_type) string?
   (spec/opt :multimedia_content) string?
   (spec/opt :narrator) string?
   (spec/opt :producer) string?
   (spec/opt :produced_date) ::date
   :revision int?
   (spec/opt :revision_date) ::date
   (spec/opt :revision_description) string?
   (spec/opt :total_time) int?
   (spec/opt :audio_format) string?
   (spec/opt :depth) int?
   (spec/opt :volumes) int?
   :state ::state
   (spec/opt :product_number) string?
   (spec/opt :production_type) ::production_type
   (spec/opt :periodical_number) string?
   (spec/opt :library_number) string?
   (spec/opt :library_signature) ::library_signature
   (spec/opt :library_record_id) int?
   })
