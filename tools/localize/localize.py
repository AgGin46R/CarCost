# -*- coding: utf-8 -*-
"""
Вынос русских строк из кода в ресурсы.

Инструмент, а не разовый скрипт: 1712 строк в 123 файлах руками не переносятся
без ошибок, а каждая ошибка — это либо пропавшая надпись, либо несобирающийся
файл. Поэтому разбор строгий: литералы ищутся с учётом комментариев и
экранирования, а всё, что похоже на не-интерфейсную строку, пропускается и
перечисляется в отчёте, чтобы решение принимал человек, а не догадка.

Запуск:
    python localize.py <файл.kt> [ещё файлы...] --prefix home [--mode compose|context]

Состояние копится в registry.json: одинаковый русский текст в разных файлах
получает один ключ. Из реестра же собирается values/strings.xml.
"""

import io
import json
import os
import re
import sys

# ── Пути ─────────────────────────────────────────────────────────────────────
ROOT = "E:/CarCost/app/src/main"
REGISTRY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "registry.json")

CYR = re.compile(r"[\u0400-\u04FF]")
LIT = re.compile(r'"(?:[^"\\\n]|\\.)*"')
LOG = re.compile(r"\bLog\.[dewiv]\s*\(")

MODES = {
    "compose": "stringResource",
    "context": "context.getString",
    "vm": "getApplication<Application>().getString",
    "worker": "applicationContext.getString",
    "app": "app.getString",
}

# Строки, которые выглядят русскими, но интерфейсом не являются
SKIP_LINE = [
    re.compile(r"\bLog\."),
    re.compile(r"\bRegex\s*\("),
    re.compile(r"@(SerialName|ColumnInfo|Query|Entity|Index)"),
    re.compile(r"\broute\s*="),
    re.compile(r"\bcomposable\s*\("),
    re.compile(r"\bnavigate\s*\("),
    re.compile(r"\bprintln\s*\("),
    re.compile(r"\brequire\s*\(|\bcheck\s*\(|\berror\s*\("),
]

# ── Транслитерация для имён ключей ───────────────────────────────────────────
TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}


def slug(text, limit=42):
    """Имя ключа из текста: латиница, подчёркивания, без хвостов."""
    out = []
    for ch in text.lower():
        if ch in TRANSLIT:
            out.append(TRANSLIT[ch])
        elif ch.isalnum() and ord(ch) < 128:
            out.append(ch)
        else:
            out.append("_")
    s = re.sub(r"_+", "_", "".join(out)).strip("_")
    if len(s) > limit:
        s = s[:limit].rsplit("_", 1)[0]
    return s or "text"


# ── Разбор исходника ─────────────────────────────────────────────────────────
def blank_comments(src):
    """
    Комментарии заменяются пробелами: позиции символов сохраняются.

    Литералы проходят насквозь целиком — иначе `//` внутри строки (а в коде
    есть ссылки вида "https://…") оборвал бы её как начало комментария.
    """
    out, i, n = [], 0, len(src)
    while i < n:
        if src[i] == '"':
            found = find_literals(src[i:i + 4000])
            if found and found[0][0] == 0:
                out.append(src[i:i + found[0][1]])
                i += found[0][1]
                continue
            out.append(src[i]); i += 1; continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", src[i:j])); i = j; continue
        out.append(src[i]); i += 1
    return "".join(out)


def line_of(src, pos):
    start = src.rfind("\n", 0, pos) + 1
    end = src.find("\n", pos)
    return src[start:end if end >= 0 else len(src)]


def find_literals(src):
    """
    Границы строковых литералов с учётом вложенности.

    Простая регулярка `"[^"]*"` спотыкается о шаблон вида
    `"Всего ${"%.1f".format(x)} ₽"`: внутри подстановки живёт своя строка, и
    регулярка обрывает литерал на её кавычке. Дальше вынос резал выражение
    посередине — получался несобирающийся файл, причём в одном месте из
    девяти, то есть ошибку легко было бы и не заметить.

    Возвращает список (начало, конец, содержимое без кавычек).
    """
    out, i, n = [], 0, len(src)
    while i < n:
        ch = src[i]
        if ch == '"':
            if src.startswith('"""', i):          # многострочные не трогаем
                j = src.find('"""', i + 3)
                i = n if j < 0 else j + 3
                continue
            start, j, depth = i, i + 1, 0
            while j < n:
                c = src[j]
                if c == "\\" and depth == 0:
                    j += 2; continue
                if c == "$" and j + 1 < n and src[j + 1] == "{":
                    depth += 1; j += 2; continue
                if depth > 0:
                    if c == "{": depth += 1
                    elif c == "}": depth -= 1
                    elif c == '"':                # строка внутри подстановки
                        k, esc = j + 1, False
                        while k < n:
                            if esc: esc = False
                            elif src[k] == "\\": esc = True
                            elif src[k] == '"': break
                            elif src[k] == "\n": break
                            k += 1
                        j = k + 1; continue
                    j += 1; continue
                if c == '"':
                    out.append((start, j + 1, src[start + 1:j]))
                    break
                if c == "\n":
                    break
                j += 1
            i = j + 1
            continue
        i += 1
    return out


