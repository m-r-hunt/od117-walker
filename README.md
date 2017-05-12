# od117-walker

Graph visualiser for OD-117: http://od117.wikidot.com

Can be seen online here: http://mechtoast.com/od117-walker/

Needs Clojure/Lein and graph-viz.

To generate:
```
lein run # Dumps output to foo.dot
dot -Tpng .\foo.dot -o"foo.png"
```
