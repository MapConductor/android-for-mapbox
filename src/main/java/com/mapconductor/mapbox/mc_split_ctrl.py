import re, sys

path = sys.argv[1]
s = open(path, encoding='utf-8').read()
lines = s.split('\n')

def find_method(name, kind='private fun'):
    """クラス内メソッドの [start,end) 行番号（1 始まり、KDoc/コメント込み）を返す。"""
    pat = re.compile(r'(?m)^    (?:@\w+\s*\n    )?(?:override |private |internal |public |suspend )*fun ' + re.escape(name) + r'\b')
    m = pat.search(s)
    if not m:
        return None
    start_off = m.start()
    # 先行する KDoc / 行コメントを含める
    head_lines = s[:start_off].split('\n')
    k = len(head_lines) - 1
    while k > 0 and (head_lines[k-1].strip().startswith(('*','/**','//','*/'))):
        k -= 1
    start_line = k + 1
    # 本体の終わり
    i = s.index('{', m.end()) if '{' in s[m.end():m.end()+400] else None
    if i is None:
        return None
    depth = 0
    j = i
    while j < len(s):
        if s[j] == '{': depth += 1
        elif s[j] == '}':
            depth -= 1
            if depth == 0: break
        j += 1
    end_line = s[:j].count('\n') + 1
    return (start_line, end_line)

def extract(names):
    spans = []
    for n in names:
        sp = find_method(n)
        if sp: spans.append((n, sp))
        else: print(f'  not found: {n}')
    return spans

if __name__ == '__main__':
    for n in sys.argv[2:]:
        print(n, find_method(n))
