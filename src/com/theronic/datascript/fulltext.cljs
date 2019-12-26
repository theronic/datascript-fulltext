(ns com.theronic.datascript.fulltext
  ;(:require-macros [bridge.utils :refer [profile]])
  (:require
    [datascript.core :as d]
    [clojure.string :as string]))                             ;; goog.string?
    ;[taoensso.timbre :as log]))

;; It should be possible to keep fulltext updated in a web worker.

(def fulltext-schema-attr :db.index/fulltext) ;; magic keyword for attr index configuration. Not used yet. Fully-namespaced?

(def stop-words
  "Todo: make dynamic. Move into DB? Speed?"
  #{""                                        ;; sometimes empty dunno why.
    "a" "an" "and" "are" "as" "at" "be" "but" "by"
    "for" "if" "in" "into" "is" "it"
    "no" "not" "of" "on" "or" "such"
    "that" "the" "their" "then" "there" "these"
    "they" "this" "to" "was" "will" "with"})

(defn clean-string [value]
  (if (string? value)
    (string/replace-all value #"[@,\-;\.]" " ")
    ""))

(defn tokenize [s]
  ;; only handles space and comma right now. Hyphens?
  ;; is a newline a space?
  ; consider only matching on a subset of content
  (string/split s #"\s+"))

;; todo use double-metaphone.
;; Not sure how I'm going to design the attributes for multiple strategies.
;; Many DBs for many strategies? Seems a bit noisy.
;; Consider adding each possible match value as string EID.

(defn parse-fulltext-value
  [value]
  ;(if (string? value)
  ;  (log/debug "Parsing string" value)
  ;  (log/warn "Casting to string" value)) ;; todo handle numbers?
  (let [res (->> (str value)                                ;; does this double cast?
                 (string/trim)
                 (clean-string)
                 (tokenize)
                 (map string/lower-case)
                 (remove stop-words)
                 (set))]
    ;(log/debug value " => " res)
    res))

(defn search
  "Takes search string as input and returns a vector of entity IDs.
  Make query extensible? Filters for attrs?"
  [fulltext-db input]
  (->> (parse-fulltext-value input)
       (d/q                                                 ;; todo: use vaet datoms index directly.
         '[:find ?e ?a ?token
           :in $ [?token ...]
           :where
           [?e ?a ?token]]
         fulltext-db)))

(defn parse-datom
  ":a? add?"
  [[e a v t add?]]
  ;; there is potentially to do efficient diffing of values with 3DF.
  (for [token (parse-fulltext-value v)]
    [(if add? :db/add :db/retract) e a token]))

;; Need something here to check if any values need to be fulltext indexed
;; because it's possible that we need to do this lazily.

;; Considered using tx-meta for fulltext data, but you want lazy indexing.

;; not sure if you can override txid?

(defn find-unindexed-datoms
  "Performs index scan and yields datoms that do not exist in index.
  Todo: implement as-of behaviour."
  [db]
  (throw "not impl."))

(defn reindex!
  "Pass in fulltext conn?
  Can we do a diff here where we compare the transaction date with the index date?
  Would be nice if they matched."
  [ft-conn db]
  (let [])
  (->> (d/q                                                 ;; USE datoms plz
         '[:find ?e ?a ?v ?t ?add
           :in [?a ...]
           :where
           ;[?a ?fulltext-attr true]
           [?e ?a ?v ?t ?add]]
         db
         #{:message/text :translation/text})
       (mapv (fn [[e a v t add]]
               ;(let [])
               [:db/add e a v]))
       (d/transact! ft-conn)))

;; lazy seek against d/datoms?

;(defn transact-fulltext)

;; we'll need a lazy batching mode.
;; Seems like we can always query the datoms index directly and lazily in batches of e.g. 100/1000.
;; even pre-emptively.

(defn ingest-datoms!
  [ft-conn datoms]
  ;(log/debug "Ingest datoms: " datoms)
  ;(log/debug "attrs: " (keys fulltext-schema))
  (when-let [filtered (seq (filter #(get (set (keys fulltext-schema)) (:a %)) datoms))]
    ;(log/debug "Matched fulltext data: " filtered)
    (->> (mapv parse-datom filtered)
         (apply concat)
         (d/transact! ft-conn)
         :db-after)))
         ;log/debug)))

(defn handle-tx-report!
  [ft-conn {:as tx-report :keys [tx-data]}]
  (ingest-datoms! ft-conn tx-data))

;(when (seq filtered)
;  ;(.log js/console filtered)
;  (when db-after
;    (js/setTimeout #(persist! db-after) 0))))))

(def ds-listener-key :com.theronic.datascript/fulltext) ;; what about multiple?

(defn install-fulltext!
  "Installs a listener on the source DataScript connection,
  listening for changes to attributes in ft-schema, and maintains it in a new fulltext connection.
  Not sure if we should be instantiating here."
  [src-conn ft-schema]                                                    ;; pass in fulltext DB as arg?
  (let [ft-conn (d/create-conn ft-schema)]
    (d/listen! src-conn ds-listener-key (partial handle-tx-report! ft-conn))
    ft-conn))

(defn uninstall-fulltext!
  [src-conn]
  (d/unlisten! src-conn ds-listener-key))
