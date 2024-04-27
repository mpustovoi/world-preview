package caeruleusTait.world.preview.backend.worker;

import caeruleusTait.world.preview.backend.color.PreviewData;
import caeruleusTait.world.preview.backend.sampler.ChunkSampler;
import caeruleusTait.world.preview.backend.storage.PreviewStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class FastApproxHeightmapWorkUnit extends WorkUnit {
    private final ChunkSampler sampler;

    public FastApproxHeightmapWorkUnit(ChunkSampler sampler, ChunkPos pos, SampleUtils sampleUtils, PreviewData previewData) {
        super(sampleUtils, pos, previewData, 0);
        this.sampler = sampler;
    }

    @Override
    protected List<WorkResult> doWork() {
        WorkResult res = new WorkResult(this, QuartPos.fromBlock(0), primarySection, new ArrayList<>(16), List.of());
        for (BlockPos p : sampler.blocksForChunk(chunkPos, y)) {
            sampler.expandRaw(p, sampleUtils.doFastApproxHeight(p), res);
        }
        return List.of(res);
    }

    @Override
    public long flags() {
        return PreviewStorage.FLAG_FAST_HEIGHT;
    }
}
