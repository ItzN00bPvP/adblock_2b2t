package info.n00bs.adblock_2b2t.mixin.client;

import info.n00bs.adblock_2b2t.client.config.FilterConfig;
import info.n00bs.adblock_2b2t.client.filter.MessageFilter;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept and filter chat messages.
 */
@Mixin(ChatHud.class)
public class ChatMessageMixin {

    /**
     * Injects into the addMessage method to filter chat messages.
     * 
     * @param message The chat message text
     * @param signature The message signature data
     * @param indicator The message indicator
     * @param ci The callback info
     */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", 
            at = @At("HEAD"), 
            cancellable = true)
    private void onAddMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        // Convert the Text to a string
        String messageString = message.getString();

        // Check if the message should be filtered
        if (MessageFilter.getInstance().shouldFilterMessage(messageString)) {
            // Get the filter configuration
            FilterConfig config = FilterConfig.getInstance();

            // If debug mode is enabled, show a notification with the blocked message on hover
            if (config.isDebugMode()) {
                // Get the filter type that matched this message
                String filterType = MessageFilter.getInstance().getMatchingFilterType(messageString);
                String filterName = filterType != null ? 
                    (filterType.equals("CUSTOM") ? "Custom Filter" : "Remote Filter") : 
                    "Unknown Filter";

                // Create a hover event with the original message
                HoverEvent hoverEvent = new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT, 
                    Text.literal("Filter: " + filterName + "\n").formatted(Formatting.GOLD)
                        .append(Text.literal("expression: " + MessageFilter.getInstance().getMatchingPattern(messageString) + "\n").formatted(Formatting.GOLD))
                        .append(Text.literal("Blocked message: ").formatted(Formatting.RED))
                        .append(Text.literal(messageString).formatted(Formatting.WHITE))
                );

                // Create the debug message with hover effect
                Text debugMessage = Text.literal("[AdBlock] ").formatted(Formatting.DARK_RED)
                    .append(Text.literal("Message blocked").formatted(Formatting.RED))
                    .setStyle(Style.EMPTY.withHoverEvent(hoverEvent));

                // Replace the original message with our debug message
                ((ChatHud)(Object)this).addMessage(debugMessage, null, null);
            }
            System.out.println("[AdBlock] Message blocked: \n" +
                    messageString + "\n" +
                    "Filter: " + MessageFilter.getInstance().getMatchingFilterType(messageString) + "\n" +
                    "Pattern: " + MessageFilter.getInstance().getMatchingPattern(messageString) );

            // Cancel the event to prevent the original message from being displayed
            ci.cancel();
        }
    }
}