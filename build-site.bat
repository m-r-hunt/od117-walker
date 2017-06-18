@echo off

if not exist "output" mkdir output

xcopy /y static\* output

call lein run

dot -Tpng .\output\ranked.dot -o"output/ranked.png"
dot -Tpng .\output\general.dot -o"output/general.png"

dot -Tpng .\output\at-ranked.dot -o"output/at-ranked.png"
dot -Tpng .\output\at-general.dot -o"output/at-general.png"

cd output
git add .
git commit -m "Updated graphs."
git push
