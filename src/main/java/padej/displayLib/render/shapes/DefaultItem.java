package padej.displayLib.render.shapes;

import padej.displayLib.utils.AlignmentType;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public abstract class DefaultItem extends DefaultDisplay {
    private float scale;
    private ItemStack itemStack;
    private ItemDisplay itemDisplay;

    private AlignmentType alignmentType;

    public DefaultItem(float scale, ItemStack itemStack, AlignmentType alignmentType) {
        this.scale = scale;
        this.itemStack = itemStack;
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

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack item) {
        this.itemStack = item;
    }

    public ItemDisplay getItemDisplay() {
        return itemDisplay;
    }

    public Transformation getTransformation() {
        return itemDisplay != null ? itemDisplay.getTransformation() : emptyTransformation;
    }

    public Location getLocation() {
        return itemDisplay != null ? itemDisplay.getLocation() : null;
    }

    public ItemDisplay spawn(Location spawnLocation) {
        if (this.itemDisplay != null && !this.itemDisplay.isDead()) {
            this.itemDisplay.remove();
            this.itemDisplay = null;
            return null;
        } else {
            this.itemDisplay = (ItemDisplay) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ITEM_DISPLAY);
            this.itemDisplay.setItemStack(this.getItemStack());
            this.itemDisplay.setRotation(0.0F, 0.0F);

            Vector3f offset = getOffset(this.alignmentType, this.getScale() * 2);
            Transformation transformation = new Transformation(
                    offset,
                    new AxisAngle4f(),
                    new Vector3f(this.scale, this.scale, this.scale),
                    new AxisAngle4f()
            );

            this.itemDisplay.setTransformation(transformation);
            this.itemDisplay.setInterpolationDuration(1);
            this.itemDisplay.setTeleportDuration(1);

            this.display = this.itemDisplay;

            return this.itemDisplay;
        }
    }
}