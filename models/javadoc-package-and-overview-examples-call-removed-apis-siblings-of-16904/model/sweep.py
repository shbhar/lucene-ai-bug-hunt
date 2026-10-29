#!/usr/bin/env python3
"""Write one block file per Java documentation code example (non-sandbox lucene/**/src/java) for Sweep.java.
Usage (from the Lucene checkout): sweep.py OUT_DIR FILE...   (block file: '# rel:line package' + block text)"""
import json, os, re, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
blocks = json.loads(subprocess.check_output([sys.executable, '-I', os.path.join(HERE, 'extract_blocks.py')] + sys.argv[2:]))
n = 0
for b in blocks:
    f = b['file']
    pkg = ''
    if f.endswith('.java'):
        m = re.search(r'^package\s+([\w.]+);', open(f, encoding='utf-8').read(), re.M)
        pkg = m.group(1) if m else ''
    elif '/src/java/' in f and not f.endswith('overview.html'):
        pkg = os.path.dirname(f.split('/src/java/', 1)[1]).replace('/', '.')
    n += 1
    with open(os.path.join(out, '%04d.txt' % n), 'w', encoding='utf-8') as g:
        g.write('# %s:%d %s\n' % (f, b['start_line'], pkg) + b['text'] + '\n')
print(n)
