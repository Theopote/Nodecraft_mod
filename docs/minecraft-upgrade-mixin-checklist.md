# Minecraft Upgrade — Mixin Regression Checklist

Use this checklist after bumping `minecraft_version`, Yarn mappings, Fabric Loader/API, or Fabric Loom.
ImGui integration requires hooks across input, rendering, HUD, and world interaction; mixin injection failures
usually appear at compile time, but behavior regressions need manual verification.

Configuration: [`nodecraft.mixins.json`](../src/main/resources/nodecraft.mixins.json)

## Step 1 — Build Gate

```powershell
.\gradlew --no-daemon --console plain compileJava
.\gradlew --no-daemon --console plain test
```

Fix any mixin apply errors (missing methods, signature changes) before manual testing.

## Mixin Inventory

| Class | Target | Methods | Purpose | Risk |
|-------|--------|---------|---------|------|
| `MinecraftClientMixin` | `MinecraftClient` | `stop`, `setScreen`, `tick`, `onResolutionChanged`, `handleInputEvents` | Lifecycle, resolution rebuild, input cancellation | High |
| `GameStateMixin` | `MinecraftClient` | `setScreen`, `disconnect(...)` | Activate/deactivate NodeCraft mode, force cleanup on disconnect | High |
| `MouseHandlerMixin` | `Mouse` | `onCursorPos`, `onMouseButton`, `onMouseScroll` | UI hit-testing, middle-button look, scroll capture | High |
| `MouseCursorMixin` | `Mouse` | `lockCursor`, `unlockCursor` | Keep cursor in NORMAL mode while editor is open | Medium |
| `KeyboardMixin` | `Keyboard` | `onKey`, `onChar` | Block MC keyboard/char when mouse is over ImGui | High |
| `KeyboardInputMixin` | `KeyboardInput` | `tick` | WASD movement outside UI; disable movement inside UI | High |
| `RenderSystemMixin` | `RenderSystem` | `flipFrame` | Render pending ImGui draw data before buffer swap | High |
| `WorldRendererMixin` | `WorldRenderer` | `renderTargetBlockOutline` | Suppress vanilla block outline in editor | High |
| `InGameHudMixin` | `InGameHud` | `renderCrosshair` | Hide crosshair in NodeCraft mode | Medium |
| `ClientPlayerInteractionManagerMixin` | `ClientPlayerInteractionManager` | `interactBlock`, `attackBlock` | Forward block clicks; prevent breaking blocks | Medium |
| `ScreenMixin` | `Screen` | `shouldPause`, `close` | Do not pause game; clean close | Low |
| `NodecraftScreenMixin` | `NodecraftScreen` | `shouldPause` | Same as above (duplicate hook) | Low |
| `GameRendererAccessor` | `GameRenderer` | `@Invoker("getFov")` | Accurate picking FOV for ray tests | Medium |

Notes:

- `Mouse` and `MinecraftClient` each have **two** mixin classes.
- `shouldPause` is injected in both `ScreenMixin` and `NodecraftScreenMixin`.

## Step 2 — Manual Regression (in-game)

Open the NodeCraft editor in a world and walk through the sections below.
Estimated time: 15–20 minutes per MC upgrade.

### High priority — input and rendering

- [ ] **Mouse over UI**: clicks, drags, and scroll wheel affect ImGui only (no block selection / hotbar scroll-through).
- [ ] **Mouse outside UI**: cursor moves freely; no accidental camera drift unless middle button is held.
- [ ] **Middle-button look**: holding middle mouse outside the UI rotates the camera smoothly.
- [ ] **Cursor mode**: cursor stays visible (not locked) while the editor is open.
- [ ] **Keyboard over UI**: typing in ImGui text fields works; MC key binds (inventory, drop, chat) do not fire.
- [ ] **WASD outside UI**: movement works when the cursor is outside the NodeCraft panel.
- [ ] **WASD inside UI**: movement is disabled when the cursor is over the panel.
- [ ] **ImGui draw**: panels render every frame without flicker, double-draw artifacts, or missing widgets.
- [ ] **Input bus**: no ghost clicks or camera snaps when moving between UI and world regions.

### Medium priority — world interaction and HUD

- [ ] **Block outline**: vanilla blue target outline is hidden; custom block highlight/preview still works.
- [ ] **Crosshair**: hidden while NodeCraft mode is active; restored after closing the editor.
- [ ] **Right-click block**: forwards to the node editor interaction system; does not open chests / trigger use actions.
- [ ] **Left-click block**: does not break blocks or start cracking animation.
- [ ] **Edge picking**: block selection near screen edges remains accurate (uses `GameRendererAccessor.getFov`).

### Low priority — lifecycle

- [ ] **No pause**: world keeps ticking (entities, fluids, redstone) while the editor is open.
- [ ] **Close editor**: ESC / close path releases resources and returns to normal gameplay cleanly.
- [ ] **Switch screens**: leaving NodeCraft for another screen deactivates NodeCraft mode.
- [ ] **Resolution change**: resizing the window rebuilds the editor layout correctly.
- [ ] **Disconnect / quit world**: exiting to title or disconnecting force-closes the editor and clears NodeCraft state.

## Step 3 — Common Break Points

When Yarn remaps change, check these areas first:

1. **Input types** — `MouseInput`, `KeyInput`, `CharInput`, `PlayerInput` (`KeyboardMixin`, `MouseHandlerMixin`, `KeyboardInputMixin`).
2. **Render pipeline** — `flipFrame` parameters, `WorldRenderState`, `renderTargetBlockOutline` signature.
3. **Disconnect signature** — `MinecraftClient.disconnect(Screen, boolean, boolean)` in `GameStateMixin`.
4. **Accessor targets** — `GameRenderer.getFov` name/signature for `GameRendererAccessor`.

## Related Gradle Properties

After a MC bump, also verify in [`gradle.properties`](../gradle.properties):

- `minecraft_version`
- `yarn_mappings`
- `loader_version`
- `fabric_api_version`
- `loom_version`

Then re-run preset and graph serialization tests if node or port IDs changed.
