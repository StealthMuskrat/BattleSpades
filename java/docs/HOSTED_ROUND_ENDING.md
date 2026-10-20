# Hosted round ending

`HostedRoundEnding` composes the existing ending policy with the live standard
match: ending music, the configured forced scoreboard, the five-second final-row
delay, actual `Scoreboard.gameStats`/result capture, shared ballot resolution,
configured score-screen dwell, voted-map rollover and ordinary in-place restart.
It retains the task independently of packet draining and simulation frames, so
network service, second schedulers and late joins continue during every wait.

Create the startup voting owner before initial scene attachment, create
`HostedTransitionServices` and its `MatchTransitions`, bind that transition owner
with `host.matchTransitions`, then construct `HostedRoundEnding` before connections
or simulation start. Pass `Settings.from(rawToml)`, the real bot end-phase callback,
and the embedding failure sink. The settings read `lobby.end_screen_seconds`
(default 12, bounded to 0–120) and `lobby.end_round_headline` (default true).
The mode's existing scoreboard setting remains authoritative. Custom domains and
embeddings without the new owner retain their explicit external ending hooks.

| Operation | Ownership and behavior |
| --- | --- |
| Final rows and results | Publish the real team award rows after five seconds and reserve finished results through the existing host result owner. The scoreboard's idempotence guard prevents duplicate result batches. |
| Same-map restart | Release forced scores, prepare bots, reset runtime/HUD/team/round state, restart actual mode objectives, respawn connected playable lives, and await bot reset without replacing the world or player objects. Omit terminal statistics UI. |
| Voted map | Await the shared ballot, consume the choice once, preflight the target, and let the retained transition service perform the scores-screen dwell and serialized scene transaction. Native statistics/headline UI remains restricted to maps with bundled screenshots. |
| Headlines | Preserve score-leader/forced-winner/draw selection and the dedicated Demolition, Occupation, VIP and Zombie messages. Suppress headlines when configured off. |
| Cancellation | Invalidate pure preflight and cancel score-screen timers. Retain and cancel the actual nested round-restart policy. Once destructive rollover starts, await reload/rollback and resource retirement before releasing the ending owner. Late worker or bot completions cannot restart a retired scene. |
| Shutdown | Stop new frames, await the frame in flight, retire the ending, deactivate the resulting current mode, then flush results. `ApplicationHost.close` enforces the ending/results shutdown contract. |

One grouped component case extends the existing transition harness to cover late
preflight completion, cancellable display dwell, destructive reload settlement and
setting validation. Three actual UDP cases extend the existing standard-host
fixture to cover final scores, late-join forced-score reveal, continued frames,
same-world/life restart, resolved shared voting and real map reload, and shutdown
during suspended bot preparation. Existing ending, restart and rollover golden
coverage remains in the consolidated suite.

The original source and golden resources are unchanged. Local source verification
also records the existing Zombie fixture failure in `test_retail_end_screen`: its
server facade lacks `respawn_player`. That result is not waived or represented as
a passing full Python baseline.

This completes the standard hosted ending composition. The ordinary standalone
launcher remains gated while bots/director, Tutorial, achievement evaluation/local
profiles, master/authentication, remaining commands and acceptance work are
incomplete. The required bot/HUD hooks remain explicit embedding services.

## Checkpoint verification

Completed the standard hosted round-ending composition: ending music, forced scores, final rows/results, shared-ballot waiting, same-world restart, voted-map rollover, mode-specific headlines and ordered shutdown. Actual nested restarts are retained and cancelled; pure preflight/display waits are retired, and destructive scene transactions are awaited. **6,435 Java tests pass** (6,357 component + 78 integration), with zero failures/errors/skips. Strict Java 25 check/build, portability, distributions and launcher smoke checks pass. The CRC-valid JAR is 2,715,579 bytes, SHA-256 `f6d06ee3ca3543d5e82c6c2aabff2857ff61df800af17282f51e17614d4c2304`, class major 69. Of 36 selected unchanged Python tests, 35 pass and one existing Zombie fixture fails because its server facade lacks `respawn_player`; the full Python baseline remains unresolved. All seven source directories and 263 existing resources are unchanged. All five CI jobs are pending for this implementation. Ordinary standalone hosting and the broader migration remain incomplete. See [HOSTED_ROUND_ENDING.md](HOSTED_ROUND_ENDING.md).
