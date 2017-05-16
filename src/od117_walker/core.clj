(ns od117-walker.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clj-http.client :as client]
            [pl.danieljanus.tagsoup :as ts]))

;; TODO: Parameterise all data in defs, add code to read it from a config file. This will allow this program to be reused for future wikis.

;; Base address of wiki to walk.
(def wiki-address "http://od117.wikidot.com/")

(defn find-and-parse-page
  "Download the given wiki page and run it through the parser."
  [page]
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

;; Set of authors to ignore, since we don't want them on the final graph.
(def authors #{"/catalogatrix-maridia"
               "/isabella-yutani"
               "/john-9eb99c7f-e482-492f-8807-dc6f208b5c60"
               "/major-kaspar-parmenides"
               "/prof-cuthburt-robinson-smithe"
               "/qitik-von-gratzk"
               "/research-droid-8608c29d-d2e6-441e-9d6c-edaf932a7e82"})

(defn extract-links
  "Extract the links from the given html. Ignores authors."
  [html]
  (reduce (fn [ls h] (apply hash-set
                            (concat ls
                                    (reduce (fn [ls h2] (if-not (string? h2)
                                                          (if (and (= (first h2) :a)
                                                                   (not (authors (:href (second h2)))))
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
                                                                          (authors (:href (second h2))))
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

;; Colours used for different author's bubbles.
;; Manually chosen for maximum style.
(def author-colours {"/catalogatrix-maridia" "aquamarine"
                     "/isabella-yutani" "brown1"
                     "/john-9eb99c7f-e482-492f-8807-dc6f208b5c60" "burlywood1"
                     "/major-kaspar-parmenides" "cadetblue1"
                     "/prof-cuthburt-robinson-smithe" "chocolate1"
                     "/qitik-von-gratzk" "darkorchid2"
                     "/research-droid-8608c29d-d2e6-441e-9d6c-edaf932a7e82" "forestgreen"})

(defn graphify-one
  "Create graphviz dot language entries for given graph node (in internal map format)."
  [[key {:keys [links]}]]
  (apply str (map #(str \" key \" " -> " \" % \" ";\n") links)))

(defn node-def
  "Build a node definition for a given graph node. Form: <quoted-name> [color=<colour>,style=filled];\n. Omits colour/filled if no author given."
  [[key {:keys [author]}]]
  (str \" key \"
       (if-let [colour (get author-colours author)]
         (str " [color=" colour ",style=filled]")
         "")
       ";\n"))

;; Turn order. Manually transcribed from wiki.
(def turns [#{\a \b \c}
            #{\d \e \f}
            #{\g \h \i}
            #{\j \k \l}
            #{\m \n \o}
            #{\p \q}
            #{\r \s}
            #{\t \u \v}
            #{\w \x \y \z}])

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
  (str (apply str (interpose "->" (map #(apply str (sort %)) turns))) ";\n"))

(defn graphify-ranked
  "Convert a map representation of the graph into a string of graphviz dot language. Use a turn order to create a tiered graph."
  [g]
  (let [decls (map #(make-turn % (map node-def (seq g))) turns) 
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

;; Start points that will ensure we hit the whole graph.
;; Manually entered, a little bit of a hack.
(def start-points ["/caseinogenic-blood-rose"
                   "/anachronistic-shrubs"
                   "/depths-of-anguish"
                   "/euclidiocite"
                   "/lesser-spotted-rampike-owl"
                   "/mendacious-islands"])

(defn -main
  "Calculate and print graphs of OD-117 to ranked.dot and general.dot."
  [& args]
  (let [g (walk start-points authors)]
    (with-open [wrtr (io/writer "output/ranked.dot")]
      (.write wrtr (graphify-ranked g)))
    (with-open [wrtr (io/writer "output/general.dot")]
      (.write wrtr (graphify-unordered g)))))
