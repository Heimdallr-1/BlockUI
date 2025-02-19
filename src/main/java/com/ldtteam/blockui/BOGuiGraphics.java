package com.ldtteam.blockui;

import com.ldtteam.blockui.mod.item.BlockStateRenderingData;
import com.ldtteam.blockui.util.SingleBlockGetter.SingleBlockNeighborhood;
import com.ldtteam.blockui.util.cursor.Cursor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;

public class BOGuiGraphics extends GuiGraphics
{
    // Static instance should be fine since gui rendering is on single thread
    private static final SingleBlockNeighborhood NEIGHBORHOOD = new SingleBlockNeighborhood();

    private int cursorMaxDepth = -1;
    private Cursor selectedCursor = Cursor.DEFAULT;

    public BOGuiGraphics(final Minecraft mc, final PoseStack ps, final BufferSource buffers)
    {
        super(mc, ps, buffers);
    }

    private Font getFont(@Nullable final ItemStack itemStack)
    {
        if (itemStack != null)
        {
            final Font font = IClientItemExtensions.of(itemStack).getFont(itemStack, IClientItemExtensions.FontContext.ITEM_COUNT);
            if (font != null)
            {
                return font;
            }
        }
        return minecraft.font;
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y)
    {
        super.renderItemDecorations(getFont(itemStack), itemStack, x, y);
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y, @Nullable final String altStackSize)
    {
        super.renderItemDecorations(getFont(itemStack), itemStack, x, y, altStackSize);
    }

    public int drawString(final String text, final float x, final float y, final int color)
    {
        return drawString(text, x, y, color, false);
    }

    public int drawString(final String text, final float x, final float y, final int color, final boolean shadow)
    {
        return super.drawString(minecraft.font, text, x, y, color, shadow);
    }

    public void setCursor(final Cursor cursor)
    {
        if (pose().poseStack.size() >= cursorMaxDepth)
        {
            cursorMaxDepth = pose().poseStack.size();
            selectedCursor = cursor;
        }
    }

    /**
     * @param debugXoffset debug string x offset
     */
    public void applyCursor(final int debugXoffset)
    {
        selectedCursor.apply();

        if (Pane.debugging)
        {
            drawString(selectedCursor.toString(), debugXoffset, -minecraft.font.lineHeight, Color.getByName("white"));
        }
    }

    /**
     * Render given blockState with model just like {@link #renderItem(ItemStack, int, int)}
     *
     * @param data      blockState rendering data
     * @param itemStack backing itemStack for given blockState
     */
    public void renderBlockStateAsItem(final BlockStateRenderingData data, final ItemStack itemStack)
    {
        BakedModel itemModel = minecraft.getItemRenderer().getModel(itemStack, null, null, 0);
        if (!itemModel.isGui3d() || data.blockState().getRenderShape() == RenderShape.INVISIBLE)
        {
            // well, some items are bit dumb
            itemModel = minecraft.getItemRenderer().getModel(new ItemStack(Blocks.STONE), null, null, 0);
        }

        // prepare pose just like itemStack rendering would do

        pose().pushPose();
        pose().last().normal().identity(); // reset normals cuz lighting
        pose().translate(8, 8, 150);
        pose().scale(16.0F, -16.0F, 16.0F);
        ClientHooks.handleCameraTransforms(pose(), itemModel, ItemDisplayContext.GUI, false);

        if (data.modelNeedsRotationFix())
        {
            final Matrix3f oldNormal = pose().last().normal();
            pose().pushPose();
            pose().rotateAround(Axis.YP.rotationDegrees(45), 0.0f, 0.5f, 0.0f);
            pose().last().normal().set(oldNormal.rotate(Axis.YP.rotationDegrees(-45)));
        }

        pose().translate(-0.5F, -0.5F, -0.5F);

        // render block and BE

        final int light = LightTexture.pack(15, 15);
        minecraft.getBlockRenderer()
            .renderSingleBlock(data.blockState(), pose(), bufferSource(), light, OverlayTexture.NO_OVERLAY, data.modelData(), null);
        if (data.blockEntity() != null)
        {
            try
            {
                minecraft.getBlockEntityRenderDispatcher()
                    .getRenderer(data.blockEntity())
                    .render(data.blockEntity(), 0, pose(), bufferSource(), light, OverlayTexture.NO_OVERLAY);
            }
            catch (final Exception e)
            {
                // well, noop then
            }
        }
        flush();

        if (data.modelNeedsRotationFix())
        {
            pose().popPose();
            pose().translate(-0.5F, -0.5F, -0.5F);
        }

        // render fluid

        final FluidState fluidState = data.blockState().getFluidState();
        if (!fluidState.isEmpty())
        {
            final RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
            pushMvApplyPose();

            NEIGHBORHOOD.blockState = data.blockState();
            minecraft.getBlockRenderer()
                .renderLiquid(BlockPos.ZERO, NEIGHBORHOOD, bufferSource().getBuffer(renderType), data.blockState(), fluidState);

            bufferSource().endBatch(renderType);
            popMvPose();
        }

        pose().popPose();
    }

    public void pushMvApplyPose()
    {
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().mul(pose().last().pose());
        RenderSystem.applyModelViewMatrix();
    }

    public void popMvPose()
    {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static double getAltSpeedFactor()
    {
        return Screen.hasAltDown() ? 5 : 1;
    }
}
