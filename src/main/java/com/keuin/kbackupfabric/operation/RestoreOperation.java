package com.keuin.kbackupfabric.operation;

import com.keuin.kbackupfabric.operation.abstracts.InvokableBlockingOperation;
import com.keuin.kbackupfabric.operation.backup.method.ConfiguredBackupMethod;
import com.keuin.kbackupfabric.util.PrintUtil;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.io.IOException;
import java.util.Objects;

public class RestoreOperation extends InvokableBlockingOperation {

    //private static final Logger LOGGER = LogManager.getLogger();
    private final Thread serverThread;
    private final CommandContext<ServerCommandSource> context;
    private final MinecraftServer server;
    private final ConfiguredBackupMethod configuredBackupMethod;

    public RestoreOperation(CommandContext<ServerCommandSource> context, ConfiguredBackupMethod configuredBackupMethod) {
        server = Objects.requireNonNull(context.getSource().getMinecraftServer());
        this.serverThread = Objects.requireNonNull(server.getThread());
        this.context = Objects.requireNonNull(context);
        this.configuredBackupMethod = Objects.requireNonNull(configuredBackupMethod);
    }

    @Override
    protected boolean blockingContext() {
        // do restore to backupName
        PrintUtil.broadcast(String.format("时光回溯至 %s ...", configuredBackupMethod.getBackupFileName()));

        PrintUtil.debug("时间节点名称: " + configuredBackupMethod.getBackupFileName());

        PrintUtil.msgInfo(context, "服务器即将关闭.", true);
        PrintUtil.msgInfo(context, "温馨提示!!! 请勿强制关闭服务器, 若造成损失请自行承担.", true);
        PrintUtil.msgInfo(context, "服务器关闭后, 请自行手动重启服务器.", true);
        final int WAIT_SECONDS = 10;
        for (int i = 0; i < WAIT_SECONDS; ++i) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        PrintUtil.broadcast("正在关闭 ...");
        //RestoreWorker worker = new RestoreWorker(server.getThread(), backupFilePath, levelDirectory);
        Thread workerThread = new Thread(new WorkerThread(), "RestoreWorker");
        workerThread.start();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
        server.stop(false);
        return true;
    }

    @Override
    public String toString() {
        return String.format("时光回溯至 %s", configuredBackupMethod.getBackupFileName());
    }

    private class WorkerThread implements Runnable {

        @Override
        public void run() {
            try {
                // Wait server thread die
                PrintUtil.info("等待服务器线程退出 ...");
                while (serverThread.isAlive()) {
                    try {
                        serverThread.join();
                    } catch (InterruptedException | RuntimeException ignored) {
                    }
                }

                int cnt = 5;
                do {
                    PrintUtil.info(String.format("等待 %d 秒...", cnt));
                    try{
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }while(--cnt > 0);

                ////////////////////
                long startTime = System.currentTimeMillis();
                if (configuredBackupMethod.restore()) {
                    long endTime = System.currentTimeMillis();
                    PrintUtil.info(String.format(
                            "时光回溯完毕! (%.2fs) 请手动重启服务器.",
                            (endTime - startTime) / 1000.0
                    ));
                    //ServerRestartUtil.forkAndRestart();
                    System.exit(111);
                } else {
                    PrintUtil.error("时光回溯失败,服务器将无法自行重启.");
                }

            } catch (SecurityException e) {
                e.printStackTrace();
                PrintUtil.error("因某些原因导致回溯未成功 (Security).");
            } catch (IOException e) {
                e.printStackTrace();
                PrintUtil.error("因某些原因导致回溯未成功 (I/O).");
            }
            PrintUtil.error("回溯失败.");
            System.exit(0); // all failed restoration will eventually go here
        }
    }
}
