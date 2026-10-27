# MappingCharFilterFactory throws ArrayIndexOutOfBoundsException ('Index 256 out of bounds for length 256') when a mapping rule's source or target is longer than 256 characters, because it unescapes every rule string into a fixed char[256] buffer, so the analyzer cannot be built

Bug LFV-307 · Area: analysis / char filters (MappingCharFilterFactory mapping-file parser) · Priority: low · Impact: wrong error · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You configure the mapping char filter with a mapping file that contains one rule whose source or target is longer than 256 characters, for example "abc" =&gt; "&lt;257 x&gt;" or a long boilerplate phrase mapped to "", and expect the factory to build the map (NormalizeCharMap.Builder accepts such strings when called directly, and no limit is documented) or to reject the rule with an IllegalArgumentException that names it, as it does for other bad rules. Instead MappingCharFilterFactory.inform throws java.lang.ArrayIndexOutOfBoundsException: Index 256 out of bounds for length 256 from parseString, which copies each unescaped rule string into a fixed 256-character instance buffer without a bounds check. A 256-character string still works, and the limit counts characters after unescaping (256 \uNNNN escapes, 1536 raw characters, pass).

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: MappingCharFilterFactory.inform throws java.lang.ArrayIndexOutOfBoundsException: Index 256 out of bounds for length 256 from parseString, which copies each unescaped rule string into a fixed 256-character instance buffer without a bounds check.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestMappingCharFilterFactoryLongRule.java](src/org/apache/lucene/analysis/charfilter/TestMappingCharFilterFactoryLongRule.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.charfilter.TestMappingCharFilterFactoryLongRule
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
