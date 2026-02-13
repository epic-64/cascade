#!/bin/bash
# Lines of Code Counter for Cascade Project
# Counts LoC by category: Scala (app/test), HTML, CSS, and build files

set -e

# Colors for terminal output (disabled when not interactive)
if [ -t 1 ]; then
    BOLD='\033[1m'
    GREEN='\033[0;32m'
    BLUE='\033[0;34m'
    YELLOW='\033[0;33m'
    NC='\033[0m' # No Color
else
    BOLD=''
    GREEN=''
    BLUE=''
    YELLOW=''
    NC=''
fi

# Function to count lines in files matching a pattern
count_lines() {
    local pattern="$1"
    local paths="$2"
    local count=0

    for path in $paths; do
        if [ -d "$path" ]; then
            local result=$(find "$path" -name "$pattern" -type f 2>/dev/null | xargs cat 2>/dev/null | wc -l)
            count=$((count + result))
        fi
    done

    echo "$count"
}

# Function to count lines in specific files
count_lines_in_files() {
    local files="$@"
    local count=0

    for file in $files; do
        if [ -f "$file" ]; then
            local result=$(wc -l < "$file")
            count=$((count + result))
        fi
    done

    echo "$count"
}

# Project root (script assumes it's run from project root or adjusts)
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$PROJECT_ROOT"

echo -e "${BOLD}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║         Cascade Project - Lines of Code Report             ║${NC}"
echo -e "${BOLD}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ============================================================================
# SCALA CODE
# ============================================================================

# Application Scala code (main sources)
JVM_APP_SCALA=$(count_lines "*.scala" "jvm/src/main/scala")
SHARED_APP_SCALA=$(count_lines "*.scala" "shared/src/main/scala")
JS_APP_SCALA=$(count_lines "*.scala" "js/src/main/scala")
TOTAL_APP_SCALA=$((JVM_APP_SCALA + SHARED_APP_SCALA + JS_APP_SCALA))

# Test Scala code
JVM_TEST_SCALA=$(count_lines "*.scala" "jvm/src/test/scala")
SHARED_TEST_SCALA=$(count_lines "*.scala" "shared/src/test/scala")
JS_TEST_SCALA=$(count_lines "*.scala" "js/src/test/scala")
TOTAL_TEST_SCALA=$((JVM_TEST_SCALA + SHARED_TEST_SCALA + JS_TEST_SCALA))

TOTAL_SCALA=$((TOTAL_APP_SCALA + TOTAL_TEST_SCALA))

echo -e "${BLUE}━━━ Scala Code ━━━${NC}"
echo ""
echo -e "${GREEN}Application Code:${NC}"
printf "  %-20s %6d lines\n" "JVM:" "$JVM_APP_SCALA"
printf "  %-20s %6d lines\n" "Shared:" "$SHARED_APP_SCALA"
printf "  %-20s %6d lines\n" "JS:" "$JS_APP_SCALA"
printf "  ${BOLD}%-20s %6d lines${NC}\n" "Subtotal:" "$TOTAL_APP_SCALA"
echo ""
echo -e "${YELLOW}Test Code:${NC}"
printf "  %-20s %6d lines\n" "JVM:" "$JVM_TEST_SCALA"
printf "  %-20s %6d lines\n" "Shared:" "$SHARED_TEST_SCALA"
printf "  %-20s %6d lines\n" "JS:" "$JS_TEST_SCALA"
printf "  ${BOLD}%-20s %6d lines${NC}\n" "Subtotal:" "$TOTAL_TEST_SCALA"
echo ""
printf "${BOLD}Total Scala:         %6d lines${NC}\n" "$TOTAL_SCALA"
echo ""

# ============================================================================
# HTML FILES
# ============================================================================

HTML_LINES=$(count_lines "*.html" "jvm/src/main/resources")

