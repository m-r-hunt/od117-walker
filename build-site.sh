#!/bin/sh

mkdir -p output

cp static/* output

lein run

dot -Tpng ./output/ranked.dot -o"output/ranked.png"
dot -Tpng ./output/general.dot -o"output/general.png"
