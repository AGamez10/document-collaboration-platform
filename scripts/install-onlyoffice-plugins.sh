#!/usr/bin/env bash
#
# Installs the OnlyOffice editor plugins this project mounts into Document Server.
#
# The onlyoffice/documentserver image ships NO plugins, even though its
# plugin-list-default.json declares them. They used to be installed by hand into the
# running container, which meant they disappeared the first time it was recreated —
# including the Macros plugin the View tab needs. docker-compose.yml bind-mounts each
# plugin from onlyoffice-plugins/, so this script fills that directory.
#
# Plugins are NOT committed: 28 MB of third-party code does not belong in the repo.
# Run this once after cloning, then `docker compose up -d`.
#
# Usage:
#   ./scripts/install-onlyoffice-plugins.sh          # skips plugins already present
#   ./scripts/install-onlyoffice-plugins.sh --force  # re-clones everything
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="$REPO_ROOT/onlyoffice-plugins"
FORCE=false
[[ "${1:-}" == "--force" ]] && FORCE=true

# repository name : GUID directory the editor looks for.
# The GUIDs are not cosmetic: Document Server resolves plugins by the exact GUID in
# their config.json, and app.js hardcodes the Macros one to place its toolbar button.
PLUGINS=(
  "plugin-macros:{E6978D28-0441-4BD7-8346-82FAD68BCA3B}"
  "plugin-highlightcode:{BE5CBF95-C0AD-4842-B157-AC40FEDD9841}"
  "plugin-photoeditor:{07FD8DFA-DFE0-4089-AL24-0730933CC80A}"
  "plugin-youtube:{38E022EA-AD92-45FC-B22B-49DF39746DB4}"
  "plugin-ocr:{440EBF13-9B19-4BD8-8621-05200E58140B}"
  "plugin-translator:{7327FC95-16DA-41D9-9AF2-0E7F449F6800}"
  "plugin-mendeley:{BE5CBF95-C0AD-4842-B157-AC40FEDD9441}"
  "plugin-thesaurus:{BE5CBF95-C0AD-4842-B157-AC40FEDD9840}"
  "plugin-speech:{D71C2EF0-F15B-47C7-80E9-86D671F9C595}"
  "plugin-drawio:{DB38923B-A8C0-4DE9-8AEE-A61BB5C901A5}"
  "plugin-zotero:{BFC5D5C6-89DE-4168-9565-ABD8D1E48711}"
)

echo ""
echo "  Office Platform - OnlyOffice plugins"
echo "  Destino: $TARGET_DIR"
echo ""

command -v git >/dev/null 2>&1 || { echo "  [ERROR] git no está disponible en el PATH."; exit 1; }
mkdir -p "$TARGET_DIR"

installed=0
skipped=0
failed=0

for entry in "${PLUGINS[@]}"; do
  repo="${entry%%:*}"
  guid="${entry#*:}"
  dest="$TARGET_DIR/$guid"

  if [[ -f "$dest/config.json" && "$FORCE" == false ]]; then
    printf "  [--] %-22s ya instalado\n" "$repo"
    skipped=$((skipped + 1))
    continue
  fi

  tmp="$(mktemp -d)"
  if git clone --depth 1 -q "https://github.com/ONLYOFFICE/$repo.git" "$tmp/src" 2>/dev/null; then
    # Verified rather than trusted: a plugin whose GUID does not match the directory it
    # is mounted into is silently ignored by the editor, which is very hard to diagnose.
    if grep -q "$guid" "$tmp/src/config.json" 2>/dev/null; then
      rm -rf "$tmp/src/.git" "$dest"
      mv "$tmp/src" "$dest"
      printf "  [OK] %-22s %s\n" "$repo" "$guid"
      installed=$((installed + 1))
    else
      printf "  [!!] %-22s el GUID de su config.json no coincide; se omite\n" "$repo"
      failed=$((failed + 1))
    fi
  else
    printf "  [!!] %-22s no se pudo clonar\n" "$repo"
    failed=$((failed + 1))
  fi
  rm -rf "$tmp"
done

echo ""
echo "  Instalados: $installed   Ya presentes: $skipped   Fallidos: $failed"
echo ""

if [[ $failed -gt 0 ]]; then
  echo "  Algunos plugins no se instalaron. El editor funciona igual, pero esos"
  echo "  botones no aparecerán en la pestaña Extensiones."
  echo ""
fi

echo "  Siguiente paso:  docker compose up -d onlyoffice"
echo "  El botón Macros aparece en la pestaña VISTA al abrir un documento en edición."
echo ""
