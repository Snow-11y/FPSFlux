# FPSFlux

**The definitive performance optimization mod for Minecraft 1.12.2, built on modern foundations.**
![FPSFlux Icon](src/main/resources/icon.jpg)
[![Java 25+](https://img.shields.io/badge/Java-25%2B-orange.svg)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2-brightgreen.svg)](https://www.minecraft.net/)
[![CleanroomMC](https://img.shields.io/badge/Powered%20by-CleanroomMC-blue.svg)](https://cleanroommc.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A next-generation performance mod exploiting Java 21, modern OpenGL, and intelligent caching to deliver unprecedented frame rates and responsiveness.

---

## What Makes FPSFlux Different?

**most performance mods is stuck in 2018.** They-all run on Java 8, legacy OpenGL, and outdated optimization techniques. FPSFlux rewrites the rules by leveraging:

- **Java 21 Mathematics** - `Math.fma()` chains, vector intrinsics, and SIMD optimizations for distance calculations that leave Java 8 implementations in the dust
- **CleanroomMC Foundation** - Access to modern Java APIs, LWJGL3, and cutting-edge JVM features while maintaining 1.12.2 compatibility
- **Modernity-First Design** - Built from the ground up to exploit 2025 technology, not 2017 limitations

**This isn't just entity culling.** It's a comprehensive performance overhaul targeting every bottleneck in the rendering pipeline.

---

## ✨ Current Features (v0.1.0 - WIP)

### Intelligent Entity Culling
Four-tier distance-based system that progressively disables entity AI, pathfinding, and animations based on player proximity:

- **Tier 0 (0-32 blocks):** Full vanilla behavior
- **Tier 1 (32-64 blocks):** Reduced AI tick frequency, simplified animations
- **Tier 2 (64-128 blocks):** Physics-only mode, pathfinding disabled
- **Tier 3 (128+ blocks):** Aggressive culling, static entities with minimal gravity checks

**Farm-Safe:** Even at maximum culling, gravity and collision detection remain active to prevent mob farm breakage.

### Java 21 Optimizations
- **FMA (Fused Multiply-Add) chains** for single-cycle distance calculations with zero intermediate rounding errors
- **Parallel stream processing** via `ForkJoinPool` for multi-core entity batch processing
- **Modern GC integration** leveraging ZGC/G1GC improvements for low-latency state tracking
- **SoftReference caching** for intelligent distance calculation reuse

### Performance Monitoring
- `/fpsflux status` - Real-time entity distribution across culling tiers
- Integration with Spark/Flare for profiling
- Debug overlays showing per-entity culling states

---

## Planned Features (Roadmap)

### Chunk Render Caching
**Cache rendered chunks to storage** - render once, never again until the chunk changes. Eliminates redundant CPU/GPU work for static geometry.

### Texture Caching System
**Offload texture data to disk** when not actively visible, freeing RAM for dynamic content. Intelligent prefetching prevents stutter.

### LoliASM/Chibi Integration Helpers
Maximize the impact of existing RAM optimizations through tight integration with LoliASM's allocator improvements and LoliAsm's memory profiling.

### Advanced Configuration System
- **GUI-based chunk culling exceptions** - mark specific chunks to never cull (spawn areas, farms, etc.)
- **Per-dimension distance controls** - different culling thresholds for Overworld vs. Nether vs. End
- **Whitelist/blacklist systems** for entity types and biomes
- **Hot-reload support** for live config changes without restarting

### Modern OpenGL Rendering Pipeline
**The Nothirium × Celerita merger you've been waiting for.**

Import Nothirium's multi-GL version support (1.0/1.5/2.0/3.2/4.3/4.5) while maintaining full compatibility with Celerita (the premier Embeddium/Sodium fork for 1.12.2). 

**No more choosing between renderers.** FPSFlux will bridge both, giving you:
- Nothirium's modern GL compatibility for cutting-edge hardware
- Celerita's optimized rendering pipeline
- Seamless fallback for older GPU architectures

*Note: No code will be directly copied from Nothirium - only feature parity through clean-room implementation respecting original licenses.*

### Java 25 Experimental Features
**4-byte object headers** via Java 25's compact object layout (disabled by default, enable in config if running Java 25+). Reduces memory footprint per entity by ~12 bytes, allowing denser entity packing before RAM limits.

---

## Installation

### Requirements
- **Minecraft 1.12.2 Forge** (recommended: 14.23.5.2847+)
- **Java 21 or higher** (Java 25 recommended for experimental features)
- **CleanroomMC-compatible launcher** (ZalithLauncher, MultiMC with Java override, etc.)

### Steps
1. Download the latest `.jar` from [Releases](https://github.com/Snow-11y/FPSFlux/releases)
2. Place in your `mods/` folder
3. Launch Minecraft with Java 21+ JVM arguments
4. Run `/fpsflux status` in-game to verify functionality

---

## 🛠️ Development

Built with the [CleanroomMC Template]([https://github.com/CleanroomMC/ExampleMod](https://github.com/CleanroomMC/ForgeDevEnv)) by Rongmario.

**Tech Stack:**
- Java 25 (compiles to Java 8 bytecode via Jabel)
- Gradle 9.2.1 + RetroFuturaGradle 2.0.2
- MixinBooter 10.2 for runtime bytecode injection
- Forge 14.23.5.2847

### Building from Source

git clone https://github.com/Snow-11y/FPSFlux.git
cd FPSFlux
./gradlew build


---

## 🙏🏻 Credits & Acknowledgments

This mod stands on the shoulders of giants:

### Core Contributors
- **Snow** **Snow/snow-11y** - Author, Lead developer

### Essential Dependencies & Inspiration
- **Rongmario** ([CleanroomMC](https://github.com/CleanroomMC), [MixinBooter](https://github.com/CleanroomMC/MixinBooter)) - Without the CleanroomMC ecosystem and the template that bootstrapped this project, FPSFlux would not exist. Special thanks for making modern Java development on 1.12.2 possible.
  
- **Meldexun** ([Nothirium](https://github.com/Meldexun/Nothirium)) - Pioneering work on modern OpenGL rendering for legacy Minecraft versions. Future GL pipeline integration will honor Nothirium's innovations.

- **embeddedt** ([Celerita](https://git.taumc.org/embeddedt/celeritas)) - The definitive Embeddium/Sodium fork for 1.12.2. Celerita compatibility is a first-class design goal.  
  *(Note: While kappa-maintainer hosts the GitHub page, embeddedt authored the initial release and owns the original TauMC repository.)*

---

## 📜 License

Licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

## Bug Reports & Feature Requests

Found a crash? Have an idea? [Open an issue](https://github.com/Snow-11y/FPSFlux/issues) with:
- Minecraft version
- Java version
- Mod list (use `/fml confirm` output)
- Crash log or reproduction steps

---

## 💬 Community

**This is a work-in-progress alpha.** Expect rough edges, crashes, and incomplete features. Feedback welcome - this mod improves through real-world testing.

**Current Status:** Entity culling functional, chunk caching in development, GL pipeline planned few days or weeks.

---

*"Performance optimization isn't about doing less work - it's about doing smarter work."*
