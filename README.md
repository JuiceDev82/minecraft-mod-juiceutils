# Base Tracer

A client-side Fabric mod for Minecraft Java 26.2. It finds likely player bases: it highlights chunks that match rules you
choose, such as light sources or combinations of blocks, and draws tracer lines to them. It also includes a set of
quality-of-life tools.

Because the mod is client-only, it works on any server. The server doesn't need it installed.

## Features

- **Bases:** highlights chunks and blocks that match your rules, such as light sources or player-made blocks, with tracer
  lines and labels pointing to them.
- **Mobs and players:** outlines hostile mobs and players, and warns you when a hostile mob is close. Piglins and
  zombified piglins only trigger the warning when they're aggressive.
- **Items:** labels dropped items.
- **X-Ray:** shows only the blocks you choose.
- **Free Look:** moves the camera around without turning your character.
- **Smart Tool:** switches to the best hotbar tool for the block you're mining, after a short delay to ignore misclicks. It
  prefers Silk Touch for glass and ender chests, uses an axe on boats, and switches back to your previous item when you
  let go.
- **Auto Eat:** eats the best food in your hotbar when your hunger drops to a level you choose, or when your health is
  low.
- **Quick Use:** one key throws an ender pearl, drinks a potion or eats an enchanted golden apple, then switches back to
  your previous item.
- **Take All / Put All:** buttons on chests, barrels, shulker boxes, hoppers, dispensers and droppers to move everything
  in or out. Put All can include the hotbar, and it can move only the items on your list or everything except them.

## Default keys

| Key | Action |
| --- | --- |
| `L` | Toggle Base Tracer |
| `J` | Toggle Free Look |
| `X` | Toggle X-Ray |
| Mouse button 4 | Quick Use |

You can change these under Options → Controls. Every feature can be adjusted or turned off in the mod's settings, which
open from [Mod Menu](https://modrinth.com/mod/modmenu).

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download `basetracer-<version>.jar` from the [Releases](../../releases) page and put it in your `mods` folder.
4. Optional: add [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings in game.

The mod requires Java 25.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. To run the in-game tests, use `./gradlew runClientGameTest`.

## License

[CC0 1.0](LICENSE)
