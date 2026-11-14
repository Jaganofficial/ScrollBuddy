"""Cuts the character's clips out of the generated video and packs them into the animated WebP
files the app plays.

    /Applications/Blender.app/Contents/MacOS/Blender -b -P art/make_clips.py -- art/clips/gestures.mp4

Blender is here only for its Python, which comes with numpy and can write WebP stills; ffmpeg
decodes the video. The video is one take of the character on a flat green backdrop: he waves,
holds up a stop sign in a closer shot, then sweeps his hand out to the side. The frame numbers
and the column he stands on are read off that one take, so another take needs its own.

Each clip becomes app/src/main/res/raw/vaathi_<clip>.webp and the launcher icon's head is cut
from one frame. The still of him in sunglasses, shades.jpg beside the video, is cut out the
same way. An animated WebP is a RIFF file whose frames are the still images' own
compressed chunks, so packing only moves chunks from one container to another.
"""
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"

# The column of the video the character stands on, in both shots. Every clip is cut the same
# distance either side of it, so clips of different widths line up when the app centres them
CENTRE_X = 636
QUALITY = 86

# How much greener than red and blue a pixel is, as a share of the backdrop's own figure: below
# OPAQUE_BELOW it is the character, above CLEAR_ABOVE it is backdrop, and between is his edge
OPAQUE_BELOW = 0.25
CLEAR_ABOVE = 0.8
# How many pixels in from the outline take their colour from further inside
EDGE_PASSES = 8


def there_and_back(first, last):
    """A held pose played forward then backward, so it loops with no jump."""
    forward = list(range(first, last + 1))
    return forward + forward[-2:0:-1]


# name: (frames of the video counted from 1, milliseconds each is shown, width cut out).
# Frames 1 to 80 and 161 on are the full shot; 81 to 160 are the closer one
CLIPS = {
    "wave": (list(range(1, 81)), 38, 448),
    "stop": (list(range(81, 108)), 42, 496),
    "hold": (there_and_back(108, 133), 42, 496),
    # Stops with the arm straight out; the take goes on to lower it
    "sweep": (list(range(164, 206)), 36, 704),
}

# The frame the launcher icon's head comes from, and the square cut from it: left, top, side
ICON_FRAME = 158
ICON_BOX = (486, 26, 300)

# The still of the character in sunglasses, kept beside the video, and the part of it he stands
# in: left, top, width, height
PORTRAIT_FILE = "shades.jpg"
PORTRAIT_BOX = (190, 150, 400, 1150)

ANIMATION_FLAG = 0x02
ALPHA_FLAG = 0x10
# Replace the canvas with each frame instead of drawing it over the previous one, which would
# leave the old pose showing through the transparent parts
NO_BLEND = 0x02


def decode(video, folder):
    """The frames of a video, or the one frame of a picture, as (frame, row, column, RGB)."""
    size = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
         "-of", "csv=p=0", str(video)],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    width, height = (int(value) for value in size.split(","))
    raw = folder / f"{video.stem}.rgb"
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", str(video), "-f", "rawvideo", "-pix_fmt", "rgb24", str(raw)],
        check=True,
    )
    return np.memmap(raw, dtype=np.uint8, mode="r").reshape(-1, height, width, 3)


def shrink(values):
    """Each pixel's lowest value among itself and its eight neighbours."""
    padded = np.pad(values, 1, mode="edge")
    rows = np.minimum(np.minimum(padded[:-2], padded[1:-1]), padded[2:])
    return np.minimum(np.minimum(rows[:, :-2], rows[:, 1:-1]), rows[:, 2:])


def spread(values, reach):
    """Each pixel's sum over the square that reaches [reach] pixels from it each way."""
    padded = np.pad(values, [(reach, reach), (reach, reach)] + [(0, 0)] * (values.ndim - 2))
    height, width = values.shape[:2]
    rows = sum(padded[offset:offset + height] for offset in range(2 * reach + 1))
    return sum(rows[:, offset:offset + width] for offset in range(2 * reach + 1))


def cut_out(frame, backdrop):
    """[frame] with the green [backdrop] made transparent, as RGBA bytes."""
    rgb = frame.astype(np.float32)
    # Measured against the mean of red and blue, not the larger of them: backdrop showing
    # between his fingers mixes with red skin, and the larger of the two would hide it
    other = (rgb[..., 0] + rgb[..., 2]) / 2
    green = (rgb[..., 1] - other) / (backdrop[1] - (backdrop[0] + backdrop[2]) / 2)
    rough = np.clip((CLEAR_ABOVE - green) / (CLEAR_ABOVE - OPAQUE_BELOW), 0, 1)
    # Nothing on the character is green, so any green left over is the backdrop's light on him
    colour = rgb.copy()
    colour[..., 1] = np.minimum(colour[..., 1], other)

    # The video smears the backdrop a pixel or two into his outline, and on skin that reads as
    # a pale rim rather than as green. So the outline moves in by a pixel, and the rim takes
    # its colour from the clean pixels just inside it
    alpha = shrink(rough)
    clean = shrink(shrink((rough == 1).astype(np.float32)))
    for _ in range(EDGE_PASSES):
        near = spread(clean, 1)
        rim = (clean == 0) & (near > 0)
        colour[rim] = (spread(colour * clean[..., None], 1)[rim] / near[rim][:, None])
        clean[rim] = 1
    colour[alpha == 0] = 0
    return np.dstack([colour, alpha * 255]).round().astype(np.uint8)


