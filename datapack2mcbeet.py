#!/usr/bin/env python3
"""
datapack2mcbeet.py

runtoolkit/macroEngine reposundaki datapacks/macroEngine klasorunu indirir
ve beet (mcbeet.dev) projesi formatina cevirir.

Kaynak yapi:
    datapacks/macroEngine/
        pack.mcmeta          -> overlays.entries[0] = {"formats": [40,42], "directory": "1_20_5"}
        data/                -> ana pack (pack_format 40-107)
        1_20_5/data/         -> overlay (sadece format 40-42, yani 1.20.5)

Beet, overlay'leri beet.json icinde "data_pack.overlays" ile ayri bir
load kaynagi olarak tanimlar; ana pack_format/description bilgisini de
pack.mcmeta'dan aynen tasir.

Kullanim:
    python3 datapack2mcbeet.py [hedef_dizin]

Varsayilan hedef_dizin: ./macroEngine-beet
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
    """Repo tarball'ini indirir ve extract eder, kok klasoru dondurur."""
    tarball = tmp_dir / "repo.tar.gz"
    url = f"https://codeload.github.com/{REPO}/tar.gz/refs/heads/{BRANCH}"
    subprocess.run(
        ["curl", "-sL", url, "-o", str(tarball)],
        check=True,
    )
    if not tarball.exists() or tarball.stat().st_size < 1000:
        sys.exit(f"Indirme basarisiz oldu: {url}")

    with tarfile.open(tarball) as tf:
        tf.extractall(tmp_dir)

    extracted = next(tmp_dir.glob(f"{REPO.split('/')[1]}-*"))
    return extracted


def main() -> None:
    target = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("macroEngine-beet")
    if target.exists():
        sys.exit(f"Hedef dizin zaten var: {target}")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        print(f"[1/5] {REPO} indiriliyor...")
        repo_root = download_repo(tmp_dir)

        dp_root = repo_root / DATAPACK_SUBPATH
        if not dp_root.exists():
            sys.exit(f"Beklenen yol bulunamadi: {DATAPACK_SUBPATH}")

        pack_mcmeta_path = dp_root / "pack.mcmeta"
        pack_mcmeta = json.loads(pack_mcmeta_path.read_text(encoding="utf-8"))

        print("[2/5] Proje iskeleti olusturuluyor...")
        target.mkdir(parents=True)
        src_main = target / "src"
        src_main.mkdir()

        # Ana data/ -> src/data
        shutil.copytree(dp_root / "data", src_main / "data")

        # Overlay bilgisini pack.mcmeta'dan oku, her overlay klasoru icin
        # ayri bir src_<overlay_adi>/data olustur.
        # ONEMLI: beet.json'daki data_pack.overlays alani SADECE pack.mcmeta
        # icine format meta verisi yazar (bkz. beet/toolchain/project.py,
        # PackOverlayConfig). Overlay icerigini gercekten yuklemek icin
        # ctx.data.overlays[directory] uzerinden calisan bir plugin gerekir;
        # bu yuzden asagida load_overlays.py adinda bir plugin de uretiyoruz.
        overlays_cfg = {}
        overlay_entries = pack_mcmeta.get("overlays", {}).get("entries", [])
        for entry in overlay_entries:
            overlay_dir_name = entry["directory"]
            overlay_src_data = dp_root / overlay_dir_name / "data"
            if not overlay_src_data.exists():
                print(f"  [uyari] overlay kaynagi yok, atlaniyor: {overlay_dir_name}")
                continue

            print(f"[3/5] Overlay tasiniyor: {overlay_dir_name} "
                  f"(formats {entry.get('formats')})")
            # Kaynak (repo icindeki) klasor adiyla, beet projesindeki
            # source klasor adini ayri tutuyoruz: source_dir diskte nereden
            # okunacagini, overlay_dir_name ise pack.mcmeta'daki (ve
            # dolayisiyla oyunun bekledigi) overlay klasor adini gosterir.
            source_dir = f"src_{overlay_dir_name}"
            shutil.copytree(overlay_src_data, target / source_dir / "data")
            overlays_cfg[overlay_dir_name] = {
                "source_dir": source_dir,
                "formats": entry.get("formats"),
            }

        # description'i pack.mcmeta'dan aynen tasi (raw json text component).
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
            # data_pack.overlays: SADECE pack.mcmeta'ya formats/directory
            # meta bilgisini yazar (PackOverlayConfig -> yalnizca formats,
            # directory, min_format, max_format alanlarini kabul eder;
            # extra="forbid" oldugu icin "load" gibi baska bir alan
            # eklemek beet tarafindan reddedilir).
            beet_config["data_pack"]["overlays"] = [
                {
                    "directory": overlay_dir_name,
                    "formats": cfg["formats"],
                }
                for overlay_dir_name, cfg in overlays_cfg.items()
            ]
            # Gercek dosya yuklemesi icin plugin gerekiyor -> require listesi
            # require, Python dotted-path bekler (dosya yolu degil).
            # load_overlays.py -> "load_overlays" modulu, "load_overlays"
            # fonksiyonu (varsayilan olarak fonksiyon adiyla ayni).
            beet_config["require"] = ["load_overlays"]

        print("[4/5] beet.json yaziliyor...")
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
                '    """Her overlay klasorunu ctx.data.overlays[dir] icine yukler.',
                "",
                "    beet.json'daki data_pack.overlays alani sadece pack.mcmeta'ya",
                "    format meta verisi yazar; asil dosya yuklemesi burada yapiliyor.",
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
            print("  -> load_overlays.py plugin'i olusturuldu "
                  "(overlay icerigini yukler)")

        print("[5/5] Tamamlandi.")

    print(f"\nProje hazir: {target}/")
    print("Icerik:")
    for p in sorted(target.iterdir()):
        print(f"  {p.name}")
    print("\nBuild etmek icin:")
    print(f"  cd {target} && beet build")


if __name__ == "__main__":
    main()
