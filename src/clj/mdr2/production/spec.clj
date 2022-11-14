(ns mdr2.production.spec
  (:require [spec-tools.data-spec :as spec]
            [clojure.spec.alpha :as s]))

(s/def ::state (s/and string?
                      #{"new" "archived" "cataloged" "deleted" "encoded"
                        "failed" "pending-split" "recorded" "split" "structured"
                        "repairing"}))
(s/def ::production_type (s/and string? #{"book" "periodical" "other"}))
(s/def ::library_signature (s/and string? #(re-matches #"^ds\d{5,}$" %)))
(s/def ::library_number (s/and string? #(re-matches #"^PNX \d{4,}$" %)))
(s/def ::date #(instance? java.time.LocalDate %))

(s/def ::volumes (s/and int? #{1 2 3 4 5 6 7 8}))
(s/def ::sample-rate (s/and int? #{11025 22050 44100 48000}))
(s/def ::bit-rate (s/and int? #{32 48 56 64 128}))

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
   (spec/opt :volumes) ::volumes
   :state ::state
   (spec/opt :product_number) string?
   (spec/opt :production_type) ::production_type
   (spec/opt :periodical_number) string?
   (spec/opt :library_number) ::library_number
   (spec/opt :library_signature) ::library_signature
   (spec/opt :library_record_id) int?
   })

(s/def ::library_number-maybe (s/and string? #(re-matches #"^PNX \d{1,}$" %)))
(s/def ::library_signature-maybe (s/and string? #(re-matches #"^ds\d{1,}$" %)))

(defn state?
  "Return true if `s` is a valid state"
    [s] (s/valid? ::state s))

(defn library-number-maybe?
  "Return true if `s` looks like it might be a valid library number"
  [s] (s/valid? ::library_number-maybe s))

(defn library-signature-maybe?
  "Return true if `s` looks like it might be a valid library signature"
  [s] (s/valid? ::library_signature-maybe s))
