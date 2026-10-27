# SpanishPluralStemmer's special-case list has the singular 'contrarreloj' where the plural 'contrarrelojes' belongs, so the singular is cut to 'contrarrel', the plural stems to 'contrarreloje', and the two forms never match

Bug LFV-688 · Area: analysis-common / es (SpanishPluralStemmer special-case list, SpanishPluralStemFilter) · Priority: low · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You stem Spanish text with SpanishPluralStemFilter, which CHANGES describes as 'precise stemming of Spanish plurals', and expect the plural 'contrarrelojes' to reduce to 'contrarreloj' and the singular 'contrarreloj' to stay as it is, as 'relojes' -&gt; 'reloj' and 'bojes' -&gt; 'boj' do. Instead the singular becomes 'contrarrel' and the plural 'contrarreloje', so a document containing 'contrarrelojes' is not found by the query 'contrarreloj'. The cause is one entry of the hand-written special-case list (SpanishPluralStemmer.java:163): the 12 other entries are plurals in -es whose last two letters stem removes (:173, 'return len - 2'), but this entry is the singular, and the missing plural falls through to the generic 'remove the final s' rule (:222-224).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The singular becomes 'contrarrel' and the plural 'contrarreloje', so a document containing 'contrarrelojes' is not found by the query 'contrarreloj'.

## Files

- [src/org/apache/lucene/analysis/es/TestSpanishPluralContrarreloj.java](src/org/apache/lucene/analysis/es/TestSpanishPluralContrarreloj.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.es.TestSpanishPluralContrarreloj
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-rule-tables-second-batch/](../../models/analysis-rule-tables-second-batch/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
