# Readana logo deliverables

The installed Readana mark is an expressive open book with a discovery star: a compact symbol for finding books and entering a reading experience without depicting a device. The selected ImageGen concept is checked in as the colour master; every Android output is generated from it.

## Source artwork

| Purpose | File | Canvas |
| --- | --- | --- |
| Full-colour transparent master | `readana-logo-master.png` | 1254 × 1254 px |
| Monochrome transparent master | `readana-logo-monochrome-master.png` | 1254 × 1254 px |

## Android outputs

| Purpose | Format and sizes |
| --- | --- |
| Adaptive colour foreground | Transparent PNG at 108, 162, 216, 324, and 432 px |
| Adaptive monochrome foreground | Transparent PNG at 108, 162, 216, 324, and 432 px |
| Legacy launcher icon | PNG at 48, 72, 96, 144, and 192 px |
| Legacy round launcher icon | PNG at 48, 72, 96, 144, and 192 px |
| Play Store icon | Opaque PNG at 512 × 512 px |

The adaptive-icon wrappers live in `app/src/main/res/mipmap-anydpi-v26/`. `scripts/generate_launcher_icons.py` derives the monochrome master from the colour master's exact alpha channel, then regenerates every Android PNG output. The adaptive, legacy, and Play Store backgrounds use the app's lavender-white `#FEF7FF` surface colour.

## Design direction

- Simple, minimal, and readable at small sizes.
- Material 3 Expressive character: rounded geometry, confident silhouette, and a small asymmetrical gesture.
- Current Readana palette from `expressiveLightColorScheme()`: violet `#65558F`, deep violet `#4F378B`, lilac `#EADDFF`, plum `#7D5260`, pink `#FFD8E4`, and lavender-white `#FEF7FF`.
- The shape language should echo the app's extra-large rounded cards, circular progress marks, flexible top bars, and expressive motion without copying a UI component literally.
- No wordmark, device mock-up, download arrow, hard shadow, or watermark. The installed master retains the selected concept's subtle violet/plum tonal shading.
- A one-colour silhouette must work for Android themed icons.

## Regeneration

From the repository root:

```shell
python scripts/generate_launcher_icons.py
```

Only replace `readana-logo-master.png`. The script recreates the monochrome master, adaptive foregrounds, legacy square and round icons, and Play Store icon.
