(ns od117-walker.core-test
  (:require [od117-walker.core :refer :all]
            [od117-walker.core-test-data :as data]

            [clojure.test :as t]))

(t/deftest test-find-and-parse-page
  (with-redefs [clj-http.client/get (fn [addr] (if (= addr "http://some-wiki.example.com/aardvark") data/aardvark-req))]
    (t/is (= (find-and-parse-page "aardvark" "http://some-wiki.example.com/")
             data/aardvark-html))))

(t/deftest test-find-id
  (t/is (= (find-id data/aardvark-html "page-content") data/aardvark-content)))

(t/deftest test-extract-links
  (t/is (= (extract-links data/aardvark-content #{})
           #{"/prof-cuthburt-robinson-smithe" "/parasumatra" "/plasma-ants" "javascript:;"}))
  (t/is (= (extract-links data/aardvark-content
                          #{"/prof-cuthburt-robinson-smithe" "javascript:;"})
           #{"/parasumatra" "/plasma-ants"})))

(t/deftest test-extract-author
  (t/is (= (extract-author data/aardvark-content
                           #{"/prof-cuthburt-robinson-smithe"})
           "/prof-cuthburt-robinson-smithe")))

(defn mock-fapp
  [page _]
  (case page
    "/foo" [:html {} [:body {} [:div {:id "page-content"} [:a {:shape "rect", :href "/bar"}] [:a {:shape "rect", :href "/baz"}] [:a {:shape "rect", :href "/alice"}]]]]
    "/bar" [:html {} [:body {} [:div {:id "page-content"} [:a {:shape "rect", :href "/baz"}] [:a {:shape "rect", :href "/baz"}] [:a {:shape "rect", :href "/bob"}]]]]
    :default (throw Exception)))

(def test-graph {"/foo" {:links #{"/bar" "/baz"} :author "/alice"}
                 "/bar" {:links #{"/baz"} :author "/bob"}
                 "/baz" {}})

(t/deftest test-walk
  (t/is (= (with-redefs [find-and-parse-page mock-fapp]
             (walk ["/foo"] #{"/alice" "/bob"} "http://some-wiki.example.com/"))
           test-graph)))

(t/deftest test-make-author-colours
  (let [test-authors #{"/alice" "/bob"}
        acs (make-author-colours test-authors)]
    (t/is (map? acs))
    (t/is (every? test-authors (keys acs)))
    (t/is (every? (into #{} colours) (vals acs)))
    (t/is (apply distinct? (vals acs)))))

(def test-author-colours {"/alice" "brown1", "/bob" "aquamarine"})
(def test-turns [#{\a \b \c} #{\d \e \f}])

;; Note about these graph tests - the line order doesn't matter and could
;; change. If they break for that reason it may be worth rewriting to
;; be independent of order.

(t/deftest test-graphify-ranked
  (t/is (= (graphify-ranked test-graph
                            test-author-colours
                            test-turns)
           "digraph G {
abc->def;
{ rank=same;
abc;
\"/bar\" [color=aquamarine,style=filled];
\"/baz\";
}
{ rank=same;
def;
\"/foo\" [color=brown1,style=filled];
}
\"/foo\" -> \"/baz\";
\"/foo\" -> \"/bar\";
\"/bar\" -> \"/baz\";
}
")))

(t/deftest test-graphify-unordered
  (t/is (= (graphify-unordered test-graph test-author-colours)
           "digraph G {
\"/foo\" [color=brown1,style=filled];
\"/bar\" [color=aquamarine,style=filled];
\"/baz\";
\"/foo\" -> \"/baz\";
\"/foo\" -> \"/bar\";
\"/bar\" -> \"/baz\";
}
")))
