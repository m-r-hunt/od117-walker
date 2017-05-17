(ns od117-walker.core
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            
            [clj-http.client :as client]
            [pl.danieljanus.tagsoup :as ts]))

;; TODO: Reduce amount of data needed by either: scraping it from the wiki (authors, start points) or generating automatically (author-colours).
;;       Also consider whether or not dynamic variables are a good idea, probably should just pass stuff down but this was an easy way to replace hardcoded data.

;; Dynamic variables, need to be bound for code to work.

;; Base address of wiki to walk.
(def ^:dynamic *wiki-address*)

;; Set of authors to ignore, since we don't want them on the final graph.
(def ^:dynamic *authors*)

;; Colours used for different author's bubbles.
;; Manually chosen for maximum style.
(def ^:dynamic *author-colours*)

;; Turn order. Manually transcribed from wiki.
(def ^:dynamic *turns*)

;; Start points that will ensure we hit the whole graph.
;; Manually entered, a little bit of a hack.
(def ^:dynamic *start-points*)

(defn find-and-parse-page
  "Download the given wiki page and run it through the parser."
  [page]
  (->> page
       (str *wiki-address*)
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
  [html]
  (reduce (fn [ls h] (apply hash-set
                            (concat ls
                                    (reduce (fn [ls h2] (if-not (string? h2)
                                                          (if (and (= (first h2) :a)
                                                                   (not (*authors* (:href (second h2)))))
                                                            (conj ls (:href (second h2)))
                                                            ls)
                                                          ls))
                                            #{}
                                            (ts/children h)))))
          #{}
          (ts/children html)))

;; TODO: Refactor these 2 functions as they are copy/pasted.
(defn extract-author
  "Extract the author of the entry from the html. Assumes there is only one author linked."
  [html]
  (first (reduce (fn [ls h] (apply hash-set
                                   (concat ls
                                           (reduce (fn [ls h2] (if-not (string? h2)
                                                                 (if (and (= (first h2) :a)
                                                                          (*authors* (:href (second h2))))
                                                                   (conj ls (:href (second h2)))
                                                                   ls)
                                                                 ls))
                                                   []
                                                   (ts/children h)))))
                 []
                 (ts/children html))))

(defn walk
  "Walk through the wiki building a graph representation. Takes a list of starting pages and a set of pages to ignore."
  [start-points exclusions]
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
                               (find-id (find-and-parse-page next) "page-content")
                               (catch Exception e nil))] ;; This is necessary as the http request will throw exceptions on 404, and some pages aren't written.
            (recur (concat (rest to-check) (extract-links next-para))
                   (conj visited next)
                   (assoc out next {:links (extract-links next-para)
                                    :author (extract-author next-para)}))
            (recur (rest to-check)
                   (conj visited next)
                   (assoc out next {})))))
      out)))

(defn graphify-one
  "Create graphviz dot language entries for given graph node (in internal map format)."
  [[key {:keys [links]}]]
  (apply str (map #(str \" key \" " -> " \" % \" ";\n") links)))

(defn node-def
  "Build a node definition for a given graph node. Form: <quoted-name> [color=<colour>,style=filled];\n. Omits colour/filled if no author given."
  [[key {:keys [author]}]]
  (str \" key \"
       (if-let [colour (get *author-colours* author)]
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
  []
  (str (apply str (interpose "->" (map #(apply str (sort %)) *turns*))) ";\n"))

(defn graphify-ranked
  "Convert a map representation of the graph into a string of graphviz dot language. Use a turn order to create a tiered graph."
  [g]
  (let [decls (map #(make-turn % (map node-def (seq g))) *turns*) 
        vals (map graphify-one (seq g))]
    (str "digraph G {\n"
         (make-order)
         (apply str decls)
         (apply str vals)
         "}\n")))

(defn graphify-unordered
  "Convert a map representation of the graph into a string of graphviz dot language. Just let dot do whatever its heart tells it."
  [g]
  (let [decls (map node-def (seq g))
        vals (map graphify-one (seq g))]
    (str "digraph G {\n"
         (apply str decls)
         (apply str vals)
         "}\n")))

(defn -main
  "Calculate and print graphs of OD-117 to ranked.dot and general.dot."
  [& args]
  (let [{:keys [wiki-address authors author-colours turns start-points]} (edn/read (java.io.PushbackReader. (io/reader "data/od117.edn")))]
    (binding [*wiki-address* wiki-address
              *authors* authors
              *author-colours* author-colours
              *turns* turns
              *start-points* start-points]
      (let [g (walk start-points authors)]
        (with-open [wrtr (io/writer "output/ranked.dot")]
          (.write wrtr (graphify-ranked g)))
        (with-open [wrtr (io/writer "output/general.dot")]
          (.write wrtr (graphify-unordered g)))))))
