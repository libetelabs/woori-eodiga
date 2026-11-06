"""시연 영상 만들기: 에뮬레이터 녹화(webm)와 캡처(png)를 잘라 붙이고 자막을 얹어 mp4 하나로.

사용: python3 tools/make_demo_video.py <녹화 폴더> <출력.mp4>
녹화 폴더에는 segA.webm(첫 화면·유튜브·탈것), segB.webm(데모 여행), 캡처는 tools/out 의 n3/n4-app/n5-night.png.
자막은 Pillow 로 그린 투명 PNG 를 ffmpeg overlay 로 얹는다(이 맥의 ffmpeg 엔 drawtext 가 없다).
"""
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REC, OUT = sys.argv[1], sys.argv[2]
SHOTS = os.path.join(REPO, "tools", "out")
RES = os.path.join(REPO, "app", "src", "main", "res", "drawable-nodpi")
W, H, FPS = 1920, 1080, 30
FONT = os.path.expanduser("~/Library/Fonts/Pretendard-SemiBold.ttf")
FONT_B = os.path.expanduser("~/Library/Fonts/Pretendard-Bold.ttf")
INK, PAPER = (31, 36, 51), (255, 247, 236)
tmp = tempfile.mkdtemp(prefix="woori-video-")


def font(size, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, size)


def outlined(d, xy, text, f):
    """배경 없이 흰 글자에 검정 테두리."""
    d.text(xy, text, font=f, fill="white", stroke_width=2, stroke_fill="black")


