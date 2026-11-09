# RFC-20261109: The tsukumogami

## Status
Draft

## Summary

When a player lets their reviews pile up, a 付喪神 (tsukumogami) follows them at night. In folklore, tsukumogami are objects that come alive after years of neglect. Here, the player's own neglected items become the mob. It grows stronger the more items are overdue, stands still while its owner reviews, and leaves once enough reviews are done. It gives the player a reason to review that lives inside the game, instead of only in a menu.

## Motivation

Oboecraft locks items until the player reviews them, but nothing pushes a player who simply stops reviewing. They can play around their locked items, and the backlog grows quietly.

The tsukumogami makes that backlog felt. It should:

- **Push towards reviewing, not punish for playing.** The only real way to get rid of it is to review.
- **Be fair.** It never takes items, never pays to summon, and can't be cheesed away.
- **Fit the theme.** Neglected objects coming alive is exactly what a tsukumogami is.

## Proposal

### When it appears

The **overdue count** is the number of distinct locked items the player carries that are at level 1 or higher. Stack size doesn't matter, so 64 locked planks count as one.

- Banned items count. Otherwise pressing Don't know on everything would be a cheap way out.
- Level 0 items don't count. They were never learned, and counting them would punish exploring.
- Items in containers don't count, since the player can only review what they carry.

The tsukumogami appears when the overdue count reaches the **Tsukumogami threshold**. It only comes at night in the Overworld, for players in survival or adventure mode, and never on Peaceful.

### What it does

Each player has at most one tsukumogami. It follows and attacks only its owner, and other players and mobs ignore it.

| Aspect | Behaviour |
|---|---|
| Arrival | Spawns 16 to 24 blocks away, out of sight if possible, so it arrives rather than pops in |
| Movement | Walks slower than the player and never runs. Teleports behind its owner if it falls too far behind or gets stuck |
| Attack | A slow, heavy hit with a visible wind-up |
| Health | Grows with the overdue count, up to a cap. Damage and speed stay the same, so a big backlog makes it harder to kill, not deadlier |
| Damage taken | Only from its owner. Friends, lava and traps can't clear it |
| During a review | Stands still and watches, for at most the **Tsukumogami review freeze**, so the answer menu can't be held open all night |
| Sleeping | Its owner can't sleep while it exists, since sleeping would skip the night |

When the overdue count changes, its health changes with it. A pass in the middle of a fight weakens it right away.

### How it leaves

- **Reviewing:** when the overdue count drops below the threshold, its orbiting items fall away and it fades out. This is the intended way out.
- **Killing:** it is gone for the rest of the night.
- **Dawn:** it leaves at sunrise.

It is also removed when its owner dies, logs out, leaves the Overworld or changes to creative or spectator mode. It drops nothing, and it is never saved with the world. Whether it should exist is always worked out again from the player's progress.

### What the player sees and hears

The tsukumogami is a menreiki (面霊気), a noh mask that came alive. It is tall and stooped, in a faded kimono, with the owner's overdue items orbiting its shoulders like a dark halo. Each passed review knocks one item out of the ring.

When it spawns, its owner gets a message telling them to review to drive it away, and a boss bar shows its health and the overdue count. The threshold is never shown, so the goal is "review until it leaves", not "do the bare minimum".

Its sounds are a temple bell when it arrives, heavy geta steps as it walks, and a wind chime when it fades away. It never speaks, and never says the names of the overdue items, since that would give the answers away.

### Settings

| Setting | Default | Meaning |
|---|---|---|
| Tsukumogami threshold | 5 | The overdue count it appears at. 0 turns it off |
| Tsukumogami review freeze | 60s | How long it stands still during one review |

Both are server settings, like the others.

### Delivery

The work is split into three issues under #19:

1. **#51 The mob.** Everything about its rules, behaviour, model and animations, using vanilla sounds for now.
2. **#52 Its sounds.** Its own sounds, each under terms that allow it to ship and credited in `CREDITS.md`. This lands before the first release with the tsukumogami.
3. **#53 A better look.** Further work on the model and animations, for whoever can raise the bar.

The rules, like the overdue count, its health and the review freeze, live in `core`. The mob itself lives in `fabric`. It is built with Minecraft's own entity models and animations, so the mod still only needs Fabric API.

## Alternatives Considered

- **More mobs for a bigger backlog.** Rejected. One mob per player is easier to read and to balance, and a stronger mob says the same thing.
- **Scaling its damage with the backlog.** Rejected. A deadly mob would punish the player, not push them to review. More health makes it harder to kill while keeping it fair.
- **Counting every locked item, including level 0 and containers.** Rejected. It would punish exploring and storing items, and the player couldn't review its way out from where they stand.
- **A pure UI reminder, like a counter or a warning.** Rejected. It is easy to ignore, and the mod's goal is to put learning inside the game.
- **An animation library.** Rejected, to keep Fabric API the only dependency.

## Open Questions

- Are the default threshold of 5 and freeze of 60 seconds right, or do they need playtesting first?
- On a multiplayer server, an owner who can't sleep can hold up the night for everyone. Is it enough that the owner can fix this by reviewing, and that servers can lower `playersSleepingPercentage`?
- Should a player be able to see their overdue count before the tsukumogami appears, as a warning?
