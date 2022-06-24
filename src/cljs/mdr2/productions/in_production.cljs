(ns mdr2.productions.in-production
  (:require [ajax.core :as ajax]
            [mdr2.auth :as auth]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.i18n :refer [tr]]
            [mdr2.pagination :as pagination]
            [mdr2.productions.production :as production]
            [mdr2.productions.notifications :as notifications]
            [re-frame.core :as rf]
            [clojure.set :as set]))

(rf/reg-event-fx
  ::fetch-productions
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search])
          offset (pagination/offset db :in-production)]
      {:db (assoc-in db [:loading :in-production] true)
       :http-xhrio
       (as-transit {:method          :get
                    :uri             "/api/productions"
                    :params          {:search (if (nil? search) "" search)
                                      :offset offset
                                      :limit pagination/page-size}
                    :on-success      [::fetch-productions-success]
                    :on-failure      [::fetch-productions-failure]})})))

(rf/reg-event-db
 ::fetch-productions-success
 (fn [db [_ productions]]
   (let [productions (->> productions
                    (map #(assoc % :uuid (str (random-uuid)))))
         next? (-> productions count (= pagination/page-size))]
     (-> db
         (assoc-in [:productions :in-production] (zipmap (map :uuid productions) productions))
         (pagination/update-next :in-production next?)
         (assoc-in [:loading :in-production] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-productions-failure
 (fn [db [_ response]]
   (-> db
       (notifications/set-errors :fetch-in-production-productions (get response :status-text))
       (assoc-in [:loading :in-production] false))))

(rf/reg-event-fx
  ::delete-production
  (fn [{:keys [db]} [_ id]]
    (let [production (get-in db [:productions :in-production id])]
      {:db (notifications/set-button-state db id :delete)
       :http-xhrio
       (as-transit {:method          :delete
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/productions/" (:id production))
                    :on-success      [::ack-delete id]
                    :on-failure      [::ack-failure id :delete]
                    })})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (notifications/clear-button-state db id :save)))

(rf/reg-event-fx
  ::ack-delete
  (fn [{:keys [db]} [_ id]]
    (let [db (-> db
                 (update-in [:productions :in-production] dissoc id)
                 (notifications/clear-button-state id :delete))
          empty? (-> db (get-in [:productions :in-production]) count (< 1))]
      (if empty?
        {:db db :dispatch [::fetch-productions]}
        {:db db}))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))]
     (-> db
         (notifications/set-errors request-type message)
         (notifications/clear-button-state id request-type)))))

(rf/reg-sub
 ::productions
 (fn [db _] (->> db :productions :in-production vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id >))))

(rf/reg-sub
  ::search
  (fn [db _] (get-in db [:search :in-production])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     (cond-> {:db (assoc-in db [:search :in-production] new-search-value)}
       (> (count new-search-value) 2)
       ;; if the string has more than 2 characters fetch the productions
       ;; from the server
       (assoc :dispatch-n
              (list
               ;; when searching for a new production reset the pagination
               [::pagination/reset :in-production]
               [::fetch-productions])))))

(defn productions-search []
  (let [get-value (fn [e] (-> e .-target .-value))
        reset!    #(rf/dispatch [::set-search ""])
        save!     #(rf/dispatch [::set-search %])]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder (tr [:search])
                     :aria-label (tr [:search])
                     :value @(rf/subscribe [::search])
                     :on-change #(save! (get-value %))
                     :on-key-down #(when (= (.-which %) 27) (reset!))}]]]))

(defn productions-filter []
  [:div.field.is-horizontal
   [:div.field-body
    [productions-search]]])

(rf/reg-sub
 ::production
 (fn [db [_ id]]
   (get-in db [:productions :in-production id])))

(rf/reg-event-fx
  ::upload-dtbook
  (fn [{:keys [db]} [_ js-file-value]]
    (let [form-data (doto (js/FormData.)
                      (.append "file" js-file-value "filename.txt"))
          id (get-in db [:current-production :id])]
      {:db (-> db
               (notifications/set-button-state :in-production :upload-file))
       :http-xhrio (as-transit
                    {:method          :post
                     :headers 	      (auth/auth-header db)
                     :uri             (str "/api/productions/" id "/xml")
                     :body            form-data
                     :on-success      [::ack-upload-dtbook]
                     :on-failure      [::ack-upload-failure]})})))

(rf/reg-event-fx
  ::ack-upload-dtbook
  (fn [{:keys [db]} [_]]
    {:db (notifications/clear-button-state db :in-production :upload-file)
     :common/navigate-fx! [:in-production]}))

(rf/reg-event-db
 ::ack-upload-failure
 (fn [db [_ response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))
         errors (get-in response [:response :errors])]
     (-> db
         (notifications/set-errors :dtbook-upload message errors)
         (notifications/clear-button-state :in-production :upload-file)))))

(rf/reg-sub
 ::upload-file
 (fn [db _] (get-in db [:upload :in-production])))

(rf/reg-event-db
  ::set-upload-file
  (fn [db [_ file]] (assoc-in db [:upload :in-production] file)))

(rf/reg-event-db
  ::clear-upload-file
  (fn [db [_]] (update-in db [:upload] dissoc :in-production)))

(defn- file-input []
  (let [get-value (fn [e] (-> e .-target .-files (aget 0)))
        save!     #(rf/dispatch [::set-upload-file %])
        file      @(rf/subscribe [::upload-file])]
    [:p.control
     [:div.file.has-name
      [:label.file-label
       [:input.file-input
        {:type "file"
         :accept ".xml"
         :files file
         :on-change #(save! (get-value %))}]
       [:span.file-cta
        [:span.file-label (tr [:choose-structure])]]
       [:span.file-name (if file (.-name file) (tr [:no-file]))]]]]))

(defn- structure-upload []
  (let [klass (when @(rf/subscribe [::notifications/button-loading? :in-production :upload-file]) "is-loading")
        roles @(rf/subscribe [::auth/user-roles])
        file @(rf/subscribe [::upload-file])
        current @(rf/subscribe [::production/current])]
    [:<>
     [:div.field
      [:label.label (tr [:upload-structure] [(:title current) (:id current)])]
      [file-input]]
     [:div.field.is-grouped
      [:p.control
       [:button.button
        {:disabled (or (nil? file) (empty? (set/intersection #{"madras2.it" "madras2.etext"} roles)))
         :class klass
         :on-click (fn [e] (rf/dispatch [::upload-dtbook file]))}
        [:span (tr [:upload])]
        [:span.icon
         [:span.material-icons "upload_file"]]]]]]))

(defn buttons [{:keys [uuid id state] :as production}]
  (let [roles @(rf/subscribe [::auth/user-roles])]
    [:div.buttons.has-addons
     ;; show the download button while the production hasn't been recorded
     (when (#{"new" "structured"} state)
       [:a.button
        {:href (str "/api/productions/" id "/xml")
         :download (str id ".xml")}
        [:span.icon.is-small
         [:span.material-icons "file_download"]]])
     ;; show the upload button if the production hasn't been recorded
     ;; and the user is authorized
     (when (and (seq (set/intersection #{"madras2.it" "madras2.etext"} roles))
                (#{"new" "structured"} state))
       [:a.button
        {:href (str "#/productions/" id "/upload")
         :on-click (fn [e] (rf/dispatch [::production/set-current production]))}
        [:span.icon.is-small
         [:span.material-icons "file_upload"]]])
     ;; show the "Recorded" button if the next state is "recorded",
     ;; the user is authorized, and the production has been imported
     ;; from the libary, i.e. is not handled via ABACUS or the
     ;; production has a revision greater than zero as is the case
     ;; with productions that are repaired
     (when (and (seq (set/intersection #{"madras2.it" "madras2.admin"} roles))
                (#{"structured"} state)
                (or (:library_number production) (> (:revision production) 0)))
       (if @(rf/subscribe [::notifications/button-loading? uuid :recorded])
         [:button.button.is-loading]
         [:button.button
          {:on-click (fn [e] (rf/dispatch [::recorded-production uuid]))}
          [:span.icon.is-small
           [:span.material-icons "record_voice_over"]]]))
     ;; show the "Split" button if the next state is "split" and the user is
     ;; authorized
     (when (and (seq (set/intersection #{"madras2.it" "madras2.admin"} roles))
                (#{"pending-split"} state))
         (if @(rf/subscribe [::notifications/button-loading? uuid :split])
           [:button.button.is-loading]
           [:button.button
            {:on-click (fn [e] (rf/dispatch [::split-production uuid]))}
            [:span.icon.is-small
             [:span.material-icons "call_split"]]]))
     (when (seq (set/intersection #{"madras2.it"} roles))
       (if @(rf/subscribe [::notifications/button-loading? uuid :delete])
         [:button.button.is-danger.is-loading]
         [:button.button.is-danger
          {:on-click (fn [e] (rf/dispatch [::delete-production uuid]))}
          [:span.icon.is-small
           [:span.material-icons "delete"]]]))]))

(defn production-link [{:keys [id title] :as production}]
  [:a {:href (str "#/productions/" id)
       :on-click (fn [_] (rf/dispatch [::production/set-current production]))}
   title])


(defn production [id]
  (let [{:keys [uuid id production_type state] :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td id]
     [:td [production-link production]]
     [:td production_type]
     [:td state]
     [:td {:width "14%"} [buttons production]]]))

(defn productions []
  (let [productions @(rf/subscribe [::productions-sorted])]
    [:<>
     [:table.table.is-striped
      [:thead
       [:tr
        [:th (tr [:dam])]
        [:th (tr [:title])]
        [:th (tr [:production_type])]
        [:th (tr [:state])]
        [:th (tr [:action])]]]
      [:tbody
       (for [{:keys [uuid]} productions]
         ^{:key uuid} [production uuid])]]
     [pagination/pagination :in-production [::fetch-productions]]]))

(defn upload-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :in-production])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:section.section>div.container>div.content
     [:<>
      (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        :else [structure-upload])]])  )

(defn page []
  (let [loading? @(rf/subscribe [::notifications/loading? :in-production])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:section.section>div.container>div.content
     [:<>
      [productions-filter]
      (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        :else [productions])]]))