def caption(name, text, sub=None, voice=False):
    """화면 아래 가운데 자막: 본문 32px, 보조 28px. voice=True 면 앞에 '(음성)'."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    f, fs = font(32, True), font(28)
    if voice:
        text = f"(음성) {text}"
    y = H - 150 - (40 if sub else 0)
    outlined(d, ((W - d.textlength(text, font=f)) / 2, y), text, f)
    if sub:
        outlined(d, ((W - d.textlength(sub, font=fs)) / 2, y + 44), sub, fs)
    path = os.path.join(tmp, name + ".png")
    im.save(path)
    return path


def badge(name, text):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # 오른쪽 위는 Pleos 상태 표시줄(시계)이라 왼쪽 주행 정보 칸 위에 둔다
    outlined(d, (40, 110), text, font(32, True))
    path = os.path.join(tmp, name + ".png")
    im.save(path)
    return path


def card(name, title, lines, art):
    im = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(im)
    pic = Image.open(os.path.join(RES, art)).convert("RGBA")
    pic.thumbnail((520, 520))
    im.paste(pic, (180, (H - pic.height) // 2), pic)
    x = 800
    d.text((x, 300), title, font=font(96, True), fill=INK)
    y = 450
    for i, line in enumerate(lines):
        d.text((x, y), line, font=font(40 if i == 0 else 34), fill=INK if i == 0 else (91, 98, 117))
        y += 70 if i == 0 else 56
    path = os.path.join(tmp, name + ".png")
    im.save(path)
    return path


def run(args):
    subprocess.run(["ffmpeg", "-v", "error", "-y"] + args, check=True)


def clip(out, src_args, base_filter, overlays, duration):
    """src 하나 + (png, 시작, 끝) 자막들 → 1920x1080 30fps 무음 mp4."""
    args = src_args[:]
    for png, _, _ in overlays:
        args += ["-i", png]
    chain = f"[0:v]{base_filter},scale={W}:{H}:force_original_aspect_ratio=decrease,pad={W}:{H}:(ow-iw)/2:(oh-ih)/2,fps={FPS},format=yuv420p[v0]"
    last = "v0"
    for i, (_, a, b) in enumerate(overlays, start=1):
        chain += f";[{last}][{i}:v]overlay=0:0:enable='between(t,{a},{b})'[v{i}]"
        last = f"v{i}"
    args += ["-filter_complex", chain, "-map", f"[{last}]", "-t", str(duration),
             "-c:v", "libx264", "-crf", "20", "-preset", "medium", "-pix_fmt", "yuv420p", "-an", out]
    run(args)
    return out


def still(out, png, duration, overlays):
    return clip(out, ["-loop", "1", "-i", png], "null", overlays, duration)


parts = []

# 0. 제목
parts.append(still(os.path.join(tmp, "p0.mp4"), card(
    "title", "우리 어디가?",
    ["뒷자리 아이를 위한 여행 진행판", "현대차 Pleos Connect 앱 · 에뮬레이터 실제 화면"],
    "ride_unicorn.webp"), 4, []))

# 1. 정차 중 첫 화면 → 유튜브 보기 → 탈것 고르기 (segA 0.5~25s)
parts.append(clip(os.path.join(tmp, "p1.mp4"), ["-ss", "0.5", "-t", "24.5", "-i", os.path.join(REC, "segA.webm")], "null", [
    (caption("a1", "정차 중 첫 화면", "아이가 고른 탈것이 여행을 함께 가요"), 0, 3.6),
    (caption("a2", "유튜브 보기 · 공식 만화·애니·동요 60개", "고르면 차의 유튜브 앱이 열리고, 앱 막대로 돌아와요"), 3.6, 14.8),
    (caption("a3", "탈것 고르기 · 유니콘과 꼬마 기차 8가지"), 14.8, 19.6),
    (caption("a4", "빨간 꼬마 기차를 골랐어!", voice=True), 19.6, 24.5),
], 24.5))

# 2. 순정 내비 → 앱 (캡처)
parts.append(still(os.path.join(tmp, "p2a.mp4"), os.path.join(SHOTS, "n3.png"), 3.5, [
    (caption("n1", "순정 내비에서 목적지를 정하면", "예: 롯데월드"), 0, 3.5)]))
parts.append(still(os.path.join(tmp, "p2b.mp4"), os.path.join(SHOTS, "n4-app.png"), 5, [
    (caption("n2", "앱이 내비를 읽어 남은 시간을 '만화 몇 편'으로", "목적지 종류도 자동으로 · 롯데월드 → 놀이공원"), 0, 5)]))
parts.append(still(os.path.join(tmp, "p2c.mp4"), os.path.join(SHOTS, "n5-night.png"), 3, [
    (caption("n3", "밤에는 밤 화면으로"), 0, 3)]))

# 3. 데모 여행: 3.5~96s 를 2배속, 96~106.5s 는 그대로 (segB)
x2 = badge("x2", "2배속")
parts.append(clip(os.path.join(tmp, "p3a.mp4"), ["-ss", "3.5", "-t", "92.5", "-i", os.path.join(REC, "segB.webm")], "setpts=PTS/2", [
    (x2, 0, 46.2),
    (caption("b1", "여행 중 · 주행 중엔 '얼마나 남았어?' 버튼 하나만", "시험용 데모: 12km·25분 길을 90초로"), 0, 8.2),
    (caption("b2", "할머니 집까지 만화 두 편 남았어!", voice=True), 8.3, 12.5),
    (caption("b3", "동네 → 도시 → 들판·강 → 산길 → 도착", "구간이 바뀔 때만 그림이 바뀌어요(주행 중 움직임 최소)"), 12.6, 21.8),
    (caption("b4", "길이 바뀌면 '길이 바뀌었어! 다시 가 보자'"), 21.9, 27.5),
    (caption("b5", "남은 시간이 짧아지면 '동요 몇 곡'으로"), 27.6, 33.5),
    (caption("b6", "할머니 집까지 동요 세 곡 남았어!", voice=True), 33.6, 38.5),
], 46.2))
parts.append(clip(os.path.join(tmp, "p3b.mp4"), ["-ss", "96", "-t", "10.5", "-i", os.path.join(REC, "segB.webm")], "null", [
    (caption("c1", "도착! 차가 멈추면(P) 축하와 완주 도장"), 0, 4.5),
    (caption("c2", "할머니 집에 도착했어! 여행 완주!", voice=True), 4.5, 10.5),
], 10.5))

# 4. 마무리
parts.append(still(os.path.join(tmp, "p4.mp4"), card(
    "end", "우리 어디가?",
    ["\"언제 도착해?\"에 아이 말로 답하는 차",
     "순정 내비 목적지·남은 시간 읽기 — Pleos NaviHelper",
     "주행 중엔 버튼 하나, 정차(P)일 때만 조작 — AAOS 기어(GEAR_SELECTION)",
     "기기 안 한국어 음성 — Pleos TextToSpeech",
     "정차 중 유튜브 공식 플레이리스트 — 차의 유튜브 앱 연결"],
    "ride_train.webp"), 6, []))

lst = os.path.join(tmp, "list.txt")
with open(lst, "w") as fp:
    fp.writelines(f"file '{p}'\n" for p in parts)
run(["-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", OUT])
print(OUT)
