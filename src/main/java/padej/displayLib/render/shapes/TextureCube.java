package padej.displayLib.render.shapes;

import padej.displayLib.utils.AlignmentType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.UUID;

public class TextureCube extends DefaultItem {

    private String textureUrl;

    public TextureCube(float scale, String textureUrl, AlignmentType alignmentType) {
        super(scale, createSkullItem(textureUrl), alignmentType);
        this.textureUrl = textureUrl;
    }

    private static ItemStack createSkullItem(String url) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = org.bukkit.Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            try {
                URL skinUrl = new URL(url);
                textures.setSkin(skinUrl);
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                skull.setItemMeta(meta);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return skull;
    }

    public String getTextureUrl() {
        return textureUrl;
    }

    public void setTextureUrl(String textureUrl) {
        this.textureUrl = textureUrl;
        this.setItemStack(createSkullItem(textureUrl));
        if (this.getItemDisplay() != null && !this.getItemDisplay().isDead()) {
            this.getItemDisplay().setItemStack(this.getItemStack());
        }
    }
}