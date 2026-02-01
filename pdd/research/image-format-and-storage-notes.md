Version: v1 (2026-01-31)

# Image format & storage notes for line-scan “infinite width” images

## Context
Line-scan finish/start imagery grows over time, so storing as a single bitmap can exceed format decoder limits (max width/height), even if the spec allows more.

## Key findings (limits)
- WebP lossless encodes width/height as 14-bit integers => max 16384×16384.
- JPEG/JFIF supports max 65535×65535.
- PNG spec does not impose a small practical limit; it uses 32-bit width/height fields (implementation and memory become the real limit).
- TIFF classic has a 4GB limit; BigTIFF extends via 64-bit offsets (file size constraint lifted, but viewer support varies).

## Design implication for RegattaDesk
We should not rely on any single “huge image file” for UI zoom/pan.
Instead:
- store imagery as fixed-size tiles (e.g., 512×512 or 1024×1024) using WebP/PNG,
- serve a manifest describing the tile grid and mapping x-coordinate ↔ timestamp,
- the UI stitches tiles for overview and detail windows.
This meets “no width/height restriction” by construction and enables post-regatta pruning:
- keep all tiles during regatta,
- after delay, retain only tiles intersecting ±2s around approved markers.

## Defaults (best practice)
- Tile size: 512x512.
- Format: WebP lossless; PNG fallback where WebP is not supported.
- Manifest: include tile size, origin, and x-coordinate -> timestamp mapping.
- Pruning: default delay 14 days after regatta end (configurable per regatta).

## Sources (titles)
- “Specification for WebP Lossless Bitstream” (Google Developers)
- “JPEG” (Wikipedia, JPEG/JFIF max image size section)
- “File format limits in pixel size for png images?” (StackOverflow + PNG spec reference)
- “Error: TIF Images Are Limited to 4 GB, but BigTIFF ...” (Esri Support)
