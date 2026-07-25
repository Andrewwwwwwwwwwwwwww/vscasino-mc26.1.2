# Translating VanillaSkills Casino

Everything a player sees — the lobby, all three games, every message, and the playing cards
themselves — is translatable. There is one file: `en_us.json`.

## For translators

1. Start from the English template:
   [`src/main/resources/assets/vscasino/lang/en_us.json`](src/main/resources/assets/vscasino/lang/en_us.json).
2. Translate only the **values**, never the keys.
3. **Keep the placeholders**: `%d` (numbers), `%s` (text). Reorder with `%1$s`, `%2$d` etc. if your
   language needs a different word order.
4. Save as `<locale>.json` using your Minecraft language code (e.g. `zh_tw.json`), as **UTF-8**.
   Partial translations are fine — anything untranslated falls back to English.

### Key groups

| Prefix | What it is |
| --- | --- |
| `vscasino.menu.*` | Lobby, shared buttons, balance and bet lines |
| `vscasino.msg.*` | Rejection messages (bet limits, not enough shards, winnings cap) |
| `vscasino.cmd.*` | `/casino` command feedback |
| `vscasino.slots.*` | The slot machine, including reel symbol names |
| `vscasino.blackjack.*` | Blackjack table and results |
| `vscasino.poker.*` | Video poker, including the names of every poker hand |
| `vscasino.card.*` | Card ranks and suits (see below) |

### Cards are translatable too

Cards are drawn as text, so you control exactly how they read:

- `vscasino.card.rank.*` — the rank labels (`A`, `2`…`10`, `J`, `Q`, `K`).
- `vscasino.card.suit.*` — the suit symbols. These ship as `♠ ♥ ♦ ♣`; replace them with words
  (`Spades`) or other symbols if that reads better in your language or on your server.
- `vscasino.card.format` — how a rank and suit combine. Default is `%s%s` (e.g. `A♠`).
  Change it to `%s of %s` for a wordier style, or swap the order with `%2$s%1$s`.

## For the server owner (installing a translation)

Menus are drawn by the **server**, per player, from the language their client reports — so vanilla
clients get translated text with nothing installed.

- **This server only:** drop the file at `<world>/vscasino/lang/<locale>.json` and run `/casino reload`.
- **Bundle for everyone:** send finished translations to the author to be added to the jar.

## Bundled translations

- English (`en_us`)
