# UAX29URLEmailTokenizer ends a URL token at the first $ + ! * ' or, in the URL path, although both RFCs its grammar cites allow these characters there, so URLs such as https://en.wikipedia.org/wiki/Python_(programming_language) or http://example.com/a+b/c are split

Bug LFV-739 · Area: analysis / UAX29URLEmailTokenizer JFlex URL rules (path segments of http(s), scheme-less, ftp and file URLs; analysis-common) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run text such as 'see https://en.wikipedia.org/wiki/Python_(programming_language) now' or 'http://example.com/a+b/c' through UAX29URLEmailTokenizer, whose javadoc says URLs are 'tokenized according to the relevant RFCs', and expect each URL as one &lt;URL&gt; token, because RFC 3986 (sub-delims are allowed in path segments) and RFC 1738 (its 'safe' and 'extra' characters are allowed in hsegment and fsegment) both allow $ + ! * ' and, in a path. Instead the &lt;URL&gt; token stops right before the first such character ('https://en.wikipedia.org/wiki/Python_', then 'programming_language' as a word; 'http://example.com/a', then 'b' and 'c'), because the grammar's two path-segment rules use RFC 3986's narrow 'unreserved' set inside rules shaped after RFC 1738, so these characters are rejected under either RFC. The same characters in the query, fragment or userinfo stay inside the URL token, and nothing reports an error.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The &lt;URL&gt; token stops right before the first such character ('https://en.wikipedia.org/wiki/Python_', then 'programming_language' as a word; 'http://example.com/a', then 'b' and 'c'), because the grammar's two path-segment rules use RFC 3986's narrow 'unreserved' set inside rules shaped after RFC 1738, so these characters are rejected under either RFC.

## Files

- [src/org/apache/lucene/analysis/email/TestUrlPathSubDelims.java](src/org/apache/lucene/analysis/email/TestUrlPathSubDelims.java) (JUnit test)

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

# run 1: assertions on, seed 44A1C0DE
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=44A1C0DE \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.email.TestUrlPathSubDelims
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
