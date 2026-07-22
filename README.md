# Ping Worlds

A [RuneLite](https://runelite.net/) plugin that scans Old School RuneScape worlds for **ping and
jitter** and shows you, at a glance, which are the most *stable* to log into — right from the login
screen, before you commit to a world.

Latency alone doesn't tell the whole story: a world that pings `40, 41, 40 ms` is far better for
tight content (Inferno, solo bossing, PvP) than one that pings `20, 90, 25 ms`, even though the
second is sometimes faster. **Ping Worlds measures both** — average latency *and* jitter (how much
the ping bounces around) — so you can pick a world that stays steady.

## Features

- **Ping + jitter per world**, shown in a compact World-Hopper-style sidebar table with a
  **Stability** column (green check = fast *and* steady, red cross = not) you can hover for details.
- **Smart, relevance-first scanning.** Every eligible world is measured for jitter, but the ones
  that matter to you — your region and account type first — are measured first, in gentle batches.
- **Play-style profiles** that filter which worlds are relevant: *Stable bossing / Inferno*,
  *Leagues*, *Free-to-play*, *PvP*, or *Custom* (your own world list).
- **Click to switch.** Click any world to hop to it (or set it as your login world when logged out),
  and it stays monitored.
- **Tunable thresholds** for what counts as a "green" world (max average ping, max jitter).

## Configuration

| Setting | What it does |
|---|---|
| **Play-style profile** | Which worlds are relevant (Stable bossing, Leagues, F2P, PvP, Custom). |
| **Region** | Which region to prioritise; *Auto* matches the world you currently have selected. |
| **Account type** | Members or free-to-play. |
| **Custom world list** | Comma-separated world numbers, used by the *Custom* profile (e.g. `301, 302, 330`). |
| **Worlds to show** | How many worlds to list in the panel (all eligible worlds are still scanned). |
| **Samples per world** | How many recent pings define a world's average and jitter. |
| **Max average ping / Max jitter** | The thresholds for the green check. |

## How it decides "stable"

A world earns a green check when its rolling **average ping** *and* its **jitter** (population
standard deviation of recent pings) are both under your configured thresholds. Timed-out pings are
counted but excluded from the latency maths, so a flaky world can't hide behind a good average.

## Building / running locally

Requires a JDK that your Gradle can run on (JDK 11–21; the plugin itself targets Java 11).

```sh
./gradlew build      # compile + run the unit tests
./gradlew run        # launch a RuneLite dev client with the plugin loaded
```

On macOS with a modern JDK, the `run` task adds the Apple UI `--add-exports` flags needed to launch
the dev client; this affects only local running, not the built plugin.

## Note on fair use

Ping Worlds only pings the game worlds (the same measurement RuneLite's own World Hopper uses),
one world at a time on a steady, gentle cadence. It does not automate gameplay, inject input, or
send any of your data anywhere.

## License

[BSD 2-Clause](LICENSE).
