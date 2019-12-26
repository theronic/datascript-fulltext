# Fulltext Index Search Adapter for DataScript in ClojureScript

I needed fulltext search in a text-heavy application that uses DataScript.

## Is it any good?

Yes, but it's still early.

## Design

The search adapter maintains a fulltext index in a separate DataScript database. This is not ideal, but the current design of Datomic & DataScript do not support extensible indices.

The fulltext adapter:
 1. listens for changes in the source connection using `(d/listen! conn)`,
 2. inspects the incoming tx-report,
 3. filters on attributes that have `:db/fulltext true` in their schema,
 4. tokenises the string value,
 5. removes stop words like "the" and "and",
 6. maintains a multi-cardinality attribute in the fulltext DataScript DB. 

Using a separate connection makes it convenient to have a one-to-one attribute mapping and to manage cache eviction since it could grow large. In practice this is not an issue because you can query across DataScript databases, e.g. `(d/q '[:in $ $1 ...] db1 db2)`.

## Usage

    (ns datascript-fulltext.example
        :require [[reagent.core :as r :refer [atom]]
                  [datascript.core :as d]
                  [com.theronic.datascript.fulltext :as ft]])
    
    (def conn (d/create-conn {:message/text {:db/fulltext true}})
    (def !ft-conn (atom nil))
    
    (defn parent-component [conn]
        (let [!input (atom "hi")] ;; todo text input
          [:div [:code "Matching fulltext entities: " (ft/query! @!input)]]))
    
    (defn init! []
      (let [fulltext-conn (ft/install-fulltext! conn)] 
        (d/transact! conn [{:db/id -1 :message/text "hi there"}]) ;; load from storage after connecting to sync.
        (ft/search @ft/ft-conn "hi") ;; => fill yield message ID.
        (reset! !ft-conn fulltext-conn))
      (reagent/render [parent-component conn]))
      
    (init!)

# Todo

  1. [x] ~~Delete index values on mutation. Should be quick to add, and then do a smart diff to avoid writes.~~
  1. Store hashed token values instead of strings.
  1. Use schema definition of source connection.
  1. Track source schema and rebuild index on change.
  1. Batch updates using queued web workers to prevent locking main thread.
  1. Add adapter for off-site storage, e.g. Redis.
  1. Match source transaction IDs if possible.
  1. Fork & extend Datascript to support `(fulltext ...)` search function.
  1. [in progress] Add soundex or double-metaphone
  1. Maintain indexed token counts for relevance ranking. Maybe :db/index does this already?
  1. Support n-grams (can get heavy).
  1. Bloom filters.
  1. seq over matching datoms directly with pagination.
