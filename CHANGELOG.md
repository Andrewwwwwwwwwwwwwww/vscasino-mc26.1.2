# VanillaSkills Casino Changelog

## [1.0.2] - 2026-08-26

### Fixed
- **The 1.0.1 gold-block fix never reached servers configured under 1.0.0.** The pack URL and SHA-1 are stored in `casino.json`, so a server whose config predated 1.0.1 kept pushing the old pack — the one whose broken `gold_block` fallback stripped every gold block's inventory texture — no matter which jar it ran. A stored default URL from a superseded release is now upgraded to the current pack on load (and saved back); a hand-set custom URL is never touched.

## [1.0.1] - 2026-08-22

### Fixed
- **Gold blocks rendered with no texture in the inventory.** The pack overrides `gold_block` so a slots icon can ride on it, and its fallback pointed at `minecraft:item/gold_block` — a model that does not exist, because gold block is a block item and its model lives under `models/block/`. Every other override in the pack was already correct; `gold_nugget` worked precisely because a nugget is a real item.

## [1.0.0] - 2026-07-24

### Added
- **Casino button on the skill screen**, directly above your stats head. Opens a lobby with three games.
- **A "How to play" book in every game**, opening a real written book with the rules. Video poker's explains hand types and the paytable in full. Every page is a language key, so translators can add or drop pages per language.
- **Doubling and splitting in blackjack.** Double on any two-card hand (including after a split) for exactly one more card. Split two cards of equal value into two hands, each with its own stake; split aces get one card each.
- **Consistent bet controls in every game** — `-10 / -5 / -1` to the left of the action button and `+1 / +5 / +10` to its right, in the same row position everywhere. The three step sizes are configurable.
- **Sound throughout** — bet clicks, dealing, cards landing, reels stopping, and distinct cues for a win, a big win, a jackpot and a loss. Every cue is a configurable sound id, so you can repoint any of them (including at your own resource pack's sounds) or turn sound off entirely.
- **Spinning slot reels.** Reels stop left to right with a rising click. The outcome is decided the moment you pull the lever and the animation only plays it back, so closing the screen or disconnecting mid-spin still settles correctly. Configurable length and stagger, or turn it off for instant results.
- **Much more is configurable**: per-symbol slot weights and payouts, the whole video poker paytable, the dealer's stand-on total, whether double and split are offered, split-ace behaviour, bet step sizes, and an optional server-wide announcement for big wins.
- **Slots** — three weighted reels. Three of a kind pays big, two pays small. The payout table is scaled automatically so the machine returns exactly the configured payout rate.
- **Blackjack** — dealer stands on 17, blackjack pays 3:2 by default. Hands are kept server-side, so closing the screen mid-hand resumes it rather than losing your stake.
- **Video Poker** — five-card draw, jacks or better pays, up to 250x for a royal flush.
- **Everything is wagered in Quest Shards**, using VanillaSkills' own balances. There are no chip items to lose, dupe, or trade — your balance is always the authority.
- **Full localization.** Every string in the mod, including card ranks and suit symbols, comes from the language file. Drop a `<locale>.json` in the jar or in `<world>/vscasino/lang/` to translate it. See `TRANSLATING.md`.
- **Per-world config** at `<world>/vscasino/casino.json`: bet limits, payout rate, blackjack payout, a net-winnings cap, and per-game on/off switches. Reload with `/casino reload`.
- `/casino` opens the lobby; `/casino reload` and `/casino resetcaps` are operator commands.

### Notes
- Quest Shards convert into Skill Shards, so casino winnings feed progression indirectly. The defaults are intentionally house-favoured (95% slots payout rate, a 500-shard net winnings cap) to keep the casino from inflating the skill tree. Tune them per server.
