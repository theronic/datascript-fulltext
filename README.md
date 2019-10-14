# Fulltext Indexing Adapter for DataScript in ClojureScript

I'm working on a text-heavy application that uses DataScript and I needed to do fulltext searches on locally cached data.

## Is it any good?

Not yet, it's a work-in-progress. Currently requires manual fiddling with schema to get to work, but this can be automated.

## Design

The fulltext adapter uses `(d/listen! conn)` to inspect incoming tx-report, filters on attributes that have `:db/fulltext true`, tokenises the string value, removes stop words (like "the" and "and"), and maintains a multiple cardinality DataScript DB for these attributes.

I chose to use a separate DataScript DB because it makes it easier to have a one-to-one attribute mapping, and to manage localStorage cache eviction since it could grow large. This is convenient because you can query across DataScript databases if you want to, e.g. `(d/q '[:in $ $1 ...] db1 db2)`.

Compatible with Posh for Reagent.

## Usage

    (require '[reagent.core :as r :refer [atom]])
    (require '[datascript.core :as d])
    (require '[com.theronic.datascript.fulltext :as ft])
    
    (def conn (d/create-conn {:message/text {:db/fulltext true}})
    
    (defn parent-component [conn]
        (let [!input (atom "hi")] ;; todo text input
          [:div [:code "Matching fulltext entities: " (ft/query! @!input)]]))
    
    (defn init! []
      (ft/install-fulltext! conn) ;; here we attach to the 
      (d/transact! conn [{:db/id -1 :message/text "hi there"}]) ;; load from storage after connecting to sync.
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
