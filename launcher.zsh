#!/bin/zsh -f
# 杭州 iPhone 定位 · macOS 本地启动文件
# App assets are embedded below. On first launch, uv and the fixed Python
# dependency set are downloaded from their official distribution channels.
set -eu
setopt PIPE_FAIL
umask 077

fail() {
  print -u2 -- "$1"
  exit 1
}

[[ "$(/usr/bin/uname -s)" == Darwin ]] || fail '请在 Mac 上打开这个文件。'
case "$(/usr/bin/uname -m)" in
  arm64)
    uv_arch=aarch64
    uv_sha=5aeca67f2283f75235de40d0bd9c0a5bbc5fce23b6d48b3bf9fa1b2db08382ca
    ;;
  x86_64)
    uv_arch=x86_64
    uv_sha=617a3211261b0fc739562f0071aa34e9739797c510208ede8e2977c30903d041
    ;;
  *) fail '此 Mac 的处理器架构无法运行 USB 定位组件。' ;;
esac

runtime_root="${HANGZHOU_LOCATION_RUNTIME:-$HOME/Library/Caches/Hangzhou-iPhone-Location}"
session_dir=$(/usr/bin/mktemp -d "${TMPDIR:-/tmp}/hangzhou-location.XXXXXXXX")
trap '/bin/rm -rf -- "$session_dir"' EXIT

/usr/bin/base64 -D <<'ARCHIVE' | /usr/bin/tar -xzf - -C "$session_dir" || fail '启动文件未能解包，请重新保存该文件。'
__ASSET_BUNDLE__
ARCHIVE

/bin/mkdir -p "$runtime_root"
uv_tool="$runtime_root/uv-0.13.9-$uv_arch"
runtime_python="$runtime_root/venv-3.13.18-pmd11.63.1"
export UV_CACHE_DIR="$runtime_root/cache"
export UV_PYTHON_INSTALL_DIR="$runtime_root/python"
export UV_NO_CONFIG=1
export UV_NO_PROGRESS=1
export HANGZHOU_LOCATION_SESSION_DIR="$session_dir"

if [[ ! -x "$uv_tool" ]]; then
  print -- '首次启动，正在下载 USB 定位组件所需的运行环境…'
  uv_asset="uv-$uv_arch-apple-darwin"
  /usr/bin/curl -fLsS --proto '=https' --proto-redir '=https' --connect-timeout 12 --max-time 150 --retry 3 --retry-all-errors \
    "https://github.com/astral-sh/uv/releases/download/0.13.9/$uv_asset.tar.gz" \
    -o "$session_dir/uv.tar.gz" || fail '下载运行环境失败，请检查此 Mac 的网络后重试。'
  uv_actual=$(/usr/bin/shasum -a 256 "$session_dir/uv.tar.gz")
  [[ "${uv_actual%% *}" == "$uv_sha" ]] || fail '运行环境的文件校验未通过，请重新打开工具。'
  /usr/bin/tar -xzf "$session_dir/uv.tar.gz" -C "$session_dir" "$uv_asset/uv"
  /bin/mv -f "$session_dir/$uv_asset/uv" "$uv_tool"
fi

if [[ ! -f "$runtime_python/.location-ready" || ! -x "$runtime_python/bin/python3" ]]; then
  print -- '正在安装本工具的定位组件。首次运行需要联网，请稍等…'
  "$uv_tool" venv --quiet --allow-existing --managed-python --python 3.13.18 "$runtime_python" \
    || fail 'Python 环境安装失败，请检查网络后重试。'
  "$uv_tool" pip sync --quiet --require-hashes --only-binary :all: \
    --default-index "https://pypi.org/simple" --python "$runtime_python/bin/python3" \
    "$session_dir/requirements.lock" || fail 'USB 定位组件安装失败，请检查网络后重试。'
  : > "$runtime_python/.location-ready"
fi

"$runtime_python/bin/python3" -B -I -c \
  'import runpy, sys; sys.path.insert(0, sys.argv.pop(1)); runpy.run_path(sys.argv.pop(1), run_name="__main__")' \
  "$session_dir" "$session_dir/server.py" "$@"
