package com.keuin.kbackupfabric.operation;

import com.keuin.kbackupfabric.operation.abstracts.InvokableAsyncBlockingOperation;
import com.keuin.kbackupfabric.operation.backup.feedback.BackupFeedback;
import com.keuin.kbackupfabric.operation.backup.method.ConfiguredBackupMethod;
import com.keuin.kbackupfabric.util.PrintUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.keuin.kbackupfabric.util.PrintUtil.msgInfo;

public class BackupOperation extends InvokableAsyncBlockingOperation {

    private final CommandContext<ServerCommandSource> context;
    private final Map<World, Boolean> oldWorldsSavingDisabled = new HashMap<>();
    private final ConfiguredBackupMethod configuredBackupMethod;
    private long startTime;


    public BackupOperation(CommandContext<ServerCommandSource> context, ConfiguredBackupMethod configuredBackupMethod) {
        super("BackupWorker");
        this.context = context;
        this.configuredBackupMethod = configuredBackupMethod;
    }

    @Override
    protected void async() {
        String backupSaveDirectory = "";
        MinecraftServer server = context.getSource().getMinecraftServer();
        boolean success = false; // only success when everything is done
        try {
            //// Do our main backup logic

            // Create backup saving directory
            if (!configuredBackupMethod.touch()) {
                PrintUtil.msgErr(context, "创建备份文件路径失败,无法备份.");
                return;
            }

            // Backup
            BackupFeedback result = configuredBackupMethod.backup();
            success = result.isSuccess();
            if (success) {
                // Restore previous auto-save switch stat
                server.getWorlds().forEach(world -> world.savingDisabled = oldWorldsSavingDisabled.getOrDefault(world, true));

                // Finish. Print time elapsed and file size
                long timeElapsedMillis = System.currentTimeMillis() - startTime;
                String msgText = String.format("已成功备份, 用时: %.2fs. ", timeElapsedMillis / 1000.0) + result.getFeedback();
                PrintUtil.msgInfo(context, msgText, true);
            } else {
                // failed
                PrintUtil.msgErr(context, "备份工作失败: " + result.getFeedback());
            }
        } catch (SecurityException e) {
            msgInfo(context, String.format("创建备份文件路径失败,备份未成功.", backupSaveDirectory));
        } catch (IOException e) {
            msgInfo(context, "压缩文件失败: " + e.getMessage());
        }
    }

    @Override
    protected boolean sync() {
        //// Save world, save old auto-save configs

        PrintUtil.broadcast("正在对当前时间节点进行备份,请稍后 ...");

        // Get server
        MinecraftServer server = context.getSource().getMinecraftServer();

        // Save old auto-save switch state for restoration after finished
        oldWorldsSavingDisabled.clear();
        server.getWorlds().forEach(world -> {
            oldWorldsSavingDisabled.put(world, world.savingDisabled);
            world.savingDisabled = true;
        });

        // Force to save all player data and worlds
        PrintUtil.msgInfo(context, "正在扫描玩家信息 ...");
        server.getPlayerManager().saveAllPlayerData();
        PrintUtil.msgInfo(context, "正在扫描当前宇宙 ...");
        server.save(true, true, true);

        // Log start time
        startTime = System.currentTimeMillis();
        return true;
    }
}
