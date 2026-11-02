# Salvage Helper

A RuneLite plugin for Sailing salvaging, for both AFK crew salvaging and active hooking.

## Features

**Cargo hold**
- **Left-click deposit.** While your inventory holds something the cargo hold accepts, the hold's deposit option (Deposit-all, Quick-deposit or Deposit) becomes the left-click. That covers salvage, fish and fish crates, ship cannonballs, repair kits, drinks and fishing gear; your Sailing kit (cape, log, spyglass and so on) is left out. With nothing depositable, the menu is left alone. **Swap when carrying** picks which items trigger it:
  - *Anything the hold accepts* (default).
  - *Salvage only*.
  - *Only my list*: only the items in **My list**, written as item names or IDs separated by commas. `*` matches anything, so `*salvage` covers every salvage type. For example: `*salvage, Rune cannonball, Raw shark`.
- **Slot count.** Shown as an infobox, and written on the hold itself (`39/160`, fading from green through yellow to red). Capacity comes from the hold's tier and hull size.
- **Your own alert limit.** Set it in config, or right-click the infobox and choose **Set alert limit**. You get a notification and an orange banner when the hold reaches it.
- **Full-hold alert.** A notification and a red banner. It can repeat every few minutes while the hold stays full.
- **Time until full.** Based on how fast your hold has been filling recently. It appears as a line in the counter, and optionally as an infobox with the clock time.
- **Salvage boxes in the hold.** While the hold is open, the salvage inside it is boxed.

**Alerts and reminders.** Each one has its own notification setting and a line on the banner.
- **Hook stopped.** Shows an infobox with your own message.
- **Hook empty, crewmate free.** Repeats while it lasts.
- **Your hook is idle.** You're at a hook with nothing to salvage while a wreck is up. Crew restart on their own; you have to click again.
- **Crew stopped at a wreck.** Crew are on hooks and a wreck is in reach, but nothing has come in for a while.
- **Boost wearing off.** A warning just before your Sailing boost drops below what the wreck needs, and again after it has.
- **Idle logout.** A warning before the game logs you out for idling.
- **Danger.** The boat takes damage, you're hit, or you're poisoned while at wrecks.

**Wrecks**
- **Outlines and timers.** Wrecks that are up are outlined. Each wreck shows "Sinks in ≤ 2:41", and sunk ones show "Sunk - next ≤ 1:10".
- **Reach boxes.** Each wreck gets a box showing where a hook can reach it (8 tiles each way).
- **Double spot boxes.** These mark where one hook reaches two wrecks at once. They're labelled "Park here (1/2)", then turn cyan and say **Parked** once every hook is inside. The reach boxes can hide while you're parked.

**On deck**
- **Next-step outline.** Outlines the salvaging station while you carry salvage, the hold (to take salvage out, or to deposit when your inventory is full), or a free hook when there's nothing else to do.
- **Hide idle crewmates.** Hides crew who aren't working a hook.
- **Hide crew text.** Hides overhead crew chatter and orders, on your boat or on every boat.

**Salvage counter.** An overlay with salvage in your inventory and hold, salvage gained, salvage per hour, Sailing XP, XP per hour, and time until the hold is full. Right-click it and choose **Reset** to start over. The whole box, its title and each line can be switched on or off under **Salvage counter** in config.

## Plugin Hub rules

- The deposit swap only reorders menu entries the game already made, in `PostMenuSort`, the same way Menu Entry Swapper works. Nothing is clicked or sent for you.
- Hiding uses `RenderCallbackManager` / `RenderCallback.addEntity` for crewmates, and blanks overhead text locally. Everything else only displays information or sends local notifications.
- Nothing is sent over the network.

## How the tricky parts work

- **Own boat:** the boat you stand on is a `WorldEntity`, and it counts as yours when `getOwnerType() == OWNER_TYPE_SELF_PLAYER`. Boat objects are mapped onto the sea with `transformToMainWorld`.
- **Hold count:** the game only sends a hold's contents (inventories 963–967, one per boat slot from varbit `SAILING_LAST_PERSONAL_BOAT_BOARDED`) while its interface is open. That read is exact. In between, the count goes up by one slot for each crew line about hooking salvage, by the inventory slots that empty after you click a deposit option on the hold (or get the "You deposit some cargo…" message), and jumps to full when a crewmate says the hold is full. A `~` before the number means it's an estimate. Opening the hold resyncs it. Each boat's count is saved to your RuneLite profile, so after a reload or restart it carries on as an estimate; **open the hold once on a boat the plugin has never seen** to start its count.
- **Wreck timers** are upper limits. A wreck lasts its lifetime from the moment anyone first salvages it, which the client can't see, so its clock starts when salvage first comes in while it's in reach of your hooks.
- **Crew jobs** come from the `SAILING_CREW_SLOT_n` / `_POSITION` varbits. Position 0 means idle, and 10, 13 and 14 are hooks.
- **"On a hook"** for the player and crew comes from animation: drop, hook-down loop or pull means working; inactive or reset means idle at the hook; the "interact" loops mean sorting.

All IDs and facts are in [`SalvageIds.java`](src/main/java/com/salvagehelper/SalvageIds.java) and [`ShipwreckType.java`](src/main/java/com/salvagehelper/ShipwreckType.java). The IDs are RuneLite gameval constants.

## Credits

Many of the alert, wreck and on-deck features were modelled on [AFK Shipwrecks](https://github.com/Brettgod1355/AFK-Shipwrecks) by Brettgod1355, and several game facts come from it: hold capacities, wreck levels and lifetimes, hook reach, crew job values, chat phrases, the animation grouping and the list of items the hold accepts. Salvage Helper's code is its own. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Running it

Requirements: JDK 11+ and a Jagex account set up for the dev client.

```bash
./gradlew run
```

That starts RuneLite in developer mode with the plugin loaded. To sideload it into your normal client instead, run `./gradlew build` and copy `build/libs/salvage-helper-1.0.0.jar` into `%USERPROFILE%\.runelite\sideloaded-plugins`.

## Troubleshooting

- **Cargo count shows `?`.** The game only sends the hold's contents while it is open. Open the hold once on that boat and the count starts; after that it is saved.
- **Cargo count drifts (`~`).** Between openings the count is estimated from crew lines and your deposits. A bank boat or a crewmate banking the hold at a dock isn't seen, so open the hold to resync.
- **Something isn't detected.** Start the client with `--debug`: the plugin logs the cargo hold's menu options, the crew it sees and their jobs, and why a hook-empty reminder fired.
