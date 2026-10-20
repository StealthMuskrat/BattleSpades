# Local Java plugins

Plugins use `net.battlespades.plugin.RuntimePlugin` and a standard ServiceLoader descriptor. Copy `ExamplePlugin.java` into a separate build project, compile against the Java 25 application JAR, and package it with this UTF-8 text file:

`META-INF/services/net.battlespades.plugin.RuntimePlugin`

```text
example.ExamplePlugin
```

Place the resulting `example.jar` in the configured plugin directory. The example is opt-in and is not registered in the application JAR. Its notices use the top-screen overlay lane. It ports the source example's 3/5/10 kill announcements, including its numeric-ID streak bookkeeping and reset on victim death; it is an example policy, not the authoritative achievement tracker.

Metadata names must remain stable. Hooks return `CompletionStage<Void>` and execute on the gameplay owner. Resume authoritative work through `context.owner()` after background I/O. Obtain `context.match()` when a hook runs so a plugin does not retain a retired scene. Event arguments use the existing `ModeEvents.Event` owner objects; common kill/spawn/leave rows are `Scoreboard.Player`. The `on_tick` event receives the frame integer. Event return values do not cancel authoritative gameplay, matching the source manager.

Lifecycle hooks are `onLoad(context)`, `onUnload()`, `onEnable()` and `onDisable()`. Ordinary callback failures are isolated. A tick's elapsed budget skips later callbacks; it cannot preempt a callback already running. Construction happens on the owner after worker-side classpath discovery. The host awaits readiness before frames and drains accepted lifecycle/events before unload and worker-side classloader disposal.

Configuration retains `[plugins] enabled`, `path`, `allowlist`, `denylist` and `[network] plugin_event_budget_ms` (minimum 0.1, default 2). Filters match casefolded JAR stems or plugin names; deny wins. Private `_*.jar`, disabled suffixes and paths escaping the configured root are ignored. Trusted local plugins have ordinary Java process privileges. Python plugins require a Java port; this SPI does not execute Python.

Ordinary standalone competitive startup remains gated pending the rest of the migration. The hosted composition API is exercised by the integration suite.
