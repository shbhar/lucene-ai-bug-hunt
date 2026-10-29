# SimpleQueryParser drops the '+', '|', quote or '(' typed directly after a phrase slop, so "a b"~2+c parses as phrase OR c and "a b"~2|c (default operator AND) as phrase AND c

Bug LFV-359 · Area: queryparser / SimpleQueryParser (near operator: consumePhrase phrase slop) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-359.html](../../bugs/LFV-359.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You parse "a b"~2+c with SimpleQueryParser and expect what its javadoc defines, the near query AND c, just as "a b"~2 +c and "a b"+c parse. Instead the parser skips the character right after the slop digits, so the '+' is lost and you get phrase OR c (on an index with the documents 'a b' and 'c' it matches 2 documents instead of 0); the same happens to '|' ("a b"~2|c becomes AND under default MUST), to a quote ("a b"~2"c d" becomes the phrase OR c OR d) and to '(' ("a b"~2(c+d) loses its group). The cause is an off-by-one in consumePhrase: parseFuzziness already stops on the delimiter that ends the slop, and the unconditional '++state.index' that was written to step over the closing quote then skips that delimiter.

The test shows that `SimpleQueryParser` skips the `+` typed directly after a phrase slop, so `"a b"~2+c` does not parse like `"a b"~2 +c`.

## Expected vs actual

Expected `+field:"a b"~2 +field:c` (phrase AND c); actual `field:"a b"~2 field:c` (phrase OR c), with `-ea` and `-da`.

## Files

- [src/org/apache/lucene/queryparser/simple/TestSimpleQueryParserSlopOperator.java](src/org/apache/lucene/queryparser/simple/TestSimpleQueryParserSlopOperator.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/facet/build/libs/lucene-facet-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:$LUCENE/lucene/queryparser/build/libs/lucene-queryparser-$V.jar
CP=$CP:$LUCENE/lucene/sandbox/build/libs/lucene-sandbox-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.queryparser.simple.TestSimpleQueryParserSlopOperator
```

The module(s) facet also need their third-party runtime jars; `./gradlew -p lucene/<module> dependencies` lists them.

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
