#!/bin/bash

# setup-project-skills.sh - Install android-ui-validation skill to a project
#
# Creates symlinks from project's .claude/skills and .codex/skills
# to the skill in this toolkit.
#
# Usage:
#   ./setup-project-skills.sh /path/to/your/project

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLKIT_DIR="$(dirname "$SCRIPT_DIR")"
SKILL_NAME="android-ui-validation"
SKILL_SOURCE="${TOOLKIT_DIR}/agents/skills/${SKILL_NAME}"

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 /path/to/your/project"
    exit 1
fi

TARGET_PROJECT="$1"

if [[ ! -d "$TARGET_PROJECT" ]]; then
    echo "Error: Directory not found: $TARGET_PROJECT"
    exit 1
fi

if [[ ! -d "$SKILL_SOURCE" ]]; then
    echo "Error: Skill not found: $SKILL_SOURCE"
    exit 1
fi

echo "Installing ${SKILL_NAME} skill to: $TARGET_PROJECT"

# Create .claude/skills symlink
CLAUDE_SKILLS_DIR="${TARGET_PROJECT}/.claude/skills"
mkdir -p "$CLAUDE_SKILLS_DIR"

if [[ -L "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}" ]]; then
    rm "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}"
fi

ln -s "$SKILL_SOURCE" "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}"
echo "  Created: .claude/skills/${SKILL_NAME} -> $SKILL_SOURCE"

# Create .codex/skills symlink
CODEX_SKILLS_DIR="${TARGET_PROJECT}/.codex/skills"
mkdir -p "$CODEX_SKILLS_DIR"

if [[ -L "${CODEX_SKILLS_DIR}/${SKILL_NAME}" ]]; then
    rm "${CODEX_SKILLS_DIR}/${SKILL_NAME}"
fi

ln -s "$SKILL_SOURCE" "${CODEX_SKILLS_DIR}/${SKILL_NAME}"
echo "  Created: .codex/skills/${SKILL_NAME} -> $SKILL_SOURCE"

# Add to .gitignore if not already there
GITIGNORE="${TARGET_PROJECT}/.gitignore"
if [[ -f "$GITIGNORE" ]]; then
    if ! grep -q ".claude/skills/${SKILL_NAME}" "$GITIGNORE"; then
        echo "" >> "$GITIGNORE"
        echo "# AI skill symlinks" >> "$GITIGNORE"
        echo ".claude/skills/${SKILL_NAME}" >> "$GITIGNORE"
        echo ".codex/skills/${SKILL_NAME}" >> "$GITIGNORE"
        echo "  Updated .gitignore"
    fi
fi

echo
echo "Done! The ${SKILL_NAME} skill is now available in your project."
echo
echo "To use with Claude Code:"
echo "  cd $TARGET_PROJECT"
echo "  claude"
echo
echo "The skill will be automatically detected by Claude Code and Codex CLI."
