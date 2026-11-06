# Pix logo

The Pix logo is a P whose bowl is a camera shutter: six blades around a yellow hexagon aperture, and one blade runs on to become the stem. [`pix-logo-usage.png`](pix-logo-usage.png) is the one-page usage guide (versions, clear space, minimum sizes, colours, what not to do, which file to use where), and [`board/`](board/) shows the logo in use.

## Files

| Files | Use |
|---|---|
| `pix-horizontal*.svg` | The default logo: the symbol beside the "Pix" wordmark (slides, website headers, and the repository README, which shows `pix-horizontal` or `pix-horizontal-on-dark` with GitHub's theme) |
| `pix-stacked*.svg` | The symbol above the wordmark, for square-ish spaces (posters, title slides) |
| `pix-symbol*.svg` | The symbol alone; `pix-symbol-small*.svg` below 32 px, where the blade gaps close |
| `pix-wordmark*.svg` | The wordmark alone |
| `pix-app-icon*.svg` | The symbol on the app-icon tile: paper (the app's icon) or dark |
| `png/` | PNG exports of the above, transparent; `pix-app-icon-512.png` is the Play Store size |

Every version comes in colour (no suffix), `-on-dark` (paper blades and wordmark, sun aperture, for dark backgrounds), `-black`, and `-white`. In the one-colour versions the aperture is left open.

## Colours

| Name | HEX | Use |
|---|---|---|
| Ink | `#16171A` | Blades and wordmark |
| Sun | `#F2C14E` | The aperture and the i's dot; the app's accent colour |
| Paper | `#F4F4F2` | The app-icon tile and light backgrounds |

## Wordmark

"Pix" is set in Poppins ExtraBold (SIL Open Font License 1.1, free for logos) and outlined, so no file needs the font. The i's dot is a sun hexagon, optically as wide as the i's stem. Text set beside the logo, such as slide titles, can use Poppins from [Google Fonts](https://fonts.google.com/specimen/Poppins).

## Sources and the app

The SVG files are the masters; the PNGs are rendered from them, for example `rsvg-convert -h 512 pix-horizontal.svg -o png/pix-horizontal.png`. The app's icons are vector drawables with the same geometry: `ic_launcher_foreground` (the colour symbol, on the `ic_launcher_background` paper tile), `ic_launcher_monochrome` (the blades, for themed icons), and `ic_notification` (the small symbol, for the status bar), in `android/app/src/main/res/drawable/`. A change to the symbol changes those drawables too.
