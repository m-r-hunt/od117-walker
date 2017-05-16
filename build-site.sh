mkdir -p output

lein run

dot -Tpng ./output/ranked.dot -o"output/ranked.png"
dot -Tpng ./output/general.dot -o"output/general.png"
