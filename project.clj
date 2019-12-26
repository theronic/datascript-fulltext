(defproject datascript-fulltext "0.1"
  :description "Fulltext Index Search Adapter for DataScript in ClojureScript"
  :url "http://github.com/theronic/datascript-fulltext"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/clojurescript "1.10.597"]
   [datascript "0.18.7"]]
  :repl-options {:init-ns com.theronic.datascript.fulltext})
