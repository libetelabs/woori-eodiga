"""접수양식 01 참가신청서와 04 서명표를 채운다.

사용: python3 tools/fill_application.py <채울값.json> <양식.docx> <출력.docx>

02 장을 채우는 fill_form.py 와 달리 여기서는 양식이 이미 글을 담고 있다
("대표자 성명: ______"). 밑줄만 골라 바꾸려 하면 run 이 쪼개져 있어 어긋난다.
그래서 문단 단위로 run 을 통째로 새로 쓰되 문단 속성(pPr)과 글꼴(rPr)은 남긴다.

문단 번호는 양식마다 다르다(02 장 분량이 달라서). 그래서 번호를 코드에 박지 않고
json 으로 받는다. 번호는 아래 명령으로 찍어 확인한다.

    python3 -c "import zipfile,re;x=zipfile.ZipFile(D).read('word/document.xml').decode();\
    [print(i,''.join(re.findall(r'<w:t[^>]*>(.*?)</w:t>',p,re.S))) \
     for i,p in enumerate(re.findall(r'<w:p[ >].*?</w:p>',x,re.S))]"

json 모양:
    {"lines": {"13": "대표자 성명: ..."},
     "signs": [{"para": 274, "png": "..."}, {"para": 279, "png": "..."}]}
sign 은 목록이다. 서명은 본인이 해야 하지만, 본인이 그림 파일로 보내 오면
그 자리에 넣는다. 동의 의사는 별도로 글로 받아 둘 것. 양식이 "참조수신만으로
동의를 대체하지 않습니다"라고 하므로 파일만으로는 기록이 남지 않는다.
"""
import html
import json
import re
import sys
import zipfile

SPEC, SRC, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
spec = json.load(open(SPEC, encoding="utf-8"))
EMU = 9525          # px → EMU
DISP_W = 150        # 서명을 칸에 맞춘 표시 너비(px)

doc = zipfile.ZipFile(SRC).read("word/document.xml").decode()
paras = re.findall(r"<w:p[ >].*?</w:p>", doc, re.S)


def rewrite(p: str, text: str, extra: str = "") -> str:
    """문단의 run 을 새 글 하나로 갈아 끼운다. pPr 과 첫 run 의 rPr 은 남긴다."""
    m = re.search(r"<w:pPr>.*?</w:pPr>", p, re.S)
    head = m.group(0) if m else ""
    m = re.search(r"<w:r[ >].*?(<w:rPr>.*?</w:rPr>)", p, re.S)
    rpr = m.group(1) if m else ""
    body = f'<w:r>{rpr}<w:t xml:space="preserve">{html.escape(text)}</w:t></w:r>' if text else ""
    return f"{p[: p.index('>') + 1]}{head}{body}{extra}</w:p>"


def drawing(rid: str, w: int, h: int, seq: int) -> str:
    cx, cy = DISP_W * EMU, int(DISP_W * h / w) * EMU
    did = 900 + seq
    return (
        f'<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">'
        f'<wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{did}" name="서명{seq}"/>'
        f'<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
        f'<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">'
        f'<pic:nvPicPr><pic:cNvPr id="{did}" name="서명{seq}"/><pic:cNvPicPr/></pic:nvPicPr>'
        f'<pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
        f'<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm>'
        f'<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>'
        f"</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
    )


for idx, text in spec["lines"].items():
    i = int(idx)
    doc = doc.replace(paras[i], rewrite(paras[i], text), 1)

src = zipfile.ZipFile(SRC)
rels = src.read("word/_rels/document.xml.rels").decode()
ct = src.read("[Content_Types].xml").decode()
signs = spec.get("signs") or ([spec["sign"]] if spec.get("sign") else [])
media = {}
if signs:
    from PIL import Image

    for i, sg in enumerate(signs):
        w, h = Image.open(sg["png"]).size
        rid, name = f"rIdSign{i + 1}", f"sign{i + 1}.png"
        j = int(sg["para"])
        doc = doc.replace(paras[j], rewrite(paras[j], "", drawing(rid, w, h, i + 1)), 1)
        rels = rels.replace(
            "</Relationships>",
            f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/'
            f'2006/relationships/image" Target="media/{name}"/></Relationships>',
        )
        media[name] = sg["png"]
    if 'Extension="png"' not in ct:
        ct = ct.replace("<Default", '<Default Extension="png" ContentType="image/png"/><Default', 1)

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    swap = {"word/document.xml": doc, "word/_rels/document.xml.rels": rels,
            "[Content_Types].xml": ct}
    for item in src.infolist():
        z.writestr(item, swap.get(item.filename) or src.read(item.filename))
    for name, path in media.items():
        z.write(path, f"word/media/{name}")
print(OUT)
