package com.keuin.kbackupfabric;

import com.keuin.kbackupfabric.backup.suggestion.BackupNameSuggestionProvider;
import com.keuin.kbackupfabric.ui.KBCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public final class KBCommandsRegister {
    // First make method to register
    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {

        // register /kb and /kb help for help menu
        dispatcher.register(CommandManager.literal("kb")
                .requires(Permissions.require("kb.root", true))
                .executes(KBCommands::kb));
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("help")
                        .requires(Permissions.require("kb.help", true))
                        .executes(KBCommands::help)));

        // register /kb list for showing the backup list. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("list")
                        .requires(Permissions.require("kb.list", 4))
                        .executes(KBCommands::list)));

        // register /kb backup [name] for performing backup. OP is required.
        dispatcher.register(CommandManager.literal("kb").then(CommandManager.literal("backup").then(
                        CommandManager.argument("backupName", StringArgumentType.greedyString())
                                .requires(Permissions.require("kb.backup", 4))
                                .executes(KBCommands::primitiveBackup)
                ).requires(Permissions.require("kb.backup", 4))
                .executes(KBCommands::primitiveBackupWithDefaultName)));

        // register /kb incbak [name] for performing incremental backup. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("incbak")
                        .then(CommandManager.argument("backupName", StringArgumentType.greedyString())
                                .requires(Permissions.require("kb.incbak", 4))
                                .executes(KBCommands::incrementalBackup))
                        .requires(Permissions.require("kb.incbak", 4))
                        .executes(KBCommands::incrementalBackupWithDefaultName)
                ));

        // register /kb restore <name> for performing restore. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("restore")
                        .then(CommandManager.argument("backupName", StringArgumentType.greedyString())
                                .suggests(BackupNameSuggestionProvider.getProvider())
                                .requires(Permissions.require("kb.restore", 4))
                                .executes(KBCommands::restore))
                        .requires(Permissions.require("kb.list", 4))
                        .executes(KBCommands::list)));

        // register /kb delete [name] for deleting an existing backup. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("backupName", StringArgumentType.greedyString())
                                .suggests(BackupNameSuggestionProvider.getProvider())
//                                .requires(Permissions.require("kb.delete", 4))
                                .executes(KBCommands::delete))
                        .requires(Permissions.require("kb.delete", 4))));

        // register /kb confirm for confirming the execution. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("confirm")
                        .requires(Permissions.require("kb.confirm", 4))
                        .executes(KBCommands::confirm)));

        // register /kb cancel for cancelling the execution to be confirmed. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("cancel")
                        .requires(Permissions.require("kb.cancel", 4))
                        .executes(KBCommands::cancel)));

        // register /kb prev for showing the latest backup. OP is required.
        dispatcher.register(CommandManager.literal("kb")
                .then(CommandManager.literal("prev")
                        .requires(Permissions.require("kb.prev", 4))
                        .executes(KBCommands::prev)));

//        // register /kb setMethod for selecting backup method (zip, incremental)
//        dispatcher.register(CommandManager.literal("kb").then(CommandManager.literal("setMethod").then(CommandManager.argument("backupMethod", StringArgumentType.string()).suggests(BackupMethodSuggestionProvider.getProvider()).requires(PermissionValidator::op).executes(KBCommands::setMethod))));
    }
}
