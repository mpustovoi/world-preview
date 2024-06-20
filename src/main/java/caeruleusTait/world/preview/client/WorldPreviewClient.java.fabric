package caeruleusTait.world.preview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

public class WorldPreviewClient implements ClientModInitializer {
    public static ShaderInstance HSV_SHADER;

    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.

        CoreShaderRegistrationCallback.EVENT.register(this::registerShaders);
    }

    private void registerShaders(CoreShaderRegistrationCallback.RegistrationContext context) {
        try {
            context.register(ResourceLocation.parse("world_preview:hsv"), DefaultVertexFormat.POSITION_COLOR, x -> HSV_SHADER = x);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void renderTexture(AbstractTexture texture, double xMin, double yMin, double xMax, double yMax) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        bufferBuilder.addVertex((float) xMin, (float) yMax, 0.0F).setUv(0.0F, 1.0F);
        bufferBuilder.addVertex((float) xMax, (float) yMax, 0.0F).setUv(1.0F, 1.0F);
        bufferBuilder.addVertex((float) xMax, (float) yMin, 0.0F).setUv(1.0F, 0.0F);
        bufferBuilder.addVertex((float) xMin, (float) yMin, 0.0F).setUv(0.0F, 0.0F);
        try(MeshData data = bufferBuilder.buildOrThrow()) {
            BufferUploader.drawWithShader(data);
        }
    }

    public static String toTitleCase(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        return Arrays
                .stream(input.split(" "))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
    }
}