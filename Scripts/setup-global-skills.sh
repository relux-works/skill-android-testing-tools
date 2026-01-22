#!/bin/bash

# setup-global-skills.sh - Install android-ui-validation skill globally
#
# Creates symlinks in ~/.claude/skills and ~/.codex/skills
# so the skill is available in all projects.
#
# Usage:
#   ./setup-global-skills.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOLKIT_DIR="$(dirname "$SCRIPT_DIR")"
SKILL_NAME="android-ui-validation"
SKILL_SOURCE="${TOOLKIT_DIR}/agents/skills/${SKILL_NAME}"

if [[ ! -d "$SKILL_SOURCE" ]]; then
    echo "Error: Skill not found: $SKILL_SOURCE"
    exit 1
fi

echo "Installing ${SKILL_NAME} skill globally..."

# Create ~/.claude/skills symlink
CLAUDE_SKILLS_DIR="${HOME}/.claude/skills"
mkdir -p "$CLAUDE_SKILLS_DIR"

if [[ -L "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}" ]]; then
    rm "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}"
fi

ln -s "$SKILL_SOURCE" "${CLAUDE_SKILLS_DIR}/${SKILL_NAME}"
echo "  Created: ~/.claude/skills/${SKILL_NAME} -> $SKILL_SOURCE"

# Create ~/.codex/skills symlink
CODEX_SKILLS_DIR="${HOME}/.codex/skills"
mkdir -p "$CODEX_SKILLS_DIR"

if [[ -L "${CODEX_SKILLS_DIR}/${SKILL_NAME}" ]]; then
    rm "${CODEX_SKILLS_DIR}/${SKILL_NAME}"
fi

ln -s "$SKILL_SOURCE" "${CODEX_SKILLS_DIR}/${SKILL_NAME}"
echo "  Created: ~/.codex/skills/${SKILL_NAME} -> $SKILL_SOURCE"

echo
echo "Done! The ${SKILL_NAME} skill is now available globally."
echo
echo "The skill will be automatically detected by Claude Code and Codex CLI"
echo "in any project directory."
