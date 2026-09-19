# NodeCraft

NodeCraft is a Fabric mod for Minecraft that adds an in-game node graph editor for procedural building, geometry generation, world operations, previews, and AI-assisted graph planning.

## Features

- Visual node graph editor inside Minecraft.
- Geometry, curve, pattern, material, world query, world write, preview, and export nodes.
- Live preview workflows for generated geometry, blocks, paths, regions, labels, and vectors.
- AI assistant panel that can turn natural-language requests into node graph plans.
- Local template/mock planning for common graph requests.
- Optional remote AI planner support through OpenAI-compatible or Anthropic-style chat APIs.
- Apply and undo support for AI-generated graph changes.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Fabric API
- Java 21

## Building

```powershell
.\gradlew build
```

The built mod jar will be written to `build/libs`.

For a faster compile-only check:

```powershell
.\gradlew --no-daemon --console plain compileJava
```

## Development Checks

Before submitting changes, run the narrowest useful check for the area you changed:

```powershell
.\gradlew --no-daemon --console plain compileJava
.\gradlew --no-daemon --console plain test --tests com.nodecraft.gui.preset.GraphPresetResourceTest
```

Use the full test suite when changing shared graph, execution, preview, or serialization behavior:

```powershell
.\gradlew --no-daemon --console plain test
```

Preset resources are expected to reference current node IDs and current port IDs. Runtime preset application should stay strict; migrations from old preset formats belong in converter tooling, not in the editor path.

## Using NodeCraft

1. Install the mod with Fabric and Fabric API.
2. Launch Minecraft.
3. Find the NodeCraft tool item in the Tools creative tab.
4. Right-click with the tool to open the NodeCraft editor.
5. Build a graph manually, or use the AI Assistant tab in the Inspector panel to generate a graph plan.

## AI Assistant

The AI Assistant can create or modify graph plans from natural-language prompts. Remote planning is optional and can be configured from the assistant settings panel.

The assistant can optionally attach a structured snapshot of the current player position/view and
completed world-region selection to a planning request. These world-context options are disabled by
default because enabled coordinates, block targets, and region bounds are sent to the configured
remote planner. Dynamic world context is captured once when a request is submitted and reused for
validation or graph-expansion retries.

Supported configuration includes:

- API base URL
- API key from a local proxy, environment variables, or the current in-memory settings session
- Model name
- Provider strategy
- Request timeout
- Conversation history size

Environment variables checked for API keys:

- `NODECRAFT_AI_API_KEY`
- `OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`

### Remote AI key safety

Remote planning runs inside the Minecraft client process. When remote planning is enabled,
NodeCraft may send an API key from the settings panel or from one of the environment variables
above to the configured API endpoint as the provider's authentication header.

**1.0 primary path (frozen):** run a local proxy that holds the provider API key and point
NodeCraft's API base URL at that local proxy. Leave the client API key empty unless the proxy
needs a local token. The proxy can enforce its own authentication, rate limits, model allowlist,
logging policy, and request filtering while keeping the provider key out of the Minecraft process.

**Secondary:** environment variables when calling a provider directly. Env vars avoid storing the
key in NodeCraft's settings file; they do not encrypt the key or isolate it from the running
client process.

Use remote planning only in trusted private environments. Any local tool, injected code,
untrusted mod, debugger, or JVM heap dump with access to the Minecraft process may be able to
recover the effective API key while it is in use. API keys entered in the settings panel are not
saved by default. If "Remember API key on disk (dev only)" is explicitly enabled, the key is
stored as plain text in the Minecraft game directory under `nodecraft/config/ai_settings.json`.
OS credential stores are intentionally not the 1.0 primary path for this client — they still
load the provider key into the shared-mod JVM.

AI request logs avoid prompt previews by default. The debug console and debug logging can include
additional request metadata, but prompt previews are only included when the explicit debug prompt
preview option is enabled.

Generated plans are validated before they can be applied. Connection creation uses the same type compatibility rules as the editor.

## Project Structure

- `src/main/java/com/nodecraft/core`: mod entrypoints and item registration.
- `src/main/java/com/nodecraft/gui`: editor UI, panels, dialogs, AI assistant UI, and ImGui integration.
- `src/main/java/com/nodecraft/nodesystem`: node API, graph model, execution, preview, data types, and node implementations.
- `src/main/resources/assets/nodecraft`: mod assets, language files, models, textures, fonts, and node icons.
- `docs/development`: current roadmap and phase checklists.
- `docs/architecture` / `docs/contracts`: design freezes and regression contracts.
- `docs/architecture/ai-assistant-subsystem.md`: AI as a first-class module (controller / session / planner / validator / apply); panel must not own orchestration.
- `docs/history`: archived status / fix notes (not current policy).
- `tools/migration`: canonical preset / id migration entrypoints (see README there).
- `scripts/`: maintained maintenance scripts (`build_v0_migration_manifest.py`, `canonicalize_presets.py`, …).

## License

NodeCraft is licensed under the MIT License. See [LICENSE](LICENSE).
