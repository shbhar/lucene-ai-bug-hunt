# CSVUtil.parse decodes every quoted field except the last one of the line, so a quoted last field (for example the part-of-speech of a kuromoji user-dictionary entry) keeps its quote characters

Bug LFV-895 · Area: analysis-common / util CSVUtil (CSV line parsing for the kuromoji user dictionary and the kuromoji/nori dictionary builders) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-895.html](../../bugs/LFV-895.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse a CSV line whose last field is quoted, for example CSVUtil.parse("x," + CSVUtil.quoteEscape("To be, or not to be")), or load a kuromoji user dictionary written with every field quoted, and expect the quoted value to be decoded the same way as in any other position, as parse does for every field followed by a comma. Instead the last field comes back raw, enclosing quotes and doubled inner quotes included: parse("\"a,b\",\"c,d\"") returns [a,b, "c,d"], and the dictionary line "abcd","a b cd","foo1 foo2 foo3","bar" yields tokens whose part-of-speech is "bar" with the quotes. The cause is one line: parse passes each comma-terminated field through unQuoteUnEscape (CSVUtil.java:56) but adds the final field as sb.toString (:65).

## Expected vs actual

- Expected: `parse("\"p,q\",\"p,q\",\"p,q\"")` gives `[p,q, p,q, p,q]`, and the fully quoted dictionary entry gives part-of-speech `bar`.
- Actual: `[p,q, p,q, "p,q"]` and part-of-speech `"bar"`: 3 of 3 and 2 of 3 tests fail, with assertions on and off.

## Files

- [src/org/apache/lucene/analysis/util/TestCSVUtilLastField.java](src/org/apache/lucene/analysis/util/TestCSVUtilLastField.java) (JUnit test)
- [src/org/apache/lucene/analysis/ja/TestUserDictionaryQuotedFields.java](src/org/apache/lucene/analysis/ja/TestUserDictionaryQuotedFields.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/kuromoji/build/libs/lucene-analysis-kuromoji-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.util.TestCSVUtilLastField

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.ja.TestUserDictionaryQuotedFields
```

The module(s) analysis-kuromoji also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestCSVUtilQuotedLastField.java](TestCSVUtilQuotedLastField.java)
- [TestUserDictionaryQuotedPartOfSpeech.java](TestUserDictionaryQuotedPartOfSpeech.java)
- [TestCSVUtilLastField.java](TestCSVUtilLastField.java)
- [TestQuotedLastField.java](TestQuotedLastField.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
