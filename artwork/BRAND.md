# Readana — Logo Guidelines

## 1. The logo
- **Idea**: a lowercase r whose stem runs below the baseline and ends in a notched ribbon. The r is a bookmark that keeps your place.
- **Versions**: horizontal (primary) · stacked · symbol · wordmark. Each in light, dark, violet, black and white (the symbol has no separate violet file because violet is already its default colour).
- **Construction** (raw grid, scaled ×1.111 into the 256 symbol canvas): stem 52 wide (x 70–122) from the x-height to the ribbon tips at y 236; notch apex 26 above the tips, so the cut runs at 45°. The arm is a quarter ring centred on (190,150), R94 / r46, with a vertical terminal at x 190. Convex corners are rounded r4. Dark and white versions are about 1 unit thinner to offset irradiation. The `-small` cut has a heavier arm (r40), a deeper notch (30) and r3 corners, for use at 24 px and below.
- **Wordmark**: Outfit SemiBold (600), outlined, tracking −10/1000 em. Outfit is licensed under the SIL OFL, which allows logo use; the wordmark is supplied as outlines, so the font itself is not needed.
- **Horizontal lockup**: the top of the wordmark's ascender lines up with the top of the stem, and the wordmark's baseline lines up with the notch apex. The ribbon hangs below the text. Gap = 0.62 × stem width.

## 2. Colour: shared mtgfmax / ordo / pokeum / piikey tokens
| Role | Light mode | Dark mode | Token |
|---|---|---|---|
| Symbol / accent | `#6F49B1` · oklch(0.5 0.16 297.347) | `#D6C9F9` · oklch(0.862 0.067 297.347) | `--ring` |
| Wordmark | `#0A0A0A` · oklch(0.145 0 0) | `#FAFAFA` · oklch(0.985 0 0) | `--foreground` |
| Background | `#FFFFFF` | `#0A0A0A` | `--background` |
| Tile / card (dark) | — | `#171717` · oklch(0.205 0 0) | `--card` |

| Colour | RGB | CMYK (approx.) |
|---|---|---|
| Violet `#6F49B1` | 111 73 177 | 37 59 0 31 |
| Lavender `#D6C9F9` | 214 201 249 | 14 19 0 2 |
| Ink `#0A0A0A` | 10 10 10 | 0 0 0 96 |

The CMYK values are naive conversions, so proof them before printing.

Contrast ratios: ink on white 19.8:1 · paper on black 19.0:1 · white on violet 6.4:1 · lavender on `#0A0A0A` 12.8:1.

**Approved pairs**:
- `readana-*.svg` on white
- `readana-*-dark.svg` on `#0A0A0A` or `#171717`
- white symbol on a violet tile (app icon)
- lavender symbol on a `#171717` tile (dark app icon)
- black or white one-colour

**Never** use the violet symbol on dark backgrounds. Switch to the `-dark` file instead.

## 3. Clear space
Keep **1 stem width** clear on all sides. The stem is 29 % of the symbol height, and the clear space scales with the logo.

## 4. Minimum size
| Version | Screen | Print |
|---|---|---|
| Horizontal | 110 px wide | 28 mm wide |
| Stacked | 64 px wide | 18 mm wide |
| Symbol | 24 px (at 24 px and below use the `-small` files or `web/favicon.*`) | 6 mm |

## 5. In the app
| Surface | Source | Where |
|---|---|---|
| Launcher icon (adaptive) | `svg/readana-symbol-small-white.svg`, placed as in `app-icon/readana-symbol-app-icon.svg` | `app/src/main/res/drawable/ic_launcher_foreground.xml` (vector, inside the 66 dp safe zone) |
| Launcher background | violet `#6F49B1` | `ic_launcher_background` in `app/src/main/res/values/colors.xml` |
| Themed (monochrome) icon | the same foreground | `mipmap-anydpi-v26/ic_launcher*.xml` |
| Splash screen and window | the launcher icon on `#FFFFFF` / `#0A0A0A` (the theme background) | `values*/themes.xml`, `values-night/colors.xml` |
| In-app logo (Settings › About) | `svg/readana-horizontal.svg`, `svg/readana-horizontal-dark.svg` | `core/designsystem/src/main/res/drawable/core_designsystem_logo*.xml`, `ReadanaLogo()` |
| Play Store icon | 512 px full-bleed render | `readana-play-store-icon.png` |
| README | `<picture>` switching to `svg/readana-horizontal-dark.svg` on `prefers-color-scheme: dark` | `README.md` |

Every in-app copy is a vector drawable converted path-for-path from the SVG masters, so it is sharp at
every density and no PNG ladder is needed (minSdk 29 always uses the adaptive icon). After changing a
master, regenerate the drawable from it rather than editing the path data by hand.

## 6. Don'ts
- Don't stretch, rotate or mirror the logo.
- Don't use colours outside the table above.
- Don't add shadows, outlines or gradients.
- Don't close the ribbon notch or square it off.
- Don't retype the wordmark in a font.
- Don't put the r symbol in front of the wordmark in any arrangement other than the supplied lockups.

## 7. Files
| Folder | Contents |
|---|---|
| `svg/` | Masters: symbol and small cut × light/dark/black/white; horizontal, stacked and wordmark × light/dark/violet/black/white |
| `png/` | Transparent 1024 px renders of the masters |
| `app-icon/` | App-icon tile masters: white symbol on violet, lavender symbol on `#171717` (dark), SVG and 1024 px |
| `readana-play-store-icon.png` | 512 px full-bleed store icon |

No trademark clearance has been done. Run a professional search before launch.
