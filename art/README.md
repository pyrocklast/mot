# Icon

- `icon.svg`: the design, with live Inter text (weight 650).
- `icon-outlined.svg`: the same with the lettering outlined, so it looks the same everywhere.

In the app, the outlined drawing is `app/src/main/res/drawable/ic_launcher_foreground.xml` (and `ic_launcher_monochrome.xml` for themed icons), on the `icon_background` colour, #191919. The design's 66×66 square is fitted to the 72dp an Android icon shows, which keeps everything inside the 66dp circle Android never crops.
