#!/bin/sh
# Do Lucene's 11.0 upgrade notes say that incremental doc-values updates are now on by default?
# Usage: sh check_11_0_notes.sh "$LUCENE"      ($LUCENE: a Lucene checkout of main or branch_11x)
# Exit 0 when the 11.0.0 section of CHANGES.txt or MIGRATE.md has a line with one of the search terms
# (16418, MaxDocValuesOverlays, overlay), 1 when neither does. A term search: read the sections too.
set -eu
cd "$1/lucene"
IWC=core/src/java/org/apache/lucene/index/IndexWriterConfig.java
echo "code default: $(grep -o 'DEFAULT_MAX_DOC_VALUES_OVERLAYS = [0-9]*' "$IWC")"
# the CHANGES.txt lines naming GITHUB#16418, Opt-in; or MaxDocValuesOverlays, with their release section
awk '/^=+ Lucene [0-9.]+ =+$/ { sec = $3 }
     /GITHUB#16418|Opt-in;|MaxDocValuesOverlays/ { printf "CHANGES.txt:%d [%s] %s\n", NR, sec, $0 }' CHANGES.txt
n11=$(awk '/^=+ Lucene [0-9.]+ =+$/ { on = ($3 == "11.0.0") }
           on && /16418|MaxDocValuesOverlays|[Oo]verlay/' CHANGES.txt | wc -l)
nmig=$(grep -c -i 'MaxDocValuesOverlays\|overlay' MIGRATE.md || true)
echo "lines on it in the 11.0.0 section of CHANGES.txt: $n11"
echo "lines on it in MIGRATE.md: $nmig"
if [ "$n11" -gt 0 ] || [ "$nmig" -gt 0 ]; then
  echo "OK: the 11.0 notes record the feature"
else
  echo "MISSING: no 11.0 note says the default changed"
  exit 1
fi
