package com.mryu.signin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mryu.signin.data.SigninResult;
import com.mryu.signin.network.PlayerSyncPayload;
import com.mryu.signin.network.SigninNetworking;
import com.mryu.signin.service.SigninService;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SigninCommand {
	private static final String SUB_STATUS_CN = "状态";
	private static final String SUB_GUI_CN = "界面";
	private static final String SUB_MAKEUP_CN = "补签";
	private static final String SUB_CLEAR_CN = "清空";

	private static final String SUB_STATUS_EN = "status";
	private static final String SUB_GUI_EN = "gui";
	private static final String SUB_MAKEUP_EN = "makeup";
	private static final String SUB_CLEAR_EN = "clear";

	private SigninCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		registerRoot(dispatcher, "signin", SUB_STATUS_EN, SUB_GUI_EN, SUB_MAKEUP_EN, SUB_CLEAR_EN);
		registerRoot(dispatcher, "签到", SUB_STATUS_CN, SUB_GUI_CN, SUB_MAKEUP_CN, SUB_CLEAR_CN);
		registerRoot(dispatcher, "qd", SUB_STATUS_CN, SUB_GUI_CN, SUB_MAKEUP_CN, SUB_CLEAR_CN);
	}

	private static void registerRoot(
		CommandDispatcher<ServerCommandSource> dispatcher,
		String root,
		String subStatus,
		String subGui,
		String subMakeup,
		String subClear
	) {
		LiteralArgumentBuilder<ServerCommandSource> command = CommandManager.literal(root)
			.requires(SigninPermissions::canUseSign)
			.executes(context -> sign(context.getSource()))
			.then(CommandManager.literal(subStatus)
				.requires(SigninPermissions::canUseStatus)
				.executes(context -> status(context.getSource())))
			.then(CommandManager.literal(subGui)
				.requires(SigninPermissions::canUseGui)
				.executes(context -> openGui(context.getSource())))
			.then(CommandManager.literal(subMakeup)
				.requires(SigninPermissions::canUseMakeup)
				.executes(context -> makeup(context.getSource())))
			.then(CommandManager.literal(subClear)
				.requires(SigninPermissions::canUseClear)
				.executes(context -> clearSelf(context.getSource()))
				.then(CommandManager.argument("player", EntityArgumentType.player())
					.requires(SigninPermissions::canUseClearOthers)
					.executes(context -> clearTarget(context.getSource(), EntityArgumentType.getPlayer(context, "player")))));

		dispatcher.register(command);
	}

	private static int sign(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SigninResult result = SigninService.signToday(source.getServer(), player);
		player.sendMessage(result.message().copy().formatted(result.success() ? Formatting.GREEN : Formatting.YELLOW), false);
		SigninNetworking.sendSync(player, false, result.message());
		return 1;
	}

	private static int status(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		PlayerSyncPayload payload = SigninService.buildSyncPayload(source.getServer(), player, Text.empty());
		player.sendMessage(SigninService.buildStatusText(payload).copy().formatted(Formatting.AQUA), false);
		return 1;
	}

	private static int openGui(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SigninNetworking.openMainScreen(player);
		return 1;
	}

	private static int makeup(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		SigninResult result = SigninService.makeupYesterday(source.getServer(), player);
		player.sendMessage(result.message().copy().formatted(result.success() ? Formatting.GREEN : Formatting.RED), false);
		SigninNetworking.sendSync(player, false, result.message());
		return 1;
	}

	private static int clearSelf(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		boolean changed = SigninService.clearPlayerSigninData(source.getServer(), player);
		MutableText message = changed
			? Text.translatable("message.signin.clear.self_success")
			: Text.translatable("message.signin.clear.self_no_data");
		player.sendMessage(message.copy().formatted(changed ? Formatting.GREEN : Formatting.YELLOW), false);
		SigninNetworking.sendSync(player, false, message);
		return 1;
	}

	private static int clearTarget(ServerCommandSource source, ServerPlayerEntity target) {
		boolean changed = SigninService.clearPlayerSigninData(source.getServer(), target);
		MutableText operatorMessage = changed
			? Text.translatable("message.signin.clear.target_success", target.getName())
			: Text.translatable("message.signin.clear.target_no_data", target.getName());
		MutableText targetMessage = changed
			? Text.translatable("message.signin.clear.by_admin_success")
			: Text.translatable("message.signin.clear.by_admin_no_data");

		source.sendFeedback(() -> operatorMessage.copy().formatted(changed ? Formatting.GREEN : Formatting.YELLOW), false);
		target.sendMessage(targetMessage.copy().formatted(changed ? Formatting.YELLOW : Formatting.GRAY), false);
		SigninNetworking.sendSync(target, false, targetMessage);
		return 1;
	}
}
