package padej.displayLib.render.shapes;

import padej.displayLib.utils.AlignmentType;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public abstract class DefaultCube extends DefaultDisplay {
    private float scale;
    private BlockData block;
    private BlockDisplay blockDisplay;

    private AlignmentType alignmentType;

    public DefaultCube(float scale, BlockData block, AlignmentType alignmentType) {
        this.scale = scale;
        this.block = block;
        this.alignmentType = alignmentType;
    }

    public AlignmentType getAlignmentType() {
        return alignmentType;
    }

    public void setAlignmentType(AlignmentType alignmentType) {
        this.alignmentType = alignmentType;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public BlockData getBlock() {
        return block;
    }

    public void setBlock(BlockData block) {
        this.block = block;
    }

    public BlockDisplay getBlockDisplay() {
        return blockDisplay;
    }

    public Transformation getTransformation() {
        return blockDisplay != null ? blockDisplay.getTransformation() : emptyTransformation;
    }

    public Location getLocation() {
        return blockDisplay != null ? blockDisplay.getLocation() : null;
    }

    public BlockDisplay spawn(Location spawnLocation) {
        if (this.blockDisplay != null && !this.blockDisplay.isDead()) {
            this.blockDisplay.remove();
            this.blockDisplay = null;
            return null;
        } else {
            this.blockDisplay = (BlockDisplay)spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.BLOCK_DISPLAY);
            this.blockDisplay.setBlock(this.getBlock());
            this.blockDisplay.setRotation(0.0F, 0.0F);
            Vector3f offset = getOffset(this.alignmentType, this.getScale());
            this.blockDisplay.setTransformation(new Transformation(offset, new AxisAngle4f(), new Vector3f(this.getScale(), this.getScale(), this.getScale()), new AxisAngle4f()));
            this.blockDisplay.setInterpolationDuration(1);
            this.blockDisplay.setTeleportDuration(1);

            this.display = this.blockDisplay;

            return this.blockDisplay;
        }
    }
}