(ns od117-walker.core
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            
            [clj-http.client :as client]
            [pl.danieljanus.tagsoup :as ts]))

(def colours ["aquamarine" "brown1" "burlywood1" "cadetblue1" "chocolate1" "darkorchid2" "forestgreen"])

(defn find-and-parse-page
  "Download the given wiki page and run it through the parser."
  [page wiki-address]
  (->> page
       (str wiki-address)
       (client/get)
       (:body)
       (ts/parse-string)))

(defn find-id
  "Find the html element with a given id in the given html."
  [html id]
  (if (= (:id (second html)) id)
    html
    (loop [childs (ts/children html)]
      (if (and (seq childs) (not (string? (first childs))))
        (if (find-id (first childs) id)
          (find-id (first childs) id)
          (recur (rest childs)))
        nil))))

(defn extract-links
  "Extract the links from the given html. Ignores authors."
  [html exclusions]
  (reduce (fn [ls h]
            (if (not (string? h))
              (apply hash-set (concat
                               (if-not (string? h)
                                 (if (and (= (first h) :a)
                                          (not (exclusions (:href (second h)))))
                                   (conj ls (:href (second h)))
                                   ls)
                                 ls)
                               (extract-links h exclusions)))
              ls))
          #{}
          (ts/children html)))

;; TODO: Refactor these 2 functions as they are copy/pasted.
(defn extract-author-help
  "Extract the author of the entry from the html. Assumes there is only one author linked."
  [html authors]
  (reduce (fn [ls h]
                    (if (not (string? h))
                      (apply hash-set (concat
                                       (if-not (string? h)
                                         (if (and (= (first h) :a)
                                                  (authors (:href (second h))))
                                           (conj ls (:href (second h)))
                                           ls)
                                         ls)
                                       (extract-author-help h authors)))
                      ls))
                  #{}
                  (ts/children html)))

(defn extract-author
  [html author]
  (first (extract-author-help html author)))

(defn walk
  "Walk through the wiki building a graph representation. Takes a list of starting pages and a set of pages to ignore."
  [start-points authors wiki-address]
  (let [exclusions (conj authors "javascript:;")]
    (loop [to-check start-points
           visited exclusions
           out {}]
      (if-let [next (first to-check)]
        (if (visited next)
          (recur (rest to-check)
                 visited
                 out)
          (do
            (println next)
            (if-let [next-para (try
                                 (find-id (find-and-parse-page next
                                                               wiki-address)
                                          "page-content")
                                 (catch Exception e nil))] ;; This is necessary as the http request will throw exceptions on 404, and some pages aren't written.
              (recur (concat (rest to-check) (extract-links next-para exclusions))
                     (conj visited next)
                     (assoc out next {:links (extract-links next-para exclusions)
                                      :author (extract-author next-para authors)}))
              (recur (rest to-check)
                     (conj visited next)
                     (assoc out next {})))))
        out))))

(defn graphify-one
  "Create graphviz dot language entries for given graph node (in internal map format)."
  [[key {:keys [links]}]]
  (apply str (map #(str \" key \" " -> " \" % \" ";\n") links)))

(defn node-def
  "Build a node definition for a given graph node. Form: <quoted-name> [color=<colour>,style=filled];\n. Omits colour/filled if no author given."
  [[key {:keys [author]}] author-colours]
  (str \" key \"
       (if-let [colour (get author-colours author)]
         (str " [color=" colour ",style=filled]")
         "")
       ";\n"))

(defn make-turn
  "Given a paticular turn (as a set) and a list of declarations, extract the declarations for that turn and build a ranked subgraph for them."
  [turn decls]
  (let [turn-decls (filter #(turn (nth % 2)) decls)]
    (str "{ rank=same;\n"
         (str (apply str (sort turn)) ";\n")
         (apply str turn-decls)
         "}\n")))

(defn make-order
  "Make a subgraph for the turn ordering."
  [turns]
  (str (apply str (interpose "->" (map #(apply str (sort %)) turns))) ";\n"))

(defn graphify-ranked
  "Convert a map representation of the graph into a string of graphviz dot language. Use a turn order to create a tiered graph."
  [g author-colours turns]
  (let [decls (map #(make-turn % (map (fn [a] (node-def a author-colours)) (seq g))) turns) 
        vals (map graphify-one (seq g))]
    (str "digraph G {\n"
         (make-order turns)
         (apply str decls)
         (apply str vals)
         "}\n")))

(defn graphify-unordered
  "Convert a map representation of the graph into a string of graphviz dot language. Just let dot do whatever its heart tells it."
  [g author-colours]
  (let [decls (map #(node-def % author-colours) (seq g))
        vals (map graphify-one (seq g))]
    (str "digraph G {\n"
         (apply str decls)
         (apply str vals)
         "}\n")))

(defn make-author-colours
  [authors]
  (reduce #(assoc %1 (first %2) (second %2))
                                 {}
                                 (map vector authors colours)))

(defn -main
  "Calculate and print graphs of OD-117 to ranked.dot and general.dot."
  [& args]
  (let [{:keys [wiki-address turns]} (edn/read (java.io.PushbackReader. (io/reader "data/od117.edn")))]
    (let [authors (extract-links (find-id (find-and-parse-page "scholars"
                                                               wiki-address)
                                          "page-content")
                                 #{})
          author-colours (make-author-colours authors)
          start-points (extract-links (find-id (find-and-parse-page "written-entries"
                                                                    wiki-address)
                                               "page-content")
                                      #{})
          g (walk start-points authors wiki-address)]
      (with-open [wrtr (io/writer "output/ranked.dot")]
        (.write wrtr (graphify-ranked g author-colours turns)))
      (with-open [wrtr (io/writer "output/general.dot")]
        (.write wrtr (graphify-unordered g author-colours))))))
