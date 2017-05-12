(ns od117-walker.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [pl.danieljanus.tagsoup :as ts]
            [clojure.java.io :as io]))

(defn find-and-parse-page
  [page]
  (->> page
       (str "http://od117.wikidot.com/")
       (client/get)
       (:body)
       (ts/parse-string)))

(defonce aardvark-html (find-and-parse-page "aardvark"))

(defn find-id
  [html id]
  (if (= (:id (second html)) id)
    html
    (loop [childs (ts/children html)]
      (if (and (seq childs) (not (string? (first childs))))
        (if (find-id (first childs) id)
          (find-id (first childs) id)
          (recur (rest childs)))
        nil))))

(def authors #{"/catalogatrix-maridia"
               "/isabella-yutani"
               "/john-9eb99c7f-e482-492f-8807-dc6f208b5c60"
               "/major-kaspar-parmenides"
               "/prof-cuthburt-robinson-smithe"
               "/qitik-von-gratzk"
               "/research-droid-8608c29d-d2e6-441e-9d6c-edaf932a7e82"})

(defn extract-links
  [html]
  (reduce (fn [ls h] (apply hash-set (concat ls
                                             (reduce (fn [ls h2] (if-not (string? h2)
                                                                   (if (and (= (first h2) :a)
                                                                            (not (authors (:href (second h2)))))
                                                                     (conj ls (:href (second h2)))
                                                                     ls)
                                                                   ls)) #{} (ts/children h)))))
          #{} (ts/children html)))

(defn extract-author
  [html]
  (reduce (fn [ls h] (apply hash-set (concat ls
                                             (reduce (fn [ls h2] (if-not (string? h2)
                                                                   (if (and (= (first h2) :a)
                                                                            (authors (:href (second h2))))
                                                                     (conj ls (:href (second h2)))
                                                                     ls)
                                                                   ls)) #{} (ts/children h)))))
          #{} (ts/children html)))

(defn walk
  [start-points exclusions]
  (loop [to-check start-points visited exclusions out {}]
    (if-let [next (first to-check)]
      (if (visited next)
        (recur (rest to-check) visited out)
        (do
          (println next)
          (if-let [next-para (try (find-id (find-and-parse-page next) "page-content") (catch Exception e nil))]
            (recur (concat (rest to-check) (extract-links next-para)) (conj visited next) (assoc out next {:links (extract-links next-para)
                                                                                                           :author (extract-author next-para)}))
            (recur (rest to-check) (conj visited next) out))))
      out)))

(def author-map {"/catalogatrix-maridia" "aquamarine"
                 "/isabella-yutani" "brown1"
                 "/john-9eb99c7f-e482-492f-8807-dc6f208b5c60" "burlywood1"
                 "/major-kaspar-parmenides" "cadetblue1"
                 "/prof-cuthburt-robinson-smithe" "chocolate1"
                 "/qitik-von-gratzk" "darkorchid2"
                 "/research-droid-8608c29d-d2e6-441e-9d6c-edaf932a7e82" "darkslategray1"})

(defn graphify-one
  [[key {:keys [links author]}]]
  (apply str (apply str (map #(str \" key \" " -> " \" % \" ";\n") links)) \" key \" " " "[color=" (get author-map (first author)) ",style=filled];\n"))

(defn graphify
  [g]
  (let [vals (map graphify-one (seq g))]
    (apply str "digraph G {" (apply str vals) "}")))

(def start-points ["/caseinogenic-blood-rose"
                   "/anachronistic-shrubs"
                   "/depths-of-anguish"
                   "/euclidiocite"
                   "/lesser-spotted-rampike-owl"
                   "/mendacious-islands"])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (with-open [wrtr (io/writer "foo.dot")]
    (.write wrtr (graphify (walk start-points (conj authors "/redacted"))))))