echo -e "${BLUE}━━━ HTML Files ━━━${NC}"
printf "  %-20s %6d lines\n" "Static HTML:" "$HTML_LINES"
echo ""

# ============================================================================
# CSS FILES
# ============================================================================

CSS_LINES=$(count_lines "*.css" "jvm/src/main/resources")

echo -e "${BLUE}━━━ CSS Files ━━━${NC}"
printf "  %-20s %6d lines\n" "Stylesheets:" "$CSS_LINES"
echo ""

# ============================================================================
# BUILD FILES
# ============================================================================

BUILD_SBT=$(count_lines_in_files "build.sbt")
PLUGINS_SBT=$(count_lines_in_files "project/plugins.sbt")
BUILD_PROPS=$(count_lines_in_files "project/build.properties")
TOTAL_BUILD=$((BUILD_SBT + PLUGINS_SBT + BUILD_PROPS))

echo -e "${BLUE}━━━ Build Files (SBT) ━━━${NC}"
printf "  %-20s %6d lines\n" "build.sbt:" "$BUILD_SBT"
printf "  %-20s %6d lines\n" "plugins.sbt:" "$PLUGINS_SBT"
printf "  %-20s %6d lines\n" "build.properties:" "$BUILD_PROPS"
printf "  ${BOLD}%-20s %6d lines${NC}\n" "Subtotal:" "$TOTAL_BUILD"
echo ""

# ============================================================================
# SUMMARY
# ============================================================================

TOTAL_LOC=$((TOTAL_SCALA + HTML_LINES + CSS_LINES + TOTAL_BUILD))

echo -e "${BOLD}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║                        SUMMARY                             ║${NC}"
echo -e "${BOLD}╠════════════════════════════════════════════════════════════╣${NC}"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "Scala (Application):" "$TOTAL_APP_SCALA"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "Scala (Tests):" "$TOTAL_TEST_SCALA"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "HTML:" "$HTML_LINES"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "CSS:" "$CSS_LINES"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "Build (SBT):" "$TOTAL_BUILD"
echo -e "${BOLD}╠════════════════════════════════════════════════════════════╣${NC}"
printf "${BOLD}║  %-26s %10d lines               ║${NC}\n" "TOTAL:" "$TOTAL_LOC"
echo -e "${BOLD}╚════════════════════════════════════════════════════════════╝${NC}"

# ============================================================================
# JSON OUTPUT FOR CI
# ============================================================================

if [ "$1" == "--json" ]; then
    echo ""
    echo "JSON_OUTPUT:"
    cat << EOF
{
  "scala": {
    "application": {
      "jvm": $JVM_APP_SCALA,
      "shared": $SHARED_APP_SCALA,
      "js": $JS_APP_SCALA,
      "total": $TOTAL_APP_SCALA
    },
    "test": {
      "jvm": $JVM_TEST_SCALA,
      "shared": $SHARED_TEST_SCALA,
      "js": $JS_TEST_SCALA,
      "total": $TOTAL_TEST_SCALA
    },
    "total": $TOTAL_SCALA
  },
  "html": $HTML_LINES,
  "css": $CSS_LINES,
  "build": $TOTAL_BUILD,
  "total": $TOTAL_LOC
}
EOF
fi

# Export for GitHub Actions
if [ -n "$GITHUB_ENV" ]; then
    echo "LOC_TOTAL=$TOTAL_LOC" >> "$GITHUB_ENV"
    echo "LOC_SCALA_APP=$TOTAL_APP_SCALA" >> "$GITHUB_ENV"
    echo "LOC_SCALA_TEST=$TOTAL_TEST_SCALA" >> "$GITHUB_ENV"
    echo "LOC_HTML=$HTML_LINES" >> "$GITHUB_ENV"
    echo "LOC_CSS=$CSS_LINES" >> "$GITHUB_ENV"
    echo "LOC_BUILD=$TOTAL_BUILD" >> "$GITHUB_ENV"
fi

