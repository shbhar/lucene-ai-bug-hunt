# DictionaryCompoundWordTokenFilterFactory (main only) warns 'onlyLongestMatch has been removed' when the key is absent and stays silent when onlyLongestMatch="false" is passed

Bug LFV-439 · Area: analysis-common / compound word factory (DictionaryCompoundWordTokenFilterFactory parameter handling) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build DictionaryCompoundWordTokenFilterFactory on main without the onlyLongestMatch key, for example with only onlyLongestMatchIgnoreSubwords, and expect no message about a key you never used; instead every construction logs the WARNING 'onlyLongestMatch has been removed and replaced by onlyLongestMatchIgnoreSubwords'. A configuration that does pass onlyLongestMatch="false", the one setting whose meaning is now ignored, gets no warning, because the factory reads the removed key with default true and warns when the value is true (getBoolean(args, "onlyLongestMatch", true); if (onlyLongestMatch) LOG.warning(...)), not when the key is present. Only the log is wrong: the tokens are the same with or without the warning.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/compound/TestDictCompoundFactoryRemovedKeyWarning.java](src/org/apache/lucene/analysis/compound/TestDictCompoundFactoryRemovedKeyWarning.java) (JUnit test)

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
     execute --select-class org.apache.lucene.analysis.compound.TestDictCompoundFactoryRemovedKeyWarning
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-spi-chain-fuzz/](../../models/analysis-spi-chain-fuzz/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
