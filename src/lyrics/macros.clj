(ns lyrics.macros)

(defmacro -*> [start-val fns]
  `(-> ~start-val ~@(eval fns)))
