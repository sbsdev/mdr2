(ns mdr2.productions.in-production
  (:require [ajax.core :as ajax]
            [mdr2.auth :as auth]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.i18n :refer [tr]]
            [mdr2.pagination :as pagination]
            [mdr2.productions.forms :as forms]
            [mdr2.productions.notifications :as notifications]
            [mdr2.productions.production :as production]
            [mdr2.productions.search :as search]
            [fork.re-frame :as fork]
            [re-frame.core :as rf]
            [clojure.set :as set]))

(rf/reg-event-fx
  ::fetch-productions
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search/search :in-production])
          offset (pagination/offset db :in-production)]
      {:db (assoc-in db [:loading :in-production] true)
       :http-xhrio
       (as-transit {:method          :get
                    :uri             "/api/productions"
                    :params          {:search search
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

(rf/reg-event-fx
  ::split-production
  (fn [{:keys [db]} [_ id {:keys [values path]}]]
    {:db (fork/set-submitting db path true)
     :http-xhrio
     (as-transit {:method          :post
                  :headers 	     (auth/auth-header db)
                  :uri             (str "/api/productions/" id "/split")
                  :params          values
                  :on-success      [::ack-split path]
                  :on-failure      [::ack-split-failure path]
                  })}))

(rf/reg-event-fx
  ::ack-split
  (fn [{:keys [db]} [_ path]]
    {:db (fork/set-submitting db path false)
     ;; FIXME: seems weird that we have to fetch the productions here.
     ;; Isn't there a way to get directly back to the :in-production
     ;; view?
     :dispatch [::fetch-productions]
     :common/navigate-fx! [:in-production]}))

(rf/reg-event-db
 ::ack-split-failure
 (fn [db [_ path response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))]
     (-> db
         (fork/set-submitting path false)
         (fork/set-server-message path message)))))

(rf/reg-sub
 ::productions
 (fn [db _] (->> db :productions :in-production vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id >))))

(defn productions-filter []
  [:div.field.is-horizontal
   [:div.field-body
    [search/productions-search :in-production ::fetch-productions]]])

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
      [:label.label (tr [:upload-structure-for] [(:title current) (:id current)])]
      [file-input]]
     [:div.field.is-grouped
      [:p.control
       [:button.button.is-primary
        {:disabled (or (nil? file) (empty? (set/intersection #{:it :etext} roles)))
         :class klass
         :on-click (fn [e] (rf/dispatch [::upload-dtbook file]))}
        [:span (tr [:upload])]
        [:span.icon {:aria-hidden true}
         [:i.material-icons "upload_file"]]]]]]))

(defn- tooltip-button [{:keys [tooltip icon] :as opts}]
  [:a.button.has-tooltip-arrow
   (merge
    {:data-tooltip (tr [tooltip])
     :aria-label (tr [tooltip])}
    (dissoc opts :tooltip :icon))
   [:span.icon.is-small {:aria-hidden true}
    [:i.material-icons icon]]])

(defn buttons [{:keys [uuid id state has-manifest? has-manual-split?] :as production}]
  (let [roles @(rf/subscribe [::auth/user-roles])]
    [:div.buttons.has-addons
     ;; show the download button while the production hasn't been recorded
     (when (#{"new" "structured"} state)
       (tooltip-button
        {:href (str "/api/productions/" id "/xml")
         :download (str id ".xml")
         :tooltip :download-structure
         :icon "file_download"}))
     ;; show the upload button if the production hasn't been recorded
     ;; and the user is authorized
     (when (and (seq (set/intersection #{:it :etext} roles))
                (#{"new" "structured"} state))
       (tooltip-button
        {:href (str "#/productions/" id "/upload")
         :on-click (fn [e] (rf/dispatch [::production/set-current production]))
         :tooltip :upload-structure
         :icon "file_upload"}))
     ;; show the "Recorded" button if the next state is "recorded",
     ;; the user is authorized, and the production has been imported
     ;; from the libary, i.e. is not handled via ABACUS or the
     ;; production has a revision greater than zero as is the case
     ;; with productions that are repaired
     (when (and (seq (set/intersection #{:it :admin} roles))
                (#{"structured"} state)
                (or (:library_number production) (> (:revision production) 0)))
       (if @(rf/subscribe [::notifications/button-loading? uuid :recorded])
         [:button.button.is-loading]
         (tooltip-button
          {:on-click (fn [e] (rf/dispatch [::recorded-production uuid]))
           :tooltip :mark-recorded
           :disabled (not has-manifest?)
           :icon "task"})))
     ;; show the "Split" button if the next state is "split" and the user is
     ;; authorized
     (when (and (seq (set/intersection #{:it :admin} roles))
                (#{"pending-split"} state))
         (if @(rf/subscribe [::notifications/button-loading? uuid :split])
           [:button.button.is-loading]
           (tooltip-button
            (cond-> {:on-click (fn [e] (rf/dispatch [::production/set-current production]))
                     :disabled (not has-manual-split?)
                     :tooltip :mark-split
                     :icon "call_split"}
              ;; only add a link (enable the button) if there is a manual split
              has-manual-split? (assoc :href (str "#/productions/" id "/split"))))))
     (when (seq (set/intersection #{:it} roles))
       (if @(rf/subscribe [::notifications/button-loading? uuid :delete])
         [:button.button.is-danger.is-loading]
         (tooltip-button
          {:on-click (fn [e] (rf/dispatch [::delete-production uuid]))
           :class "is-danger"
           :tooltip :delete
           :icon "delete"})))]))

(defn- select-field [key options {:keys [values set-handle-change handle-blur]}]
  [:div.field.is-horizontal
   [:div.field-label.is-normal
    [:label.label (tr [key])]]
   [:div.field-body
    [:div.field
     [:div.control
      [:div.select
       [:select
        {:name key
         :value (values key)
         :on-change #(set-handle-change
                      {:value (-> % .-target .-value js/parseInt)
                       :path [key]})
         :on-blur handle-blur}
        (for [option options]
          ^{:key option}
          [:option {:value option} option])]]]]]])

(defn split-form [id]
  [fork/form {:initial-values {:volumes 2
                               :sample-rate 22050
                               :bit-rate 56}
              :path [:form :split]
              :prevent-default? true
              :clean-on-unmount? true
              :on-submit #(rf/dispatch [::split-production id %])
              :keywordize-keys true
              }
   (fn [{:keys [values
                path
                form-id
                handle-blur
                submitting?
		on-submit-server-message
                set-handle-change
                handle-submit] :as props}]
     (let [roles @(rf/subscribe [::auth/user-roles])]
       (if on-submit-server-message
         [forms/error-notification on-submit-server-message path]
         [:form
          {:id form-id
           :on-submit handle-submit}
          (select-field :volumes [2 3 4 5 6 7 8] props)
          (select-field :sample-rate [11025 22050 44100 48000] props)
          (select-field :bit-rate [32 48 56 64 128] props)
          [:div.field.is-horizontal
           [:div.field-label]
           [:div.field-body
            [:div.field
             [:p.control
              [:button.button.is-primary
               {:type "submit"
                :disabled (empty? (set/intersection #{:it :admin} roles))
                :class (when submitting? "is-loading")}
               [:span (tr [:mark-split])]
               [:span.icon {:aria-hidden true}
                [:i.material-icons "call_split"]]]]]]]])))])

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
     [:td #_{:width "14%"} [buttons production]]]))

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

(defn split-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :in-production])
        errors? @(rf/subscribe [::notifications/errors?])
        current @(rf/subscribe [::production/current])]
    [:section.section>div.container>div.content
     [:<>
      (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        :else [split-form (:id current)])]]))

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
