# The Kleene star of the empty language returns the empty language instead of {""}

Bug LFV-020 · Area: util / automaton · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-020.html](../../bugs/LFV-020.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a RegExp or call Operations.repeat on an operand whose language is empty, such as (a&amp;b)*. You expect the result to accept the empty string, since the star of any language contains it. Instead the result accepts nothing.

## Expected vs actual

- Expected: `repeat(makeEmpty)`, `#*`, `(a&b)*` and `(#){0,}` accept `""`, `a(a&b)*` accepts `a`, and `RegexpQuery` counts 1 hit for `(a&b)*` and 1 for `a(a&b)*`.
- Actual: All of them reject (`states=0... false`) and `RegexpQuery` counts 0 and 0, with no error; the 1-state empty automaton, `(a&b)?` and `(a&b){0,5}` are correct.

## Files

- [src/TestKleeneStarOfEmptyLanguage.java](src/TestKleeneStarOfEmptyLanguage.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main b83101cdd2 (2026-10-23): reproduced.
- Recorded outcome of run 1 on main a9779ef3c193: 10 test(s) run, 9 failed (testBoundedRepeatIsSubsetOfStar, testConcatenationAmplifiesToEmptyLanguage, testOptionalIsSubsetOfStar, testRegExpEmptyCharClassStarMatchesJavaRegex, testRegExpStarAgreesWithBoundedForms, testRegexpQueryOnEmptyTerm).
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestKleeneStarOfEmptyLanguage
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [KleeneStarOfEmpty.java](KleeneStarOfEmpty.java)
- [TestKleeneStarOfEmptyLanguage.java](TestKleeneStarOfEmptyLanguage.java)
- [test-regexp.diff](test-regexp.diff)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
