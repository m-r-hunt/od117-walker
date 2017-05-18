@echo off

if not exist "output" mkdir output

xcopy /y static\* output

call lein run

dot -Tpng .\output\ranked.dot -o"output/ranked.png"
dot -Tpng .\output\general.dot -o"output/general.png"
