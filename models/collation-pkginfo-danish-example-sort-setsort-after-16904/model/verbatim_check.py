# Checks that the three blocks embedded in the repro test equal the package-info.java "Example Usages"
# blocks line by line (javadoc margin removed, HTML entities unescaped, Java text-block '\\' -> '\').
import re, sys
page, test = sys.argv[1], sys.argv[2]
src = open(page, encoding='utf-8').read().split('\n')
blocks, cur, first = [], None, None
for i, l in enumerate(src, 1):
    if '<pre><code class="language-java">' in l:
        cur, first = [], i + 1
        continue
    if cur is not None and '</code></pre>' in l:
        blocks.append((first, cur)); cur = None
        continue
    if cur is not None:
        c = re.sub(r'^\s*\* ?', '', l)
        c = c.replace('&lt;', '<').replace('&gt;', '>').replace('&#64;', '@').replace('&amp;', '&')
        cur.append(c.rstrip())
t = open(test, encoding='utf-8').read()
arr = t[t.index('PAGE_BLOCK = {'):]
emb = re.findall(r'"""\n(.*?)\n\s*"""', arr, re.S)
firsts = [int(x) for x in re.search(r'PAGE_FIRST_LINE = \{([^}]*)\}', t).group(1).split(',')]
ok = len(emb) == len(blocks) == 3
for k, (f, lines) in enumerate(blocks):
    e = [x.replace('\\\\', '\\').rstrip() for x in emb[k].split('\n')]
    # strip the common 6-space source indentation of the text block content, page has 2-space indent
    e = [x[4:] if x.startswith('    ') else x for x in e]
    same = e == lines
    print(f'block {k}: page first line {f} (test says {firsts[k]}), {len(lines)} lines, identical={same}')
    if not same:
        for a, b in zip(e, lines):
            if a != b: print('  test:', repr(a)); print('  page:', repr(b))
    ok &= same and f == firsts[k]
print('VERBATIM' if ok else 'MISMATCH')
sys.exit(0 if ok else 1)
