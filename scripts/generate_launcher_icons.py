"""Generate Android launcher icons from the checked-in Readana masters.

Run from the repository root after replacing either artwork master.
"""

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ARTWORK = ROOT / "artwork"
RES = ROOT / "app" / "src" / "main" / "res"
COLOR_MASTER = ARTWORK / "readana-logo-master.png"
MONO_MASTER = ARTWORK / "readana-logo-monochrome-master.png"

DENSITIES = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}


def trimmed(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    alpha = image.getchannel("A")
    meaningful = alpha.point(lambda value: 255 if value >= 8 else 0)
    bounds = meaningful.getbbox()
    if bounds is None:
        raise ValueError(f"{path} has no visible pixels")
    return image.crop(bounds)


def contain(source: Image.Image, canvas_size: int, content_size: int) -> Image.Image:
    scale = min(content_size / source.width, content_size / source.height)
    resized = source.resize(
        (max(1, round(source.width * scale)), max(1, round(source.height * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    canvas.alpha_composite(
        resized,
        ((canvas_size - resized.width) // 2, (canvas_size - resized.height) // 2),
    )
    return canvas


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def opaque_white_icon(source: Image.Image, size: int, content_fraction: float) -> Image.Image:
    transparent = contain(source, size, round(size * content_fraction))
    background = Image.new("RGBA", (size, size), "white")
    background.alpha_composite(transparent)
    return background.convert("RGB")


def legacy_white_icon(source: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    radius = round(size * 0.18)
    ImageDraw.Draw(canvas).rounded_rectangle(
        (0, 0, size - 1, size - 1), radius=radius, fill="white"
    )
    canvas.alpha_composite(contain(source, size, round(size * 0.72)))
    return canvas


def round_white_icon(source: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(canvas).ellipse((0, 0, size - 1, size - 1), fill="white")
    canvas.alpha_composite(contain(source, size, round(size * 0.68)))
    return canvas


def main() -> None:
    color = trimmed(COLOR_MASTER)
    monochrome = trimmed(MONO_MASTER)

    for density, (legacy_size, adaptive_size) in DENSITIES.items():
        # Android's adaptive safe zone is 66/108 dp. A little extra breathing room
        # keeps the original soft shadow intact under all OEM masks.
        safe_content = round(adaptive_size * 64 / 108)
        save(
            contain(color, adaptive_size, safe_content),
            RES / f"drawable-{density}" / "ic_launcher_foreground.png",
        )
        save(
            contain(monochrome, adaptive_size, safe_content),
            RES / f"drawable-{density}" / "ic_launcher_monochrome.png",
        )
        save(
            legacy_white_icon(color, legacy_size),
            ARTWORK / "android" / "legacy" / density / "ic_launcher.png",
        )
        save(
            round_white_icon(color, legacy_size),
            ARTWORK / "android" / "legacy" / density / "ic_launcher_round.png",
        )

    save(opaque_white_icon(color, 512, 0.76), ARTWORK / "readana-play-store-icon.png")


if __name__ == "__main__":
    main()
