"""Gera ícones Android a partir de icone.jpg na raiz do projeto."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "icone.jpg"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

LAUNCHER_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def square_image(img: Image.Image, size: int, padding_ratio: float = 0.08, background=(255, 255, 255, 255)) -> Image.Image:
    img = img.convert("RGBA")
    w, h = img.size
    side = max(w, h)
    canvas = Image.new("RGBA", (side, side), background)
    canvas.paste(img, ((side - w) // 2, (side - h) // 2), img if img.mode == "RGBA" else None)
    inner = int(size * (1 - padding_ratio * 2))
    canvas = canvas.resize((inner, inner), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (size, size), background)
    offset = (size - inner) // 2
    out.paste(canvas, (offset, offset), canvas)
    return out


COXINHA_DRAWABLE_SIZES = {
    "drawable-mdpi": 120,
    "drawable-hdpi": 180,
    "drawable-xhdpi": 240,
    "drawable-xxhdpi": 360,
    "drawable-xxxhdpi": 480,
}


def main():
    if not SRC.exists():
        raise SystemExit(f"Arquivo não encontrado: {SRC}")

    source = Image.open(SRC)

    for folder, size in LAUNCHER_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = square_image(source, size)
        icon.save(out_dir / "ic_launcher.png", "PNG")
        icon.save(out_dir / "ic_launcher_round.png", "PNG")
        print(f"OK {folder}/ic_launcher.png ({size}px)")

    for folder, size in FOREGROUND_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        fg = square_image(source, size, padding_ratio=0.12)
        fg.save(out_dir / "ic_launcher_foreground.png", "PNG")
        print(f"OK {folder}/ic_launcher_foreground.png ({size}px)")

    for folder, size in COXINHA_DRAWABLE_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        coxinha = square_image(source, size, padding_ratio=0.06, background=(0, 0, 0, 0))
        coxinha.save(out_dir / "coxinha.png", "PNG")
        print(f"OK {folder}/coxinha.png ({size}px)")


if __name__ == "__main__":
    main()
