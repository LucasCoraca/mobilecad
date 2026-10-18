#!/bin/bash
# Runs build_deps.sh steps in sequence for one ABI.  Usage: run_chain.sh <abi> <step>...
ABI=$1; shift
for s in "$@"; do /home/lucas/Documents/freecadmobile/engine/build_deps.sh "$s" "$ABI" || { echo "FAILED $s"; exit 1; }; done
