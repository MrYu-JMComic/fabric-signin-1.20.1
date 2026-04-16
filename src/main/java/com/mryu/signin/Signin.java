package com.mryu.signin;

import com.mryu.signin.command.SigninCommand;
import com.mryu.signin.data.SigninPersistentState;
import com.mryu.signin.network.SigninNetworking;
import com.mryu.signin.service.SigninService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Signin implements ModInitializer {
	public static final String MOD_ID = "signin";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			SigninCommand.register(dispatcher)
		);
		SigninNetworking.registerServerReceivers();
		registerJoinReminder();

		LOGGER.info("Signin mod initialized.");
	}

	private static void registerJoinReminder() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			long today = SigninService.todayEpochDay();
			boolean signedToday = SigninPersistentState.get(server).hasSigned(player.getUuid(), today);
			if (signedToday) {
				return;
			}

			MutableText reminder = Text.literal(player.getName().getString()).formatted(Formatting.AQUA)
				.append(Text.translatable("message.signin.join.not_signed_suffix").formatted(Formatting.GOLD))
				.append(Text.literal("\n"))
				.append(buildJoinActionLink(
					"message.signin.join.click_sign_link",
					"message.signin.join.click_sign_hover",
					"/signin",
					Formatting.GREEN
				))
				.append(Text.literal("  "))
				.append(Text.literal("•").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("  "))
				.append(buildJoinActionLink(
					"message.signin.join.click_gui_link",
					"message.signin.join.click_gui_hover",
					"/signin gui",
					Formatting.AQUA
				))
				.append(Text.literal("  "))
				.append(Text.literal("•").formatted(Formatting.DARK_GRAY))
				.append(Text.literal("  "))
				.append(buildJoinActionLink(
					"message.signin.join.click_makeup_link",
					"message.signin.join.click_makeup_hover",
					"/signin makeup",
					Formatting.LIGHT_PURPLE
				));
			player.sendMessage(reminder, false);
		});
	}

	private static MutableText buildJoinActionLink(String key, String hoverKey, String command, Formatting color) {
		return Text.translatable(key).styled(style -> style
			.withColor(color)
			.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(hoverKey)))
		);
	}
}
