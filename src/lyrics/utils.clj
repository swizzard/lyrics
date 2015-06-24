(ns lyrics.utils
  (:require [clojure.string :refer [capitalize]]
            [clojure.tools.reader.edn :as edn]))

(defn get-query-str
  "Get a query string to retrieve a node with a given label based
   on a provided list of attribute names. The resulting string is
   intended to be passed as the 'q' argument of
   clojurewerkz.neocons.rest.cypher/query or tquery, in conjunction
   with a map with the same keys as were passed to this function
   :param label: the label of the node(s) required
   :param attr-keys: a sequence of keys
   :param node-name: the name to give the returned node(s). Defaults to `res`"
  [label attr-keys & [node-name]]
  (let [nn (or node-name "res")]
    (str
      (transduce
        (comp ;; xf
          (map name)
          (map (fn [attr] (str nn "." attr " = {" attr "}")))
          (interpose " AND "))
        str ;; reducing function
        (str "MATCH (res:" (capitalize label) ") WHERE ") ;; init
        attr-keys) ;; coll
      " RETURN " nn)))

(defn from-edn
  "Read an edn file
   :param edn-file: path to edn file to read
   :type edn-file: string (filepath)
   :returns: data"
  [edn-file] (with-open [r (clojure.java.io/reader edn-file)]
                            (edn/read (java.io.PushbackReader. r))))

(defn filter-contains
  "Returns a transducer that will remove all entities that are also in coll"
  [coll]
  (remove (set coll)))
