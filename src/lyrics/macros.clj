(ns lyrics.macros)

(defmacro -*>
  "Like ->, but takes a seq of functions"
  [start-val fns]
  `(-> ~start-val ~@(eval fns)))
