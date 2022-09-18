package com.keuin.kbackupfabric.operation;

import com.keuin.kbackupfabric.backup.BackupFilesystemUtil;
import com.keuin.kbackupfabric.backup.incremental.ObjectCollection2;
import com.keuin.kbackupfabric.backup.incremental.ObjectCollectionSerializer;
import com.keuin.kbackupfabric.backup.incremental.manager.IncrementalBackupStorageManager;
import com.keuin.kbackupfabric.backup.incremental.serializer.IncBackupInfoSerializer;
import com.keuin.kbackupfabric.backup.incremental.serializer.SavedIncrementalBackup;
import com.keuin.kbackupfabric.backup.suggestion.BackupNameSuggestionProvider;
import com.keuin.kbackupfabric.operation.abstracts.InvokableAsyncBlockingOperation;
import com.keuin.kbackupfabric.util.PrintUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import static com.keuin.kbackupfabric.backup.BackupFilesystemUtil.getBackupSaveDirectory;
import static com.keuin.kbackupfabric.backup.BackupFilesystemUtil.getIncrementalBackupBaseDirectory;
import static com.keuin.kbackupfabric.util.PrintUtil.msgErr;
import static com.keuin.kbackupfabric.util.PrintUtil.msgInfo;
import static org.apache.commons.io.FileUtils.forceDelete;

public class DeleteOperation extends InvokableAsyncBlockingOperation {

    private static final Logger LOGGER = Logger.getLogger(DeleteOperation.class.getName());
    private final String backupFileName;
    private final CommandContext<ServerCommandSource> context;

    public DeleteOperation(CommandContext<ServerCommandSource> context, String backupFileName) {
        super("BackupDeletingWorker");
        this.backupFileName = backupFileName;
        this.context = context;
    }

    @Override
    public String toString() {
        return String.format("deletion of %s", backupFileName);
    }

    @Override
    protected void async() {
        delete();
        BackupNameSuggestionProvider.updateCandidateList();
    }

    private void delete() {
        try {
            MinecraftServer server = context.getSource().getMinecraftServer();
            PrintUtil.info("删除时光节点 " + this.backupFileName);
            File backupFile = new File(getBackupSaveDirectory(server), backupFileName);
            SavedIncrementalBackup incrementalBackup = null;
            if (backupFile.getName().endsWith(".kbi")) {
                incrementalBackup = IncBackupInfoSerializer.fromFile(backupFile);
            }

            // remove .zip or .kbi file
            PrintUtil.info("正在移除节点 " + backupFileName + "...");
            int tryCounter = 0;
            do {
                if (tryCounter == 5) {
                    String msg = "移除时间节点失败 " + backupFileName;
                    PrintUtil.error(msg);
                    msgErr(context, msg);
                    return;
                }
                try {
                    if (!backupFile.delete())
                        forceDelete(backupFile);
                } catch (SecurityException | NullPointerException | IOException ignored) {
                }
                ++tryCounter;
            } while (backupFile.exists());


            // If it is an incremental backup, do clean-up
            if (incrementalBackup != null) {
                PrintUtil.info("清理中 ...");
                IncrementalBackupStorageManager manager =
                        new IncrementalBackupStorageManager(getIncrementalBackupBaseDirectory(server).toPath());
                Iterable<ObjectCollection2> backups = ObjectCollectionSerializer
                        .fromDirectory(BackupFilesystemUtil
                                .getBackupSaveDirectory(context.getSource().getMinecraftServer()));
                int deleted = manager.deleteObjectCollection(incrementalBackup.getObjectCollection(), backups);
                PrintUtil.info("已移除 " + deleted + " 无用文件.");
            }

            PrintUtil.info("成功删除时间节点 " + this.backupFileName);
            msgInfo(context, "成功删除时间节点文件 " + this.backupFileName);
        } catch (IOException e) {
            LOGGER.severe("移除时间节点文件失败: " + e);
        }
    }
}
