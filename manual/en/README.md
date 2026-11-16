# MobileCAD User Manual (English)

The English edition of the MobileCAD handbook: 17 chapters and 3 appendices, about 90 pages, written against the
application's source. It is the same content as the Chinese manual next to it (`../`), and it doubles as an i18n
walkthrough: the screenshots show the Chinese build, and the opening note explains the language mechanism
(`assets/i18n/zh-Hans.tsv`, the English key, FreeCAD's own Chinese vocabulary, how to add a language).

## Files

| Path | What |
|---|---|
| `main.tex` | the book (LaTeX `book`, XeLaTeX) |
| `chapters/` | preface, the i18n note, and 17 chapters |
| `appendix/` | A command reference (English / 中文 / command ID), B bilingual glossary, C FAQ |
| `MobileCAD-manual-en.pdf` | the compiled PDF |
| `../figures/` | shared screenshots (device shots of the Chinese build + the project's own) |

## Building

```sh
cd manual/en
xelatex main.tex
xelatex main.tex     # second pass for the table of contents
```

Needs XeLaTeX with `booktabs`, `tabularx`, `longtable`, `tcolorbox`, `listings`, `fontawesome5`,
`newunicodechar`, `seqsplit`, `microtype` (a full TeX Live / MacTeX install has them). The symbol fallbacks use
macOS' "Apple Symbols" font for a few UI glyphs; on other systems point `\newfontfamily\symfont{...}` at any font
that covers ▸ ▾ ☰ etc., or replace those glyphs with plain text.

## License

GNU General Public License v3.0 or later, like the application.
