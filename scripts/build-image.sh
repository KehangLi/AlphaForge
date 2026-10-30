#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: build-image.sh [--tag IMAGE:TAG] [--platform PLATFORM] [--version VERSION] [--revision COMMIT]

Build the backend image and load it into the local Docker image store.

Options:
  -t, --tag       Image name and tag (default: alphaforge-backend:local)
      --platform linux/amd64 or linux/arm64 (default: Docker builder's native platform)
      --version  Release version recorded in image metadata (default: development)
      --revision Full Git commit recorded in image metadata (default: unknown)
  -h, --help      Show this help

Examples:
  ./scripts/build-image.sh
  ./scripts/build-image.sh --platform linux/amd64 --tag alphaforge-backend:0.1.0

Tests run separately with ./mvnw test. Supply database credentials when running
the container, not while building it. This script does not push to a registry.
USAGE
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

image_tag=alphaforge-backend:local
image_platform=
build_version=development
build_revision=unknown

while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--tag)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            [[ -n "$2" && "$2" != -* ]] || fail "$1 requires an image name and tag"
            image_tag=$2
            shift 2
            ;;
        --platform)
            [[ $# -ge 2 ]] || fail "$1 requires a value"
            case "$2" in
                linux/amd64|linux/arm64) image_platform=$2 ;;
                *) fail "--platform must be linux/amd64 or linux/arm64; build one platform at a time" ;;
            esac
            shift 2
            ;;
        --version)
            [[ $# -ge 2 && "$2" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] \
                || fail "--version must look like v0.2.0"
            build_version=$2
            shift 2
            ;;
        --revision)
            [[ $# -ge 2 && "$2" =~ ^[0-9a-f]{40}$ ]] || fail "--revision must be a full lowercase Git commit SHA"
            build_revision=$2
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *) fail "Unknown argument: $1 (use --help for usage)" ;;
    esac
done

command -v docker >/dev/null 2>&1 || fail "Docker is not installed or is not on PATH"
docker info >/dev/null 2>&1 || fail "Docker is unavailable; start Docker Desktop or check your Docker context"
docker buildx version >/dev/null 2>&1 || fail "Docker Buildx is required"

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/.." && pwd)
build_args=(buildx build --load --tag "$image_tag" --file "$repo_root/Dockerfile")
build_args+=(--build-arg "BUILD_VERSION=$build_version" --build-arg "BUILD_REVISION=$build_revision")
if [[ -n "$image_platform" ]]; then
    build_args+=(--platform "$image_platform")
fi

printf 'Building %s for %s\n' "$image_tag" "${image_platform:-the native builder platform}"
exec docker "${build_args[@]}" "$repo_root"
