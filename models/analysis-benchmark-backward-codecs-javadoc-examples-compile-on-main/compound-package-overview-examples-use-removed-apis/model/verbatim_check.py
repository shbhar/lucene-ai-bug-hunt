# Check that a repro's embedded text block equals the page's <pre><code> block (javadoc '*' prefix
# stripped, HTML entities decoded, indentation ignored), apart from listed allowed edits.
# Usage: python3 -I verbatim_check.py <package-info.java> <Test.java> [old=>new ...]
import html, re, sys
page = open(sys.argv[1], encoding='utf-8').read()
m = re.search(r'<pre><code class="language-java">\n(.*?)</code></pre>', page, re.S)
blk = [html.unescape(re.sub(r'^\s*\*\s?', '', l)).strip() for l in m.group(1).split('\n')]
blk = [l for l in blk if l]
test = open(sys.argv[2], encoding='utf-8').read()
tb = re.search(r'"""\n(.*?)"""', test, re.S).group(1).split('\n')
tb = [l.strip() for l in tb if l.strip()]
for e in sys.argv[3:]:
    old, new = e.split('=>'); blk = [l.replace(old, new) for l in blk]
# the test adds imports and a wrapper; the page lines must appear in order, contiguously
start = next(i for i, l in enumerate(tb) if l == blk[0])
seg = tb[start:start + len(blk)]
diff = [(a, b) for a, b in zip(blk, seg) if a != b]
print('page lines:', len(blk), 'matched contiguously from test line', start, '| differences:', len(diff))
for a, b in diff: print('  PAGE:', a, '\n  TEST:', b)
print('wrapper/import lines added by test:', [l for i, l in enumerate(tb) if not (start <= i < start + len(blk))])
sys.exit(1 if diff else 0)
