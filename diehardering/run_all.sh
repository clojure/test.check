#!/usr/bin/env bash

run_dieharder(){
  SEED=`date +%s`
  RUN_NAME="$1"
  OUTFILE="$RUN_NAME.txt"
  echo "Seed: $SEED" > $OUTFILE
  echo "Running $RUN_NAME"
  time lein print-random $SEED $RUN_NAME | dieharder -a -g 200 >> $OUTFILE
}

run_dieharder "JUR"
run_dieharder "siphash-right-linear"
run_dieharder "siphash-left-linear"
run_dieharder "siphash-alternating"
run_dieharder "siphash-balanced-63"
run_dieharder "siphash-balanced-64"
run_dieharder "siphash-right-lumpy"
run_dieharder "siphash-left-lumpy"
run_dieharder "siphash-fibonacci"
run_dieharder "AES-right-linear"
run_dieharder "AES-left-linear"
run_dieharder "AES-alternating"
run_dieharder "AES-balanced-63"
run_dieharder "AES-balanced-64"
run_dieharder "AES-right-lumpy"
run_dieharder "AES-left-lumpy"
run_dieharder "AES-fibonacci"
