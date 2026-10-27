# RandomSamplingFacetsCollector.amortizeFacetCounts caps each amortized label count at the docFreq of the label's drill-down term, so with a non-default FacetsConfig DrillDownTermsIndexing (NONE on any dimension, or FULL_PATH_ONLY / DIMENSION_AND_FULL_PATH on a hierarchical dimension) it silently returns 0, or a value below the true count

Bug LFV-425 · Area: facet / sampling (RandomSamplingFacetsCollector.amortizeFacetCounts x FacetsConfig drill-down term indexing) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-425.html](../../bugs/LFV-425.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You sample facet counts with RandomSamplingFacetsCollector on a dimension whose FacetsConfig skips drill-down terms (DrillDownTermsIndexing NONE, or FULL_PATH_ONLY / DIMENSION_AND_FULL_PATH on a hierarchical dimension), call amortizeFacetCounts to scale the sampled counts up to estimates of the full counts, and expect what the default indexing gives: about sampled / samplingRate per label, capped by the label's real count. Instead each label is capped at reader.docFreq of its drill-down term, which these modes do not index at all (docFreq 0) or index only for the documents whose full path is that label, so a label seen 15 times in a 10% sample comes back as 0 and a label on 1000 documents comes back as 500. Nothing fails, and in the same FacetResult the amortized dimension total (for example 1000) no longer matches its children (all 0).

## Expected vs actual

- Expected: Each amortized count between min(true count, estimate) and the estimate: label 3 of `iMod10` gives 100 (estimate 150, 100 documents), as the default configuration does; mixed-depth `x` gives 1000.
- Actual: 0 for all ten labels of `iMod10` (total still 1000), 0 for the parent labels `x` and `y`, and 500 for mixed-depth `x` (true count 1000).

## Files

- [src/org/apache/lucene/facet/TestAmortizeFacetCountsDrillDownTerms.java](src/org/apache/lucene/facet/TestAmortizeFacetCountsDrillDownTerms.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.facet.TestAmortizeFacetCountsDrillDownTerms
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestSamplingAmortizeDrillDownTerms.java](TestSamplingAmortizeDrillDownTerms.java)
- [TestAmortizeFacetCountsDrillDownTerms.java](TestAmortizeFacetCountsDrillDownTerms.java)
- [TestSamplingAmortizeDrillDownIndexingV1.java](TestSamplingAmortizeDrillDownIndexingV1.java)
- [TestAmortizeIntendedProbe.java](TestAmortizeIntendedProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
