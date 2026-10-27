# FreeTextSuggester.build accepts a token that contains the separator byte whenever the shingled term stays within the gram limit, although its javadoc promises an IllegalArgumentException; later lookups whose last complete word occurs only inside that token throw AssertionError (-ea) or NullPointerException (-da)

Bug LFV-600 · Area: suggest / FreeTextSuggester (n-gram model joined with an in-band separator byte; build-side separator check) · Priority: low · Impact: wrong error · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a FreeTextSuggester with the default bigram model and separator U+001E over the single entry 'foo\u001ebar' (the upstream test input 'foo\u001ebar baz' without its neighbour 'baz'), using an analyzer that keeps it as one token such as UnicodeWhitespaceAnalyzer, and expect build to throw IllegalArgumentException, as the constructor javadoc promises and as it does for 'foo\u001ebar baz'. Instead build succeeds, because it only rejects an indexed shingle whose separator count implies more than grams words, so the token is stored as if it were the bigram (foo, bar) with no unigram 'foo'. A later lookup('foo ') then fails inside the suggester with AssertionError at FreeTextSuggester.java:576 (-ea) or NullPointerException in decodeWeight (-da), and lookup('f') returns the token itself with a wrong score.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: build succeeds, because it only rejects an indexed shingle whose separator count implies more than grams words, so the token is stored as if it were the bigram (foo, bar) with no unigram 'foo'.

## Files

- [src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSeparatorInToken.java](src/org/apache/lucene/search/suggest/analyzing/TestFreeTextSeparatorInToken.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/suggest/build/libs/lucene-suggest-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.suggest.analyzing.TestFreeTextSeparatorInToken
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
