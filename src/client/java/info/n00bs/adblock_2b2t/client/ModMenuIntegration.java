package info.n00bs.adblock_2b2t.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import info.n00bs.adblock_2b2t.client.config.FilterConfig;
import info.n00bs.adblock_2b2t.client.filter.MessageFilter;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    private final FilterConfig def = new FilterConfig();
    private final FilterConfig cur = FilterConfig.getInstance();

    private Text text(String text) {
        return Text.literal(text);
    }

    private OptionDescription textOptDesc(String text) {
        return OptionDescription.of(Text.literal(text));
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // https://docs.isxander.dev/yet-another-config-lib/gui-builder/
        return parentScreen -> YetAnotherConfigLib.createBuilder()
                .title(text("adblock_2b2t"))
                .category(ConfigCategory.createBuilder()
                        .name(text("adblock_2b2t"))
                        .option(Option.<Boolean>createBuilder()
                                .name(text("Mod Enabled"))
                                .description(textOptDesc("Whether ads should be blocked and remote lists be periodically pulled (if enabled)"))
                                .controller(TickBoxControllerBuilder::create)
                                .binding(def.isEnabled(), cur::isEnabled, cur::setEnabled)
                                .build()
                        )
                        .option(Option.<Boolean>createBuilder()
                                .name(text("Debug Mode"))
                                .description(textOptDesc("Debug mode displays current blocked messages among other things"))
                                .controller(TickBoxControllerBuilder::create)
                                .binding(def.isDebugMode(), cur::isDebugMode, cur::setDebugMode)
                                .build()
                        )
                        .group(OptionGroup.createBuilder()
                                .name(text("Remote Filters"))
                                .description(textOptDesc("Remote filters are fetched from an online resource and can auto-update in the background for your convenience"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(text("Use"))
                                        .description(textOptDesc("Whether to use specified remote filter"))
                                        .controller(TickBoxControllerBuilder::create)
                                        .binding(def.isUseRemoteFilters(), cur::isUseRemoteFilters, cur::setUseRemoteFilters)
                                        .build()
                                )
                                .option(Option.<String>createBuilder()
                                        .name(text("URL"))
                                        .description(textOptDesc("URL of the remote filter"))
                                        .controller(StringControllerBuilder::create)
                                        .binding(def.getRemoteUrl(), cur::getRemoteUrl, cur::setRemoteUrl)
                                        .build()
                                )
                                .option(Option.<Boolean>createBuilder()
                                        .name(text("Auto Refresh Enabled"))
                                        .description(textOptDesc("Whether remote filters should be pulled periodically"))
                                        .controller(TickBoxControllerBuilder::create)
                                        .binding(def.isAutoRefreshEnabled(), cur::isAutoRefreshEnabled, (val) -> {
                                            cur.setAutoRefreshEnabled(val);
                                            MessageFilter.getInstance().startAutoRefreshIfEnabled();
                                        })
                                        .build()
                                )
                                .option(Option.<Integer>createBuilder()
                                        .name(text("Auto Refresh Period"))
                                        .description(textOptDesc("The delay between pulling new remote filters (in minutes, at least 1)"))
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                                .range(1, 120)
                                                .step(1)
                                                .formatValue((value) -> value == 1 ? text("1 minute") : Text.translatable("%s minutes", value))
                                        )
                                        .binding(def.getAutoRefreshDelay(), cur::getAutoRefreshDelay, cur::setAutoRefreshDelay)
                                        .build()
                                )
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(text("Custom Filters"))
                                .description(textOptDesc("Remote filters allow you to setup filters in a local file. Useful for testing or personal preference."))
                                .option(Option.<Boolean>createBuilder()
                                        .name(text("Use"))
                                        .description(textOptDesc(
                                                """
                                                Whether to use a custom, local filter file.
                                                
                                                Location: .minecraft/config/adblock_2b2t/filters/custom.txt
                                                """
                                        ))
                                        .controller(TickBoxControllerBuilder::create)
                                        .binding(def.isUseCustomFilters(), cur::isUseCustomFilters, cur::setUseCustomFilters)
                                        .build()
                                )
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(text("Actions"))
                                .option(ButtonOption.createBuilder()
                                        .name(text("Refresh filters now"))
                                        .description(textOptDesc("Fetches remote and custom filters (if enabled) and applies them"))
                                        .action((_screen, _opt) -> MessageFilter.getInstance().refreshFilters())
                                        .build()
                                )
                                .build())
                        .build()
                )
                //.save(() -> {}) // Nothing to do, auto-saves
                .build()
                .generateScreen(parentScreen);
    }

}
