package com.mryu.signin.command;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

public final class SigninPermissions {
	public static final String USER_SIGN = "signin.user.sign";
	public static final String USER_STATUS = "signin.user.status";
	public static final String USER_GUI = "signin.user.gui";
	public static final String USER_MAKEUP = "signin.user.makeup";
	public static final String ADMIN_REWARDS_EDIT = "signin.admin.rewards.edit";
	public static final String ADMIN_CLEAR = "signin.admin.clear";
	public static final String ADMIN_CLEAR_OTHERS = "signin.admin.clear.others";

	private static final Method FABRIC_CHECK_INT;
	private static final Method FABRIC_CHECK_BOOL;

	static {
		Method checkInt = null;
		Method checkBool = null;
		try {
			Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
			for (Method method : permissionsClass.getMethods()) {
				if (!"check".equals(method.getName()) || method.getParameterCount() != 3) {
					continue;
				}
				Class<?>[] params = method.getParameterTypes();
				if (params[1] != String.class) {
					continue;
				}
				if (params[2] == int.class) {
					checkInt = method;
				} else if (params[2] == boolean.class) {
					checkBool = method;
				}
			}
		} catch (ClassNotFoundException ignored) {
			// Optional dependency: fallback to vanilla permission levels.
		}
		FABRIC_CHECK_INT = checkInt;
		FABRIC_CHECK_BOOL = checkBool;
	}

	private SigninPermissions() {
	}

	public static boolean canUseSign(ServerCommandSource source) {
		return has(source, USER_SIGN, 0);
	}

	public static boolean canUseStatus(ServerCommandSource source) {
		return has(source, USER_STATUS, 0);
	}

	public static boolean canUseGui(ServerCommandSource source) {
		return has(source, USER_GUI, 0);
	}

	public static boolean canUseMakeup(ServerCommandSource source) {
		return has(source, USER_MAKEUP, 0);
	}

	public static boolean canManageRewards(ServerCommandSource source) {
		return has(source, ADMIN_REWARDS_EDIT, 2);
	}

	public static boolean canManageRewards(ServerPlayerEntity player) {
		return canManageRewards(player.getCommandSource());
	}

	public static boolean canUseClear(ServerCommandSource source) {
		return has(source, ADMIN_CLEAR, 2);
	}

	public static boolean canUseClearOthers(ServerCommandSource source) {
		return has(source, ADMIN_CLEAR_OTHERS, 2);
	}

	private static boolean has(ServerCommandSource source, String node, int fallbackLevel) {
		try {
			if (FABRIC_CHECK_INT != null && FABRIC_CHECK_INT.getParameterTypes()[0].isAssignableFrom(source.getClass())) {
				return (boolean) FABRIC_CHECK_INT.invoke(null, source, node, fallbackLevel);
			}
			if (FABRIC_CHECK_BOOL != null && FABRIC_CHECK_BOOL.getParameterTypes()[0].isAssignableFrom(source.getClass())) {
				boolean fallback = source.hasPermissionLevel(fallbackLevel);
				return (boolean) FABRIC_CHECK_BOOL.invoke(null, source, node, fallback);
			}
		} catch (ReflectiveOperationException ignored) {
			// If permissions API call fails, fallback to vanilla OP level.
		}
		return source.hasPermissionLevel(fallbackLevel);
	}
}
