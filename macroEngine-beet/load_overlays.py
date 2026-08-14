from beet import Context, DataPack


def beet_default(ctx: Context):
    """Loads each overlay folder into ctx.data.overlays[dir].

    The data_pack.overlays field in beet.json only writes format
    metadata to pack.mcmeta; actual file loading is done here.
    """
    overlay_pack = DataPack(path="src_1_20_5")
    ctx.data.overlays["1_20_5"].merge(overlay_pack)
