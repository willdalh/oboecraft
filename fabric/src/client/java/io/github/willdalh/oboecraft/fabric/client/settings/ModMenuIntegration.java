package io.github.willdalh.oboecraft.fabric.client.settings;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu's entrypoint: it opens the settings screen from the mod list. Mod Menu is optional, and Fabric only loads
 * this class when it is installed.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<SettingsScreen> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
