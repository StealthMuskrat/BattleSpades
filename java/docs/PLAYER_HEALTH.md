# Accepted damage and healing

`LifeRuntime` now applies accepted connected-player/world damage and integer HP
healing to `PlayerLife`. The caller must first authorize the hit, action or mode
effect. No current client packet handler directly invokes this trusted API.

## Behavior

- Dead/god-mode victims return before source attribution. God mode survives
  respawn; health, maximum health and the victim contribution ledger reset.
- Synchronous chained-blast scopes preserve the outermost non-null instigator,
  unwind on exceptions and enforce close order. Qualifying teammate instigation
  changes damage credit; enemy instigation and world damage retain source rules.
- Victim protection precedes attacker-protection cancellation and mode changes.
  Mode vetoes precede damage scales and the source one-hit kill-type set. An absent
  hook differs from an identity hook, which can veto non-positive input first.
- Weapon/Zombie scales apply to other-player damage only. Ties-to-even rounding
  follows them. The wall-clock damage stamp updates even if the result rounds to
  zero; nonfinite accepted values fail before HP/history changes.
- Only removed HP enters enemy history. Entries retain actor identity, team,
  generation and monotonic time; new enemy hits prune expired/departed identities.
  Healing proportionally reduces retained contributions, without pruning expiry.
  Healing never lowers current HP, including HP already above the healing cap.
- Lethal damage uses the verified death/counter/respawn path. Contribution evidence
  is consumed once before the death broadcast. Damage/healing observers retain
  their ordering relative to HP, recent-hit state and death.
- World hits retain the last player interaction. Falls/transitions use the source
  recent-enemy window, allowing credit to dead connected attackers. Credited
  transitions use the source ordinary weapon kill type. The original timestamp
  and current numeric-ID lookup semantics, including equal-time respawns and ID
  reuse, are retained separately from contribution-ledger identity checks.
- SetHP preserves world/self/hit or explicit type and fixed source coordinates;
  wire HP clamps at 255. Healing sends type 2 even if nothing was restored. HP
  packets are suppressed during loading. Dead-join repair retains the source's
  default 100-HP notification. Normal reveal does not invent an extra HP repair;
  complete owner WorldUpdate replication remains pending.

Owner controls and reliable gameplay broadcasts now share bounded reliable
ordering. A queued SetHP previously could be overtaken by KillAction. The live
fast path sends immediately when possible and queues subsequent reliable traffic
in order under backpressure. Exhaustion retires the session and releases its ID.
Unreliable delivery retains its existing semantics.

## Boundaries

Required policy ports supply the optional concrete mode damage hook, original
round/UGC scoring gate and actual live admission state. Required effect ports
receive damage/healing, pre-broadcast death evidence and transition-credit events.
This records contribution evidence; complete assist payouts, round/profile awards,
achievements and conduct warnings/kicks remain observer work.

Detached projectile actors, hit/friendly-fire/action authorization, firing/reload,
full input and flight/fuel timelines and owner replication remain pending. A
damage source ID names a current connection, not a disconnected projectile owner.
Concrete modes, authentication and playable hosting remain incomplete. Main still
returns exit 2 for ordinary hosting.

## Verification

The generator executes original Player damage/heal, conduct attribution, combat
history/healing and recent-enemy/transition methods unchanged, with compiled
original World/VXL/packet codecs. A Python profile hook observes original
combat-score call boundaries without replacing functions. Other source observers
run; their awards/assist-score packets are outside this checkpoint's expected
state. Inputs explicitly configure clocks, rules, mode arithmetic, positions and
conduct warning/kick settings.

| Source comparisons | Vectors |
|---|---:|
| Damage scales, rounding, protection, types and mode hooks | 1152 |
| Healing caps, negative/zero amounts and loading gates | 120 |
| Nested blast attribution | 80 |
| Stateful history, healing, death, expiry, reuse and transitions | 832 |

Java checks frozen evidence, thread ownership, exception cleanup, observer/wire
order and reliable backpressure. The new UDP test covers gated HP suppression,
live damage/healing, god mode, lethal HP before KillAction and fresh respawn with
production domain/session/body code and explicit test mode/input/effect ports.
Packaged native probes retain fixture player lives. This is not a supported-client
gameplay test.

Focused original kill-feed/combat-score/conduct/explosion suites: **46 passed**.
The broader original rules audit has **45 passes and one existing VIP failure**:
`test_vip_match_ends_after_the_configured_rounds_played`. It reproduces in isolation
with original Python/Cython/mode files unchanged. The reference discrepancy is
outside this health checkpoint; see the migration report for retained evidence.
