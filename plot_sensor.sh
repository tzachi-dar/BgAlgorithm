#!/bin/sh
echo set autoscale >plotfile.p
echo unset log >>plotfile.p
echo unset label >>plotfile.p
echo set xtic 1 >>plotfile.p
echo set ytic 20 >>plotfile.p
echo set title \"Sensor $1\" >>plotfile.p
echo set xlabel \"Time in days\" >>plotfile.p
echo set ylabel \"BG\" >>plotfile.p
echo set key right top >>plotfile.p
echo set grid >>plotfile.p
echo plot \"sensor$1_raw.csv\" using 1:2 title \'raw values\' with linespoints, \\ >>plotfile.p
echo      \"sensor$1_calib.csv\" using 1:2 title \'calibration\' with points >>plotfile.p
echo pause -1 >>plotfile.p
gnuplot plotfile.p