# ── Подстановки внутри строки ────────────────────────────────────────────────
IDENT = re.compile(r"\$([A-Za-z_][A-Za-z0-9_]*)")


def split_template(text):
    """
    Разбирает "Осталось $days дней" в ("Осталось %1$s дней", ["days"]).

    Тип аргумента всегда %s: он подходит и числам — форматирование зовёт у них
    toString. Числовой %d на Double уронил бы приложение во время работы, а не
    при сборке, то есть у пользователя.
    """
    args, out, i, n = [], [], 0, len(text)
    while i < n:
        ch = text[i]
        if ch == "%":
            out.append("%%"); i += 1; continue
        if ch == "\\" and i + 1 < n:
            out.append(text[i:i + 2]); i += 2; continue
        if ch == "$" and i + 1 < n and text[i + 1] == "{":
            depth, j = 1, i + 2
            while j < n and depth:
                if text[j] == "{": depth += 1
                elif text[j] == "}": depth -= 1
                j += 1
            args.append(text[i + 2:j - 1])
            out.append("%%%d$s" % len(args))
            i = j
            continue
        m = IDENT.match(text, i)
        if m:
            args.append(m.group(1))
            out.append("%%%d$s" % len(args))
            i = m.end()
            continue
        out.append(ch); i += 1
    return "".join(out), args


def xml_escape(text):
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"')
    return text


# ── Реестр ───────────────────────────────────────────────────────────────────
def load_registry():
    if os.path.exists(REGISTRY):
        return json.load(io.open(REGISTRY, encoding="utf-8"))
    return {"keys": {}, "order": []}


def save_registry(reg):
    io.open(REGISTRY, "w", encoding="utf-8", newline="\n").write(
        json.dumps(reg, ensure_ascii=False, indent=2)
    )


def key_for(reg, xml_text, prefix):
    """Один и тот же текст переиспользует ключ — переводить его дважды незачем."""
    if xml_text in reg["keys"]:
        return reg["keys"][xml_text]
    # Финальная чистка: имя ресурса — только строчная латиница, цифры и
    # подчёркивания. Раньше сюда однажды просочился перенос строки из префикса,
    # и сборка ресурсов упала спустя несколько партий, далеко от причины.
    base = re.sub(r"[^a-z0-9_]", "", "%s_%s" % (prefix, slug(re.sub(r"%\d+\$s", "", xml_text))))
    base = re.sub(r"_+", "_", base).strip("_") or "text"
    key, n = base, 2
    used = set(reg["keys"].values())
    while key in used:
        key = "%s_%d" % (base, n)
        n += 1
    reg["keys"][xml_text] = key
    reg["order"].append(xml_text)
    return key


# ── Обработка файла ──────────────────────────────────────────────────────────
def process(path, prefix, mode, reg, report):
    src = io.open(path, encoding="utf-8").read()
    scan = blank_comments(src)

    hits = []
    for start, end, text in find_literals(scan):
        if not CYR.search(text):
            continue
        line = line_of(scan, start)
        if any(p.search(line) for p in SKIP_LINE):
            report.append(("skip", path, text, line.strip()[:70]))
            continue
        hits.append((start, end, text))

    if not hits:
        return 0

    pieces, last = [], 0
    for start, end, text in hits:
        xml_text, args = split_template(text)
        key = key_for(reg, xml_text, prefix)
        # mode задаёт способ обращения к ресурсам: в композиции это
        # stringResource, во ViewModel — getApplication(), в Worker —
        # applicationContext. Универсального нет, поэтому вызывающий говорит
        # прямо, а не угадывается по имени файла.
        call = MODES.get(mode, mode)
        if args:
            call += "(R.string.%s, %s)" % (key, ", ".join(args))
        else:
            call += "(R.string.%s)" % key
        pieces.append(src[last:start])
        pieces.append(call)
        last = end
    pieces.append(src[last:])
    result = "".join(pieces)

    # Импорты
    need = []
    if mode == "vm" and "import android.app.Application" not in result:
        need.append("import android.app.Application")
    if mode == "compose" and "import androidx.compose.ui.res.stringResource" not in result:
        need.append("import androidx.compose.ui.res.stringResource")
    if "import com.aggin.carcost.R" not in result:
        need.append("import com.aggin.carcost.R")
    if need:
        m = re.search(r"^import .*$", result, re.M)
        if m:
            result = result[:m.start()] + "\n".join(need) + "\n" + result[m.start():]

    io.open(path + ".tmp", "w", encoding="utf-8", newline="\n").write(result)
    os.replace(path + ".tmp", path)
    return len(hits)


