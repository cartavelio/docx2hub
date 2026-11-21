#!/usr/bin/env bash
# Run one explicit command with the pinned Linux x64 JDK17, without editing profiles.
# Usage: with-jdk.sh [--] COMMAND [ARG ...]; no arguments prints the JDK home.
# JDK_CACHE_DIR overrides ~/.local/share/kun-stack/jdks for isolated verification.
# Other repository toolchains are resolved by the repository's Gradle resolver.
set -euo pipefail
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || {
    echo 'This bootstrap supports Linux x64; use a supported standard JDK manager elsewhere.' >&2
    exit 1
}
version='jdk-17.0.21.1+2'
sha='0b0ac5eab6db6abaec68c9913bcddb31321e5da411c45073d520d9c125e6f7b4'
url='https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21.1_2.tar.gz'
cache=${JDK_CACHE_DIR:-$HOME/.local/share/kun-stack/jdks}
mkdir -p "$cache"
cache=$(realpath "$cache")
jdk="$cache/$version"
exec 9>"$cache/.jdk17.lock"
flock 9
if [[ ! -e $jdk ]]; then
    stage=$(mktemp -d "$cache/.jdk17.XXXXXX")
    trap 'rm -rf -- "$stage"' EXIT
    echo "Obtaining checksum-pinned Temurin $version" >&2
    curl --fail --location --silent --show-error --retry 2 --max-time 300 "$url" -o "$stage/jdk.tar.gz"
    printf '%s  %s\n' "$sha" "$stage/jdk.tar.gz" | sha256sum --check >&2
    mkdir "$stage/unpack"
    tar --extract --gzip --file "$stage/jdk.tar.gz" --directory "$stage/unpack" --no-same-owner
    [[ -x $stage/unpack/$version/bin/java && -x $stage/unpack/$version/bin/javac ]] || {
        echo 'Downloaded archive lacks the pinned JDK layout.' >&2; exit 1;
    }
    grep -q '^JAVA_VERSION="17\.' "$stage/unpack/$version/release"
    printf '%s\n' "$sha" > "$stage/unpack/$version/.archive-sha256"
    mv "$stage/unpack/$version" "$jdk"
    rm -rf -- "$stage"
    trap - EXIT
fi
[[ -x $jdk/bin/java && -x $jdk/bin/javac && $(cat "$jdk/.archive-sha256") == "$sha" ]] || {
    echo 'Existing JDK cache is incomplete or has a different receipt; preserve it and investigate.' >&2
    exit 1
}
flock -u 9
exec 9>&-
[[ ${1:-} != -- ]] || shift
if [[ $# == 0 ]]; then
    printf '%s\n' "$jdk"
else
    JAVA_HOME="$jdk" PATH="$jdk/bin:$PATH" exec "$@"
fi
