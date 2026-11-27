# Juice Client

A client-side Fabric mod for Minecraft Java 26.2, with Block and Entity ESP and a set of quality-of-life tools. The ESP
highlights the blocks and entities that match rules you choose, such as light sources or combinations of blocks, and
the chunks they're in, with tracer lines to them. That makes player bases easy to find.

Juice Client was called Base Tracer before 1.0.4. Your settings, exports and keys carry over: remove the old
`basetracer` jar from your `mods` folder when you add this one.

Because the mod is client-only, it works on any server. The server doesn't need it installed.

## Features

- **Block and Entity ESP:** highlights chunks and blocks that match your rules, such as light sources or player-made blocks, with tracer
  lines and labels pointing to them. Each block is highlighted along its own shape, so a torch shows just the torch,
  and entities that match a rule are outlined along their shape. Rules are listed highest priority first, and the
  Highest Priority tracer target draws one tracer to the closest find of your highest priority rule. Close-up
  highlights fade so they don't cover what you're looking at, but stay when the block is behind a wall.
- **Mobs and players:** outlines hostile mobs and other players along their shape. Warns you when a hostile mob is
  close; piglins and zombified piglins only count when they're aggressive. A message and a ding tell you when a player
  comes into or goes out of range.
- **Items:** labels dropped items and outlines them along their shape, with the label in the outline color.
- **Name Tags:** shows the name above the entities and blocks you choose, such as "Acacia Boat" above a boat.
- **X-Ray:** shows only the blocks you choose.
- **Free Look:** moves the camera around without turning your character.
- **Trajectories:** shows the path of what you're about to throw or shoot, with an X where it lands: ender pearls,
- **Auto Clutch** breaks a long fall, over 10 blocks by default: with a water bucket, cobweb or hay bale in your hand or anywhere in your hotbar, chosen in that order, it's placed on the block you're about to land on, then you're back on the slot you had. A shorter fall is broken too when it could kill you. A hay bale only softens the fall.
  snowballs, eggs, splash and lingering potions, bottles o' enchanting, wind charges, a bow being drawn, a loaded crossbow
  and a trident being readied. A mob or player it would hit is outlined.
- **Stack Boss Bars:** boss bars with the same name show as one, such as "Wither x3", instead of filling the screen.
- **Armor Status:** shows the armor you wear in slots beside the offhand slot, each with roughly how much durability
  it has left, in place of the armor bar above the hearts.
- **Smart Tool:** switches to the best hotbar tool for the block you're mining, after a short delay to ignore misclicks. It
  prefers Silk Touch for glass and ender chests, uses shears (or else a sword) on cobwebs and an axe on boats, and
  switches back to your previous item when you let go.
- **Auto Eat:** eats the best food in your hotbar when your hunger drops to a level you choose, or when your health is
  low. When your hotbar runs out of food, it moves the best food from the rest of your inventory into it.
- **Auto Totem:** keeps a totem of undying in your offhand, waiting a time you choose after one pops. A shield in your
  offhand stays until you take it out, unless your health drops below a level you choose. Then the shield goes back
  once your health and hunger are full again, at least 15 seconds have passed and you're standing still. If the server
  undoes either swap, it's done again.
  A fall that could kill you, or one into lava from any height, also puts a totem in straight away, long before you land.
- **Auto Armor:** wears the best armor in your inventory, counting protection enchantments, one piece every half second.
  While an elytra is on, no chestplate goes on. Anything with Curse of Binding is never put on.
- **Auto Drop:** drops the items you choose, such as junk picked up while mining, once you've stood still for half a
  second with them in your inventory. One stack goes every half second. Choose them under Auto Drop Items.
- **Anti Drop** stops the items you choose from being thrown out by mistake, with the drop key or by clicking outside your inventory. They can still go in chests and other containers, and blocks can still be placed. Choose them under Anti Drop Items.
- **Auto Weapon:** when you hit a mob or player, switches to the best sword in your hotbar, or the best axe if you have
  no sword. Sharpness counts, and Smite or Bane of Arthropods against the mobs they work on. Switches back after a time
  you choose without hitting anything.
- **Quick Use:** one key throws an ender pearl, drinks a potion or eats an enchanted golden apple, then switches back to
  your previous item.
- **Take All / Put All:** buttons on chests, barrels, shulker boxes, hoppers, dispensers and droppers to move everything
  in or out. Put All can include the hotbar, and it can move only the items on your list or everything except them.
- **Sort:** buttons on storage screens and in your inventory that sort by type, by most items or by value, joining
  partial stacks and leaving your hotbar alone. Stacks are picked up and put down one click at a time, like by hand.
  The small button beside Sort changes the order. By type, things are grouped as armor, weapons, tools, wood, stone,
  food, farming, ores and minerals, nature, colored blocks, redstone, utility, potions and mob drops. Their order can
  be changed under Sort Group Order. Under Sort Priorities, you can choose items that always go to the
  front or the back.

## Default keys

| Key | Action |
| --- | --- |
| `L` | Toggle Juice Client |
| `J` | Toggle Free Look |
| `X` | Toggle X-Ray |
| Mouse button 4 | Quick Use |

Each settings section also has its own on/off key, unbound by default, shown next to its title in the settings.
Searching "Juice Client" under Options → Controls lists all of the mod's keys.

You can change these under Options → Controls. Every feature can be adjusted or turned off in the mod's settings, which
open from [Mod Menu](https://modrinth.com/mod/modmenu).

## Saving your settings

**Export Settings**, at the top of the mod's settings, saves everything to a file in `config/juiceclient-exports`. That
includes every option, your base rules and lists, and the mod's keys. **Import Settings...** lists those files and loads
the one you click. Your settings from just before the import are saved there too, as `before-import_...`, so an
import can be undone. Copy the files to another computer to take your setup with you.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer for Minecraft 26.2.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download `juiceclient-<version>.jar` from the [Releases](../../releases) page and put it in your `mods` folder.
4. Optional: add [Mod Menu](https://modrinth.com/mod/modmenu) to open the settings in game.

The mod requires Java 25.

## Building

```sh
./gradlew build
```

The jar is written to `build/libs/`. To run the in-game tests, use `./gradlew runClientGameTest`.

## License

[CC0 1.0](LICENSE)