# ── Сборка strings.xml ───────────────────────────────────────────────────────
HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Русский — основной язык. Остальные лежат в values-en, values-be, values-kk.

  Имена ключей описывают место и смысл, а не текст: action_save, а не save_text.
  Когда формулировка меняется, ключ остаётся прежним, и переводы не рассыпаются.
-->
<resources>
"""


def write_strings(reg, manual):
    lines = [HEADER]
    for line in manual:
        lines.append(line)
    lines.append("\n    <!-- Вынесено из кода -->\n")
    for text in reg["order"]:
        key = reg["keys"][text]
        lines.append('    <string name="%s">%s</string>\n' % (key, xml_escape(text)))
    lines.append("</resources>\n")
    path = os.path.join(ROOT, "res/values/strings.xml")
    io.open(path + ".tmp", "w", encoding="utf-8", newline="\n").write("".join(lines))
    os.replace(path + ".tmp", path)


def manual_block():
    """Строки, заведённые руками, сохраняются при пересборке файла."""
    path = os.path.join(ROOT, "res/values/strings.xml")
    if not os.path.exists(path):
        return []
    src = io.open(path, encoding="utf-8").read()
    body = src.split("<resources>", 1)[-1]
    body = body.split("<!-- Вынесено из кода -->")[0]
    body = body.replace("</resources>", "")
    return [l + "\n" for l in body.rstrip().split("\n") if l.strip()]


def seed_manual(reg):
    """
    Строки, заведённые руками, попадают в реестр как уже известные.

    Иначе «Повторить» из кода получило бы собственный ключ рядом с уже
    существующим action_retry, и один и тот же текст пришлось бы переводить
    дважды на каждый из трёх языков — а потом расходиться в формулировках.
    """
    path = os.path.join(ROOT, "res/values/strings.xml")
    if not os.path.exists(path):
        return
    src = io.open(path, encoding="utf-8").read()
    head = src.split("<!-- Вынесено из кода -->")[0]
    for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', head, re.S):
        key, text = m.group(1), m.group(2)
        if key == "app_name":
            continue
        reg["keys"].setdefault(text, key)


if __name__ == "__main__":
    args = sys.argv[1:]
    prefix, mode, files = "app", "compose", []
    i = 0
    while i < len(args):
        if args[i] == "--prefix":
            # Чистим: имя ресурса — только строчные латинские и подчёркивания.
            # Хвостовой возврат каретки из файла со списком однажды уже попал
            # внутрь ключей и уронил сборку ресурсов, а не компиляцию — то есть
            # обнаружился далеко от места ошибки.
            prefix = re.sub(r"[^a-z0-9_]", "", args[i + 1].strip().lower()) or "app"
            i += 2
        elif args[i] == "--mode":
            mode = args[i + 1]; i += 2
        else:
            files.append(args[i]); i += 1

    reg = load_registry()
    manual = manual_block()
    seed_manual(reg)
    report, total = [], 0
    for f in files:
        n = process(f, prefix, mode, reg, report)
        total += n
        print("%-70s %d" % (os.path.basename(f), n))
    save_registry(reg)
    write_strings(reg, manual)

    skipped = [r for r in report if r[0] == "skip"]
    if skipped:
        print("\nпропущено (проверить вручную):")
        for _, path, text, line in skipped[:40]:
            print("  %s | %s" % (text[:40], line))
    print("\nвсего вынесено: %d, ключей в реестре: %d" % (total, len(reg["keys"])))
