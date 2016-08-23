(ns event-data-facebook-agent.core
  (:require [clojure.tools.logging :as l]
            [clojure.java.io :refer [reader writer]]
            [clojure.set :refer [map-invert]]
            [clojure.data.json :as json])
  (:require [org.httpkit.client :as http]
            [clj-time.format :as f]
            [clj-time.core :as clj-time]
            [crossref.util.doi :as cr-doi]
            [robert.bruce :refer [try-try-again]])
  (:import [java.net URLEncoder]
           [java.util UUID])
  (:gen-class))

; https://graph.facebook.com/v2.7/?access_token=656701554388983|fqJuiXLWOJR6kIvzpijiJgZlUxU&ids=https://example.com1,https://example.com2,https://example.com3,https://example.com4,https://example.com5,https://example.com6,https://example.com7,https://example.com8,https://example.com9,https://example.com10

; Maxmum per Facebook API.
(def chunk-size 20)


(def facebook-endpoint "https://graph.facebook.com/v2.7/")

(def api-token (atom nil))
(def source-token (atom nil))

(defn new-uuid [] (.toString (UUID/randomUUID)))

(defn extract-events
  "Extract a facebook result into a list of event deposits."
  [facebook-result url-doi-mapping subj-url time-str]
  (mapcat (fn [[url value]]
            (let [doi (url-doi-mapping url)]
              ; DOI should be found. But if FB gives us something back that we didn't recognise we can't.
              (when (not doi)
                (l/error "Couldn't reverse" url))
              (when doi
                [{:uuid (new-uuid)
                :source_token @source-token
                :subj_id subj-url
                :subj {:pid subj-url :URL "https://facebook.com" :title (str "Facebook activity at " time-str) :type "webpage" :issued time-str}
                :obj_id (cr-doi/normalise-doi doi)
                :relation_type_id "discusses"
                :source_id "facebook"
                :occurred_at time-str
                :total (get-in value ["share" "comment_count"] 0)}

               {:uuid (new-uuid)
                :source_token @source-token
                :subj_id subj-url
                :subj {:pid subj-url :URL "https://facebook.com" :title (str "Facebook activity at " time-str) :type "webpage" :issued time-str}
                :obj_id (cr-doi/normalise-doi doi)
                :relation_type_id "bookmarks"
                :source_id "facebook"
                :occurred_at time-str
                :total (get-in value ["share" "share_count"] 0)}])) facebook-result)))

(def facebook-url-date-formatter (f/formatter "yyyy/MM"))

(defn fetch
  "For a URL token and map of url-doi, retrieve all data.
  Return Evidence Log items."
  [url-dois]
  (let [urls (map first url-dois)
        ; escaped-urls (map #(URLEncoder/encode % "UTF-8") urls)
        ids-query (clojure.string/join "," urls)
        result (try-try-again {:sleep 5000 :tries 10} 
                #(deref (http/get facebook-endpoint {:as :text :query-params {"ids" ids-query "access_token" @api-token}})))
        now (clj-time/now)
        subj-url (str "https://facebook.com/" (f/unparse facebook-url-date-formatter now))
        ; If there's no body, report that. Status code will also be returned.
        body (when (:body result) (json/read-str (:body result)))
        events (when body (extract-events body url-dois subj-url (str now)))
        evidence-log {:input-status (:status result)
                      :input-headers (:headers result)
                      :input-body (:body result)
                      :events events}]
        
    evidence-log))


(defn fetch-partition
  "Fetch chunk of 'doi \n url \n' lines"
  [counter lines]
  (let [doi-urls (apply hash-map lines)
        url-dois (map-invert doi-urls)]
    (swap! counter #(+ chunk-size %))
    ; (l/info "Fetched" @counter)
    (when (zero? (mod @counter 1))
      (l/info "Fetched" @counter))
    (fetch url-dois)))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [api-token-input (first args)
        url-file (second args)
        output-file (nth args 2)
        source-token-input (nth args 3)
        counter (atom 0)]
    (reset! api-token api-token-input)
    (reset! source-token source-token-input)
    (l/info "URL File " url-file)
    (l/info "Output file" output-file)
    (l/info "Source token" source-token)
        (with-open [rdr (reader url-file)]
          ; Lines are pairs of "doi \n url \n"
          ; They are ALWAYS 1-1 mapping so it's OK to invert the map.
          (let [lines (partition-all (* chunk-size 2) (line-seq rdr))
                results (lazy-cat (pmap (partial fetch-partition counter) lines))]
            (with-open [wrt (writer output-file)]
              (json/write results wrt))
            ; (doseq [result results]
            ;   (prn result))
            

            


              )))
  (shutdown-agents)

  )