def write_webp(rgba, path):
    height, width = rgba.shape[:2]
    image = bpy.data.images.new("frame", width, height, alpha=True)
    # Blender's rows run bottom to top
    image.pixels.foreach_set((rgba[::-1].astype(np.float32) / 255).ravel())
    image.file_format = "WEBP"
    image.filepath_raw = str(path)
    image.save(quality=QUALITY)
    bpy.data.images.remove(image)


def chunk(tag, payload):
    padding = b"\x00" if len(payload) % 2 else b""
    return tag + struct.pack("<I", len(payload)) + payload + padding


def uint24(value):
    return struct.pack("<I", value)[:3]


def read_still(path):
    """Returns (width, height, image chunks) of a still WebP: its ALPH and VP8 or VP8L chunks."""
    data = path.read_bytes()
    if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
        raise ValueError(f"{path} is not a WebP file")
    width = height = None
    image = b""
    offset = 12
    while offset < len(data):
        tag = data[offset:offset + 4]
        size = struct.unpack("<I", data[offset + 4:offset + 8])[0]
        payload = data[offset + 8:offset + 8 + size]
        if tag == b"VP8X":
            width = 1 + int.from_bytes(payload[4:7], "little")
            height = 1 + int.from_bytes(payload[7:10], "little")
        elif tag in (b"ALPH", b"VP8 ", b"VP8L"):
            image += chunk(tag, payload)
            if tag == b"VP8 " and width is None:
                width = struct.unpack("<H", payload[6:8])[0] & 0x3FFF
                height = struct.unpack("<H", payload[8:10])[0] & 0x3FFF
            elif tag == b"VP8L" and width is None:
                bits = struct.unpack("<I", payload[1:5])[0]
                width = 1 + (bits & 0x3FFF)
                height = 1 + ((bits >> 14) & 0x3FFF)
        offset += 8 + size + size % 2
    return width, height, image


def pack(stills, frame_ms, target):
    """Writes the still WebP files [stills], in playing order, as one animated WebP."""
    read = {path: read_still(path) for path in set(stills)}
    width, height = read[stills[0]][:2]
    body = chunk(b"VP8X", bytes([ANIMATION_FLAG | ALPHA_FLAG, 0, 0, 0]) + uint24(width - 1) + uint24(height - 1))
    # Transparent background; loop count 0 is forever, and the app sets its own repeat count
    body += chunk(b"ANIM", struct.pack("<IH", 0, 0))
    for path in stills:
        frame_width, frame_height, image = read[path]
        if (frame_width, frame_height) != (width, height):
            raise ValueError("every frame of a clip must be the same size")
        header = uint24(0) + uint24(0) + uint24(width - 1) + uint24(height - 1) + uint24(frame_ms) + bytes([NO_BLEND])
        body += chunk(b"ANMF", header + image)
    target.write_bytes(b"RIFF" + struct.pack("<I", 4 + len(body)) + b"WEBP" + body)
    print(f"{target.relative_to(ROOT)}: {len(stills)} frames, {width}x{height}, {target.stat().st_size // 1024} KB")


def main(video):
    with tempfile.TemporaryDirectory() as temporary:
        folder = Path(temporary)
        frames = decode(video, folder)
        # The top left corner is backdrop in every frame
        backdrop = np.median(frames[0][20:200, 20:250].reshape(-1, 3), axis=0).astype(np.float32)
        (RES / "raw").mkdir(exist_ok=True)
        for name, (numbers, frame_ms, width) in CLIPS.items():
            left = CENTRE_X - width // 2
            stills = []
            for number in numbers:
                still = folder / f"{name}_{number:04d}.webp"
                if not still.exists():
                    write_webp(cut_out(frames[number - 1][:, left:left + width], backdrop), still)
                stills.append(still)
            pack(stills, frame_ms, RES / "raw" / f"vaathi_{name}.webp")
        left, top, side = ICON_BOX
        icon = RES / "drawable-nodpi" / "ic_launcher_head.webp"
        icon.parent.mkdir(exist_ok=True)
        write_webp(cut_out(frames[ICON_FRAME - 1][top:top + side, left:left + side], backdrop), icon)
        print(f"{icon.relative_to(ROOT)}: {icon.stat().st_size // 1024} KB")

        picture = decode(video.with_name(PORTRAIT_FILE), folder)[0]
        backdrop = np.median(picture[20:200, 20:150].reshape(-1, 3), axis=0).astype(np.float32)
        left, top, width, height = PORTRAIT_BOX
        portrait = RES / "drawable-nodpi" / "vaathi_shades.webp"
        write_webp(cut_out(picture[top:top + height, left:left + width], backdrop), portrait)
        print(f"{portrait.relative_to(ROOT)}: {width}x{height}, {portrait.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main(Path(sys.argv[sys.argv.index("--") + 1]).resolve())
