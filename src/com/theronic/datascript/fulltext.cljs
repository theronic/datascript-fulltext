(ns com.theronic.datascript.fulltext
  (:require-macros
    [bridge.utils :refer [profile]])
  (:require
    [datascript.core :as d]
    [posh.reagent :as p]
    [clojure.string :as string]                             ;; goog.string?
    [taoensso.timbre :as log]))

;; Is it possible to do this in a web worker? Probably. The functions are simple.

;; cleanest would be to have a separate fulltext database.
;; it will be clean, but fill it be performant?

(def fulltext-schema
  ;; todo infer and set cardinality correctly.
  {:contact/name     {:db/index       true
                      :db/cardinality :db.cardinality/many}
   :contact/phone    {:db/index       true
                      :db/cardinality :db.cardinality/many}
   :contact/email    {:db/index       true
                      :db/cardinality :db.cardinality/many}
   :translation/text {:db/index       true
                      :db/cardinality :db.cardinality/many}
   :message/text     {:db/index       true
                      ;:db/valueType :db/
                      :db/cardinality :db.cardinality/many}})

(def fulltext-conn
  (d/create-conn fulltext-schema))

;(defn register-fulltext!
;  "Schema is a DS map of attributes to monitor and index in the fulltext cache."
;  [schema])

(def stop-words #{""                                        ;; sometimes empty dunno why.
                  "a" "an" "and" "are" "as" "at" "be" "but" "by"
                  "for" "if" "in" "into" "is" "it"
                  "no" "not" "of" "on" "or" "such"
                  "that" "the" "their" "then" "there" "these"
                  "they" "this" "to" "was" "will" "with"})

(defn clean-string [value]
  (if (string? value)
    (string/replace-all value #"[@,\-;]" " ")
    ""))

(defn tokenize [s]
  ;; only handles space and comma right now. Hyphens?
  ;; is a newline a space?
  ; consider only matching on a subset of content
  (string/split s #"\s+"))

;; todo use double-metaphone.
;; Not sure how I'm going to design the attributes for multiple strategies.
;; Multiple DBs maybe? Seems a bit noisy.
;; consider adding each possible match value as string EID.

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
    (log/debug value " => " res)
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

(def fulltext-schema-attr :db.index/fulltext)               ;; eww

;; Considered using tx-meta for fulltext data, but you want lazy indexing.

;; not sure if you can override txid?

(defn find-unindexed-datoms
  "When "
  [db])


(defn reindex!
  "Pass in fulltext conn?
  Can we do a diff here where we compare the transaction date with the index date?
  Would be nice if they matched."
  [db]
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
       (d/transact! fulltext-conn)))

;; lazy seek against d/datoms?

;(defn transact-fulltext)

;; we'll need a lazy batching mode.
;; Seems like we can always query the datoms index directly and lazily in batches of e.g. 100/1000.
;; even pre-emptively.

(defn ingest-datoms!
  [datoms]
  (log/debug "Ingest datoms: " datoms)
  (log/debug "attrs: " (keys fulltext-schema))
  (when-let [filtered (seq (filter #(get (set (keys fulltext-schema)) (:a %)) datoms))]
    ;(log/debug "Matched fulltext data: " filtered)
    (->> (mapv parse-datom filtered)
         (apply concat)
         (d/transact! fulltext-conn)
         :db-after
         log/debug)))

(defn handle-tx-report!
  [{:as tx-report :keys [tx-data]}]
  (ingest-datoms! tx-data))

;(when (seq filtered)
;  ;(.log js/console filtered)
;  (when db-after
;    (js/setTimeout #(persist! db-after) 0))))))

(def ds-listener-key :com.theronic.datascript/fulltext)

(defn install-fulltext!
  "Installs a listener on the DS connection.
  Should indexed attrs be static or dynamic?"
  [conn]                                                    ;; pass in fulltext DB as arg?
  (d/listen! conn ds-listener-key handle-tx-report!))

(defn uninstall-fulltext!
  [conn]
  (d/unlisten! conn ds-listener-key))
