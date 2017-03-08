(ns atom-finder.results-util
  (:require [atom-finder.util :refer :all]
            [clojure.string :as str]
            [schema.core :as s]
            )
  (:use     [clojure.pprint :only [pprint print-table]])
  (:import [org.eclipse.cdt.core.dom.ast IASTNode IASTUnaryExpression]))

; Utilities for processing the edn output of print-atoms-in-dir

(defn read-data
  "Parse an edn file from the data directory"
  [filename]
  (->> (str "data/" filename)
       ClassLoader/getSystemResource
       clojure.java.io/file
       slurp
       read-string))

(defn dedupe-preprocessors
  [results]
  "Only count 1 preprocessor statement per line"
  (for [result results]
    (update-in result [1 :preprocessor-in-statement] distinct)))

(defn sum-found-atoms
  "Generate a total count of each of the atoms in the result edn"
  [results]
  (->> results
       ;(take 300)
       (map last)
       (map (partial map-values count))
       (reduce (partial merge-with +))
       ))

(defn found-atom-source
  "Return the source code for an atom found in a file"
  [atom-name results]
  (->> results
       (filter #(not-empty (atom-name (last %))))
       (map (fn [[filename hash]]
              [filename
              (map #(vector %1 (nth (slurp-lines (expand-home filename)) (dec %1)))
                   (atom-name hash))]))

       pprint))