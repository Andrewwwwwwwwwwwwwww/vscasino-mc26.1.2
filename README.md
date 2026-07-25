# VanillaSkills Casino

An add-on for [VanillaSkills](https://github.com/Andrewwwwwwwwwwwwwww/vanillaskills) that adds a
casino to the skill screen. Bet your Quest Shards on slots, blackjack, or video poker.

Fully **server-side**: every screen is a chest menu drawn and translated by the server, so vanilla
clients can play with nothing installed and no resource pack.

## Requirements

- Minecraft **26.2**, **Fabric** with Fabric API
- **VanillaSkills 1.7.5 or newer** (the casino button hooks into its skill screen)

## Getting in

Open the skill tree and click the **Casino** button above your stats head, or run `/casino`.

## The games

- **Slots** — three weighted reels. Three of a kind pays big, two pays small.
- **Blackjack** — dealer stands on 17; blackjack pays 3:2. Close the screen mid-hand and it picks up
  where you left off.
- **Video Poker** — five-card draw, jacks or better pays, up to 250x on a royal flush.

## Chips

There are no chip items. Everything is wagered directly in **Quest Shards**, read from and written
back to VanillaSkills, so there is nothing to lose on a crash, nothing to dupe, and nothing to drop.
Your balance is always the single source of truth.

## Configuration

Per-world, at `<world>/vscasino/casino.json`. Reload it live with `/casino reload`.

**Wagering**

| Setting | Default | What it does |
| --- | --- | --- |
| `minBet` / `maxBet` | 1 / 25 | Stake limits, in Quest Shards |
| `netWinCap` | 500 | How far ahead a player can get before payouts stop. 0 disables |
| `bigWinLogThreshold` | 100 | Wins at or above this are logged (and announced, if enabled) |
| `broadcastBigWins` | false | Announce those wins in chat to everyone online |
| `betStepSmall` / `Medium` / `Large` | 1 / 5 / 10 | The six bet buttons either side of the action button |
| `slotsEnabled` / `blackjackEnabled` / `videoPokerEnabled` | true | Show each game in the lobby |

**Sound** — every cue is a sound id, so you can point them anywhere, including at your own resource pack's sounds. An id that doesn't exist simply plays nothing.

| Setting | Default |
| --- | --- |
| `soundsEnabled` / `soundVolume` | true / 0.7 |
| `soundBetChange` | `minecraft:ui.button.click` |
| `soundDeal` | `minecraft:entity.item.pickup` |
| `soundCard` | `minecraft:block.note_block.hat` |
| `soundReelStop` | `minecraft:block.note_block.bass` |
| `soundWin` | `minecraft:entity.experience_orb.pickup` |
| `soundBigWin` | `minecraft:ui.toast.challenge_complete` |
| `soundJackpot` | `minecraft:block.note_block.bell` |
| `soundLose` | `minecraft:entity.villager.no` |

**Slots**

| Setting | Default | What it does |
| --- | --- | --- |
| `slotsRtpPercent` | 95 | What fraction of stakes the reels return over time (clamped below 100) |
| `slotsAnimationEnabled` | true | Spin the reels, or resolve instantly |
| `slotsSpinTicks` | 26 | How long a spin lasts (20 ticks = 1 second) |
| `slotsReelStaggerTicks` | 5 | Gap between each reel stopping |
| `slotsSymbols` | six symbols | Per-symbol `weight`, `payThree` and `payTwo` — see below |

**Blackjack**

| Setting | Default | What it does |
| --- | --- | --- |
| `blackjackNaturalPercent` | 150 | Blackjack payout, as a percentage of the stake |
| `blackjackDealerStandsOn` | 17 | The dealer draws until this total |
| `blackjackAllowDouble` / `blackjackAllowSplit` | true | Offer those actions |
| `blackjackSplitAcesOneCard` | true | Split aces get one card each and stand |

**Video poker** — `pokerPayRoyalFlush` (250), `pokerPayStraightFlush` (50), `pokerPayFourKind` (25), `pokerPayFullHouse` (9), `pokerPayFlush` (6), `pokerPayStraight` (4), `pokerPayThreeKind` (3), `pokerPayTwoPair` (2), `pokerPayJacksOrBetter` (1). Each is the gross return per unit staked.

### Retuning the slot reels

`slotsSymbols` controls both how often each symbol lands and what a match of it pays:

```json
"slotsSymbols": {
  "diamond": { "weight": 3, "payThree": 60, "payTwo": 8.0 }
}
```

`weight` is relative — a symbol with twice the weight appears twice as often. Lowering a weight
makes that symbol rarer and its wins more exciting. Because payouts are scaled to hit
`slotsRtpPercent`, changing weights or multipliers alters **how wins feel** (frequent small ones vs
rare big ones) without changing what the machine pays out overall. Removing a symbol id drops it
from the reels entirely; the six built-in ids are `coal`, `wheat`, `iron`, `gold`, `emerald` and
`diamond`.

### A note on the economy

Quest Shards convert into Skill Shards, which buy the skill tree — so a casino that pays out too
generously inflates VanillaSkills' whole progression. The defaults are deliberately house-favoured,
and `slotsRtpPercent` is clamped below 100 so the machine can never be made to print shards. The
slots paytable is *scaled* to hit the configured rate, so changing a symbol's weight or multiplier
adjusts how wins feel without changing how much the game pays out overall.

## Commands

- `/casino` — open the lobby
- `/casino reload` — reload config and language files (operators)
- `/casino resetcaps` — clear everyone's winnings cap (operators)

## Translations

Every string, including the playing cards, lives in the language file. See
[TRANSLATING.md](TRANSLATING.md).
