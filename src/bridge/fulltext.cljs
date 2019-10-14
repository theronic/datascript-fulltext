(ns com.theronic.datascript.fulltext
  (:require
    [datascript.core :as d]
    [posh.reagent :as p]
    [clojure.string :as string] ;; goog.string?
    [taoensso.timbre :as log]))

;; Is it possible to do this in a web worker? Probably. The functions are simple.

;; cleanest would be to have a separate fulltext database.
;; it will be clean, but fill it be performant?

(def fulltext-schema
  ;; feels wrong to hardcode it.
  {:message/text {:db/index       true
                  ;:db/valueType :db/
                  :db/cardinality :db.cardinality/many}})

(def conn
  (d/create-conn fulltext-schema))

(defn register-fulltext!
  "Schema is a DS map of attributes to monitor and index in the fulltext cache."
  [schema])

(def stop-words #{"the" "a"})

(defn tokenize [value]
  ;; only handles space and comma right now. Hyphens?
  ;; is a newline a space?
  (string/split value #"[\s,-;]+"))

(defn parse-fulltext-value [value]
  (->> value
       (tokenize)
       (remove stop-words)
       (set)))

(defn query!
  "Takes search string as input and returns a vector of entity IDs.
  Consider adding FT conn as input?
  Make query extensible?"
  [input]
  (->> (parse-fulltext-value input)
       (d/q
         '[:find [?e ...]
           :in $ [?token ...]
           :where
           [?e ?a ?token]]
         conn)))

(defn parse-datom
  ":a? add?"
  [[e a v t add?]]
  ;; there is potentially to do efficient diffing of values with 3DF.
  (for [token (parse-fulltext-value v)]
    ;; TODO HANDLE REMOVE!
    [:db/add e a token]))

;; Need something here to check if any values need to be fulltext indexed
;; because it's possible that we need to do this lazily.

(def fulltext-schema-attr :db.index/fulltext) ;; eww

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
  (->> (d/q ;; USE datoms plz
         '[:find ?e ?a ?v ?t ?add
           :in [?a ...]
           :where
           ;[?a ?fulltext-attr true]
           [?e ?a ?v ?t ?add]]
         db
         #{:message/text})
       (mapv (fn [[e a v t add]]
               (let [])
               [:db/add e a v]))
       (d/transact! conn)))

;; lazy seek against d/datoms?

;(defn transact-fulltext)

;; we'll need a lazy batching mode.
;; Seems like we can always query the datoms index directly and lazily in batches of e.g. 100/1000.
;; even pre-emptively.

(defn handle-tx-report!
  "Parses "
  [{:as tx-report :keys [tx-data db-after]}]
  ;(log/debug "Fulltext tx-report:" tx-report)
  (let [fulltext-attrs #{:message/text}]                                 ;; todo: check schema please. And cache.
    (when-let [filtered (seq (filter #(get fulltext-attrs (:a %)) tx-data))]
      ;(log/debug "Matched fulltext data: " filtered)
      (->> (mapv parse-datom filtered)
           (apply concat)
           (d/transact! conn)
           :db-after
           log/debug))))
      ;(when (seq filtered)
      ;  ;(.log js/console filtered)
      ;  (when db-after
      ;    (js/setTimeout #(persist! db-after) 0))))))


(defn install-fulltext!
  "Installs a listener on the DS connection.
  Should indexed attrs be static or dynamic?"
  [conn] ;; pass in fulltext DB as arg?
  (d/listen! conn handle-tx-report!))
