# Cache Busting for Static Assets

## Overview

This project includes automatic cache busting for JavaScript assets to prevent browsers from serving stale cached files after deployments.

## How It Works

When you compile the Scala.js frontend, the build process:

1. **Generates asset hashes** - Computes a SHA-256 hash of each `.js` file in `/static/js/`
2. **Creates versioned copies** - Copies files with hashed names (e.g., `main.js` → `main.b8af4e6e.js`)
3. **Updates HTML files** - Automatically updates all HTML files to reference the hashed versions
4. **Includes source maps** - Also versions and updates source map references

## Usage

### Development Workflow

After compiling the JavaScript:

```bash
# Compile JS (development)
sbt "cascadeJS / fastLinkJS"

# Run cache busting
sbt cacheBust
```

Or use the watch mode and run cache busting when needed:

```bash
# Watch mode for JS
sbt ~"cascadeJS / fastLinkJS"

# In another terminal, run cache busting when you deploy
sbt cacheBust
```

### Production Build

The production build process (via nixpacks.toml) automatically runs cache busting:

```bash
sbt cascadeJS/fullLinkJS
sbt cacheBust           # ← Automatically included in production builds
sbt cascadeJVM/stage
```

## SBT Tasks

The following SBT tasks are available:

- `cacheBust` - Complete cache busting process (recommended)
- `generateAssetHashes` - Only generate hashes without copying files
- `updateHtmlWithHashes` - Only update HTML files with existing hashes

## Example

Before cache busting:
```html
<script src="/static/js/main.js"></script>
```

After cache busting:
```html
<script src="/static/js/main.b8af4e6e.js"></script>
```

The hash changes whenever the content changes, forcing browsers to fetch the new version.

## Files Affected

- All `.js` files in `jvm/src/main/resources/static/js/`
- All `.html` files in `jvm/src/main/resources/static/`
- Source maps (`.js.map` files)

## Implementation Details

- **Hash algorithm**: SHA-256 (first 8 characters used)
- **Location**: `/project/CacheBusting.scala`
- **Integration**: Configured in `build.sbt` and `nixpacks.toml`
- **Original files**: Original `main.js` is kept alongside the hashed version

## Cleanup

The hashed files are generated artifacts. You may want to add them to `.gitignore`:

```gitignore
# Cache-busted assets (keep originals only)
jvm/src/main/resources/static/js/*.*.js
jvm/src/main/resources/static/js/*.*.js.map
```

However, for deployment purposes, you may choose to commit them if your deployment process doesn't run the full build.

