package caeruleusTait.world.preview;

import java.util.ArrayList;
import java.util.List;

public class WorldPreviewConfig {

    public List<String> savedSeeds = new ArrayList<>();

    public boolean showInPauseMenu = true;
    public boolean showPlayer = true;
    public boolean showControls = true;
    public boolean showFrameTime = false;
    public boolean buildFullVertChunk = false;
    public boolean backgroundSampleVertChunk = false;
    public boolean sampleStructures = false;
    public boolean sampleHeightmap = false;
    public boolean sampleFastHeightmap = true;
    public boolean sampleIntersections = false;
    public int heightmapMinY = 32;
    public int heightmapMaxY = 255;
    public boolean onlySampleInVisualRange = true;
    public boolean cacheInGame = true;
    public boolean cacheInNew = false;
    public boolean enableCompression = true;
    public String colorMap = "world_preview:inferno";

    private int numThreads = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

    public int numThreads() {
        setNumThreads(numThreads);
        return numThreads;
    }

    public void setNumThreads(int numThreads) {
        numThreads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), numThreads));
        this.numThreads = numThreads;
    }
}
