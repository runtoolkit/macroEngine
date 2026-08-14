#!/usr/bin/env python3
"""
datapack2mcbeet.py

Downloads the datapacks/macroEngine folder from the runtoolkit/macroEngine repository
and converts it to the beet (mcbeet.dev) project format.

Source structure:
    datapacks/macroEngine/
        pack.mcmeta         -> overlays.entries[0] = {"formats": [40,42], "directory": "1_20_5"}
        data/               -> main pack (pack_format 40-107)
        1_20_5/data/        -> overlay (only format 40-42, i.e., 1.20.5)

Beet defines overlays in beet.json as a separate load source using
"data_pack.overlays"; it carries over the main pack_format/description info
directly from pack.mcmeta.

Usage:
    python3 datapack2mcbeet.py [target_directory]

Default target_directory: ./macroEngine-beet
"""

import json
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

REPO = "runtoolkit/macroEngine"
BRANCH = "main"
DATAPACK_SUBPATH = "datapacks/macroEngine"


def download_repo(tmp_dir: Path) -> Path:
    """Downloads and extracts the repo tarball, returns the root directory."""
    tarball = tmp_dir / "repo.tar.gz"
    url = f"https://codeload.github.com/{REPO}/tar.gz/refs/heads/{BRANCH}"
    subprocess.run(
        ["curl", "-sL", url, "-o", str(tarball)],
        check=True,
    )
    if not tarball.exists() or tarball.stat().st_size < 1000:
        sys.exit(f"Download failed: {url}")

    with tarfile.open(tarball) as tf:
        tf.extractall(tmp_dir)

    extracted = next(tmp_dir.glob(f"{REPO.split('/')[1]}-*"))
    return extracted


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("macroEngine-beet")
    if target.exists():
        sys.exit(f"Target directory already exists: {target}")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        print(f"[1/5] Downloading {REPO}...")
        repo_root = download_repo(tmp_dir)

        dp_root = repo_root / DATAPACK_SUBPATH
        if not dp_root.exists():
            sys.exit(f"Expected path not found: {DATAPACK_SUBPATH}")

        pack_mcmeta_path = dp_root / "pack.mcmeta"
        pack_mcmeta = json.loads(pack_mcmeta_path.read_text(encoding="utf-8"))

        print("[2/5] Creating project structure...")
        target.mkdir(parents=True)
        src_main = target / "src"
        src_main.mkdir()

        # Main data/ -> src/data
        shutil.copytree(dp_root / "data", src_main / "data")

        # Read overlay info from pack.mcmeta, create a separate
        # src_<overlay_name>/data directory for each overlay folder.
        # IMPORTANT: The data_pack.overlays field in beet.json ONLY writes
        # format metadata to pack.mcmeta (see beet/toolchain/project.py,
        # PackOverlayConfig). To actually load overlay content, a plugin
        # working via ctx.data.overlays[directory] is required; therefore,
        # we also generate a plugin named load_overlays.py below.
        overlays_cfg = {}
        overlay_entries = pack_mcmeta.get("overlays", {}).get("entries", [])
        for entry in overlay_entries:
            overlay_dir_name = entry["directory"]
            overlay_src_data = dp_root / overlay_dir_name / "data"
            if not overlay_src_data.exists():
                print(f"  [warning] overlay source does not exist, skipping: {overlay_dir_name}")
                continue

            print(f"[3/5] Moving overlay: {overlay_dir_name} "
                  f"(formats {entry.get('formats')})")
            # Keep the source directory name (in the repo) and the source directory
            # name in the beet project separate: source_dir indicates where it will be
            # read from disk, while overlay_dir_name shows the overlay folder name
            # in pack.mcmeta (and thus expected by the game).
            source_dir = f"src_{overlay_dir_name}"
            shutil.copytree(overlay_src_data, target / source_dir / "data")
            overlays_cfg[overlay_dir_name] = {
                "source_dir": source_dir,
                "formats": entry.get("formats"),
            }

        # Carry over description directly from pack.mcmeta (raw json text component).
        description = pack_mcmeta["pack"]["description"]
        pack_format = pack_mcmeta["pack"]["pack_format"]
        supported_formats = pack_mcmeta["pack"].get("supported_formats")

        beet_config = {
            "name": "macroEngine",
            "description": description,
            "data_pack": {
                "load": ["src"],
                "pack_format": pack_format,
            },
            "output": "build",
        }
        if supported_formats:
            beet_config["data_pack"]["supported_formats"] = supported_formats

        if overlays_cfg:
            # data_pack.overlays: ONLY writes formats/directory meta info to
            # pack.mcmeta (PackOverlayConfig -> accepts only formats, directory,
            # min_format, max_format fields; since extra="forbid", adding another
            # field like "load" will be rejected by beet).
            beet_config["data_pack"]["overlays"] = [
                {
                    "directory": overlay_dir_name,
                    "formats": cfg["formats"],
                }
                for overlay_dir_name, cfg in overlays_cfg.items()
            ]
            # Plugin required for actual file loading -> require list
            # require expects a Python dotted-path (not a file path).
            # load_overlays.py -> "load_overlays" module, "load_overlays"
            # function (defaults to matching the function name).
            beet_config["require"] = ["load_overlays"]

        print("[4/5] Writing beet.json...")
        (target / "beet.json").write_text(
            json.dumps(beet_config, indent=4, ensure_ascii=False),
            encoding="utf-8",
        )

        if overlays_cfg:
            plugin_lines = [
                "from beet import Context, DataPack",
                "",
                "",
                "def beet_default(ctx: Context):",
                '    """Loads each overlay folder into ctx.data.overlays[dir].',
                "",
                "    The data_pack.overlays field in beet.json only writes format",
                "    metadata to pack.mcmeta; actual file loading is done here.",
                '    """',
            ]
            for overlay_dir_name, cfg in overlays_cfg.items():
                src_dir = cfg["source_dir"]
                plugin_lines.append(
                    f'    overlay_pack = DataPack(path="{src_dir}")'
                )
                plugin_lines.append(
                    f'    ctx.data.overlays["{overlay_dir_name}"].merge(overlay_pack)'
                )
            plugin_lines.append("")

            (target / "load_overlays.py").write_text(
                "\n".join(plugin_lines), encoding="utf-8"
            )
            print("  -> Created load_overlays.py plugin "
                  "(loads overlay contents)")

        print("[5/5] Done.")

    print(f"\nProject ready: {target}/")
    print("Contents:")
    for p in sorted(target.iterdir()):
        print(f"  {p.name}")
    print("\nTo build:")
    print(f"  cd {target} && beet build")


if __name__ == "__main__":
    main()