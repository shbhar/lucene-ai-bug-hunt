"""Static scan: find Java methods in which one builder variable has build() called, then add*/set* called,
then build() called again (a reused builder). Evidence for whether Lucene code relies on builder reuse.
Usage: python3 -I reuse_scan.py <root> [<root>...]"""
import os, re, sys
call = re.compile(r'\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\.\s*(build|add[A-Za-z]*|set[A-Za-z]*)\s*\(')
hits = []
for root in sys.argv[1:]:
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if not f.endswith('.java'):
                continue
            p = os.path.join(dp, f)
            try:
                src = open(p, encoding='utf-8', errors='replace').read()
            except OSError:
                continue
            # split crude: by method-ish blocks using lines; track per variable state across file lines
            state = {}
            for ln, line in enumerate(src.splitlines(), 1):
                if re.match(r'\s*(public|private|protected|static|void)\b.*\)\s*(throws [^{]*)?\{\s*$', line):
                    state = {}
                for v in list(state):
                    if re.search(r'\b' + re.escape(v) + r'\s*=\s*(new\b|[A-Za-z_.]*[Bb]uilder\s*\()', line):
                        state[v] = 0
                if re.search(r'\b(class|interface|record)\s+\w+', line):
                    state = {}
                for m in call.finditer(line):
                    v, op = m.group(1), m.group(2)
                    s = state.get(v, 0)
                    if op == 'build':
                        if s == 2:
                            hits.append((p, ln, v, line.strip()))
                        state[v] = 1
                    elif s >= 1:
                        state[v] = 2
for h in hits:
    print('%s:%d %s :: %s' % h)
print('TOTAL', len(hits))
