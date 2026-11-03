package dev.chatchronicle.platform;

import dev.chatchronicle.client.ChatChronicle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = "chatchronicle", dist = Dist.CLIENT)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint() {
        ChatChronicle.initialize(FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
    }
}
