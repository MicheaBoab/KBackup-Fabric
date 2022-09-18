package com.keuin.kbackupfabric.ui;

import com.keuin.kbackupfabric.backup.BackupFilesystemUtil;
import com.keuin.kbackupfabric.backup.name.IncrementalBackupFileNameEncoder;
import com.keuin.kbackupfabric.backup.name.PrimitiveBackupFileNameEncoder;
import com.keuin.kbackupfabric.backup.suggestion.BackupNameSuggestionProvider;
import com.keuin.kbackupfabric.metadata.MetadataHolder;
import com.keuin.kbackupfabric.operation.BackupOperation;
import com.keuin.kbackupfabric.operation.DeleteOperation;
import com.keuin.kbackupfabric.operation.RestoreOperation;
import com.keuin.kbackupfabric.operation.abstracts.i.Invokable;
import com.keuin.kbackupfabric.operation.backup.method.ConfiguredBackupMethod;
import com.keuin.kbackupfabric.operation.backup.method.ConfiguredIncrementalBackupMethod;
import com.keuin.kbackupfabric.operation.backup.method.ConfiguredPrimitiveBackupMethod;
import com.keuin.kbackupfabric.util.DateUtil;
import com.keuin.kbackupfabric.util.PrintUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.keuin.kbackupfabric.backup.BackupFilesystemUtil.*;
import static com.keuin.kbackupfabric.util.PrintUtil.*;

public final class KBCommands {


    private static final int SUCCESS = 1;
    private static final int FAILED = -1;
    private static final String DEFAULT_BACKUP_NAME = "noname";
    private static boolean notifiedPreviousRestoration = false;

    // don't access it directly
    private static MinecraftServer server;
    private static BackupManager backupManager;
    private static final Object managerCreatorLock = new Object();

    //private static final Logger LOGGER = LogManager.getLogger();

    private static final List<BackupInfo> backupList = new ArrayList<>(); // index -> backupName
    private static Invokable pendingOperation = null;
    //private static BackupMethod activatedBackupMethod = new PrimitiveBackupMethod(); // The backup method we currently using

    public static void setServer(MinecraftServer server) {
        KBCommands.server = server;
    }

    private static MinecraftServer getServer() {
        if (server != null)
            return server;
        throw new IllegalStateException("server is not initialized.");
    }

    private static BackupManager getBackupManager() {
        synchronized (managerCreatorLock) {
            if (backupManager == null)
                backupManager = new BackupManager(getBackupSaveDirectory(getServer()));
            return backupManager;
        }
    }

    /**
     * Print the help menu.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int help(CommandContext<ServerCommandSource> context) {
        msgInfo(context, "======== KBackup Manual ========");
        msgInfo(context, "/kb , /kb help - 显示帮助菜单.");
        msgInfo(context, "/kb list - 列出所有存档.");
        msgInfo(context, "/kb backup [incremental(增量)/zip(压缩)] [文件名] - 备份当前信息至文件. 名字默认为当前系统时间.");
        msgInfo(context, "/kb restore <文件名> - 时光回溯至选取的时间点.");
        msgInfo(context, "/kb confirm - 确认并且开始时光回溯.");
        msgInfo(context, "/kb cancel - 取消时光回溯命令.");
        msgInfo(context, "=================================");
        return SUCCESS;
    }

    /**
     * Print the help menu. (May show extra info during the first run after restoring)
     *
     * @param context the context.
     * @return stat code.
     */
    public static int kb(CommandContext<ServerCommandSource> context) {
        int statCode = list(context);
        if (MetadataHolder.hasMetadata() && !notifiedPreviousRestoration) {
            // Output metadata info
            notifiedPreviousRestoration = true;
            msgStress(context, "时间回溯至 "
                    + MetadataHolder.getMetadata().getBackupName() + " (创建于 " +
                    DateUtil.fromEpochMillis(MetadataHolder.getMetadata().getBackupTime())
                    + ")");
        }
        return statCode;
    }

    private static void updateBackupList() {
        synchronized (backupList) {
            backupList.clear();
            List<BackupInfo> list = new ArrayList<>();
            getBackupManager().getAllBackups().forEach(list::add);
            list.sort(Comparator.comparing(BackupInfo::getCreationTime).reversed());
            backupList.addAll(list);
        }
    }

    /**
     * List all existing backups.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int list(CommandContext<ServerCommandSource> context) {
        // lazy: it just works as expected. Don't try to refactor, it's a waste of time. Just improve display and
        //       that's enough.
        // TODO: Show real name and size and etc info for incremental backup
        // TODO: Show concrete info from metadata for `.zip` backup
//        MinecraftServer server = context.getSource().getMinecraftServer();
        // TODO: refactor this to use {@link ObjectCollectionSerializer#fromDirectory}
//        File[] files = getBackupSaveDirectory(server).listFiles(
//                (dir, name) -> dir.isDirectory() &&
//                        (name.toLowerCase().endsWith(".zip") && name.toLowerCase().startsWith(getBackupFileNamePrefix())
//                                || name.toLowerCase().endsWith(".kbi"))
//        );

//        Function<File, String> backupInformationProvider = file -> {
//            Objects.requireNonNull(file);
//            if (file.getName().toLowerCase().endsWith(".zip"))
//                return getPrimitiveBackupInformationString(file.getName(), file.length());
//                // TODO: refactor this to use {@link ObjectCollectionSerializer#fromDirectory}
//            else if (file.getName().toLowerCase().endsWith(".kbi"))
//                return getIncrementalBackupInformationString(file);
//            return file.getName();
//        };

        updateBackupList();
        synchronized (backupList) {
            if (backupList.isEmpty())
                msgInfo(context, "当前无已储存时间节点,如需生成新节点,请使用 `/kb backup`.");
            else
                msgInfo(context, "当前可使用节点:");
            for (int i = backupList.size() - 1; i >= 0; --i) {
                BackupInfo info = backupList.get(i);
                printBackupInfo(context, info, i);
            }
//            if (files != null) {
//                if (files.length != 0) {
//                    msgInfo(context, "Available backups: (file is not checked, manipulation may affect this plugin)");
//                } else {
//                    msgInfo(context, "There are no available backups. To make a new backup, run /kb backup.");
//                }
//                int i = 0;
//                for (File file : files) {
//                    ++i;
//                    String backupFileName = file.getName();
//                    msgInfo(context, String.format("[%d] %s", i, backupInformationProvider.apply(file)));
//                    backupFileNameList.add(backupFileName);
//                }
//            } else {
//                msgErr(context, "Error: failed to list files in backup folder.");
//            }
        }
        return SUCCESS;
    }

    /**
     * Print backup information.
     *
     * @param context the context.
     * @param info    the info.
     * @param i       the index, starting from 0.
     */
    private static void printBackupInfo(CommandContext<ServerCommandSource> context, BackupInfo info, int i) {
        msgInfo(context, String.format(
                "[%d] (%s) %s (%s) %s",
                i + 1,
                info.getType(),
                info.getName(),
                DateUtil.getPrettyString(info.getCreationTime()),
                (info.getSizeBytes() > 0) ? BackupFilesystemUtil.getFriendlyFileSizeString(info.getSizeBytes()) : ""
        ));
    }

    /**
     * Backup with context parameter backupName.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int primitiveBackup(CommandContext<ServerCommandSource> context) {
        //KBMain.backup("name")
        String customBackupName = StringArgumentType.getString(context, "backupName");
        if (customBackupName.matches("[0-9]*")) {
            // Numeric param is not allowed
            customBackupName = String.format("a%s", customBackupName);
            msgWarn(context, String.format("不支持使用纯数字名称. 已自动修改为 %s", customBackupName));
        }
        return doBackup(context, customBackupName, false);
    }

    /**
     * Backup with default name.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int primitiveBackupWithDefaultName(CommandContext<ServerCommandSource> context) {
        return doBackup(context, DEFAULT_BACKUP_NAME, false);
    }

    public static int incrementalBackup(CommandContext<ServerCommandSource> context) {
        String customBackupName = StringArgumentType.getString(context, "backupName");
        if (customBackupName.matches("[0-9]*")) {
            // Numeric param is not allowed
            customBackupName = String.format("a%s", customBackupName);
            msgWarn(context, String.format("不支持使用纯数字名称. 已自动修改为 %s", customBackupName));
        }
        return doBackup(context, customBackupName, true);
    }

    public static int incrementalBackupWithDefaultName(CommandContext<ServerCommandSource> context) {
        return doBackup(context, DEFAULT_BACKUP_NAME, true);
    }


//    public static int incrementalBackup(CommandContext<ServerCommandSource> context) {
//        //KBMain.backup("name")
//        String backupName = StringArgumentType.getString(context, "backupName");
//        if (backupName.matches("[0-9]*")) {
//            // Numeric param is not allowed
//            backupName = String.format("a%s", backupName);
//            msgWarn(context, String.format("Pure numeric name is not allowed. Renaming to %s", backupName));
//        }
//        return doBackup(context, backupName, IncrementalBackupMethod.getInstance());
//    }
//
//    public static int incrementalBackupWithDefaultName(CommandContext<ServerCommandSource> context) {
//        return doBackup(context, DEFAULT_BACKUP_NAME, IncrementalBackupMethod.getInstance());
//    }

    /**
     * Delete an existing backup with context parameter backupName.
     * Simply set the pending backupName to given backupName, for the second confirmation.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int delete(CommandContext<ServerCommandSource> context) {

        String backupFileName = parseBackupFileName(context, StringArgumentType.getString(context, "backupName"));
        MinecraftServer server = context.getSource().getMinecraftServer();

        if (backupFileName == null)
            return list(context); // Show the list and return

        // Validate backupName
        if (!isBackupFileExists(backupFileName, server)) {
            // Invalid backupName
            msgErr(context, "警告!!! 无效名称, 请检查是否输入正确.");
            return FAILED;
        }

        // Update pending task
        //pendingOperation = AbstractConfirmableOperation.createDeleteOperation(context, backupName);
        pendingOperation = new DeleteOperation(context, backupFileName);

        msgWarn(context, String.format("警告!!! 此操作是不可逆的, 您将会用永久失去此时间节点. 请使用/kb cancle 终止 或 /kb confirm 确认请求.", backupFileName), true);
        return SUCCESS;
    }


    /**
     * Restore with context parameter backupName.
     * Simply set the pending backupName to given backupName, for the second confirmation.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int restore(CommandContext<ServerCommandSource> context) {
        try {
            //KBMain.restore("name")
            MinecraftServer server = context.getSource().getMinecraftServer();
            String backupFileName = parseBackupFileName(context, StringArgumentType.getString(context, "backupName"));
//            backupFileName = parseBackupFileName(context, backupFileName);

            if (backupFileName == null)
                return list(context); // Show the list and return

            // Validate backupName
            if (!isBackupFileExists(backupFileName, server)) {
                // Invalid backupName
                msgErr(context, "警告!!! 无效名称, 请检查是否输入正确.", false);
                return FAILED;
            }

            // Detect backup type

            // Update pending task
            //pendingOperation = AbstractConfirmableOperation.createRestoreOperation(context, backupName);
//        File backupFile = new File(getBackupSaveDirectory(server), getBackupFileName(backupName));
            // TODO: improve this
            ConfiguredBackupMethod method = backupFileName.endsWith(".zip") ?
                    new ConfiguredPrimitiveBackupMethod(
                            backupFileName, getLevelPath(server), getBackupSaveDirectory(server).getAbsolutePath()
                    ) : new ConfiguredIncrementalBackupMethod(
                    backupFileName, getLevelPath(server),
                    getBackupSaveDirectory(server).getAbsolutePath(),
                    getIncrementalBackupBaseDirectory(server).getAbsolutePath()
            );
            // String backupSavePath, String levelPath, String backupFileName
//        getBackupSaveDirectory(server).getAbsolutePath(), getLevelPath(server), backupFileName
            pendingOperation = new RestoreOperation(context, method);

            msgWarn(context, String.format("警告!!! 此操作是不可逆的, 您将时光回溯至 %s 并且失去当前世界 请使用/kb cancle 终止 或 /kb confirm 确认请求.", backupFileName), true);
            return SUCCESS;
        } catch (IOException e) {
            msgErr(context, String.format("创建存档时出现错误 (I/O): %s", e));
        }
        return FAILED;
    }

    private static int doBackup(CommandContext<ServerCommandSource> context, String customBackupName, boolean incremental) {
        try {
            // Real backup name (compatible with legacy backup): date_name, such as 2020-04-23_21-03-00_test
            //KBMain.backup("name")
//        String backupName = BackupNameTimeFormatter.getTimeString() + "_" + customBackupName;

            // Validate file name
            final char[] ILLEGAL_CHARACTERS = {'/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>', '|', '\"', ':'};
            for (char c : ILLEGAL_CHARACTERS) {
                if (customBackupName.contains(String.valueOf(c))) {
                    msgErr(context, String.format("文件名请勿出现特殊符号 \"%c\".", c));
                    return FAILED;
                }
            }

            PrintUtil.info("开始存档 ...");

            // configure backup method
            MinecraftServer server = context.getSource().getMinecraftServer();
            ConfiguredBackupMethod method = !incremental ? new ConfiguredPrimitiveBackupMethod(
                    PrimitiveBackupFileNameEncoder.INSTANCE.encode(customBackupName, LocalDateTime.now()),
                    getLevelPath(server),
                    getBackupSaveDirectory(server).getCanonicalPath()
            ) : new ConfiguredIncrementalBackupMethod(
                    IncrementalBackupFileNameEncoder.INSTANCE.encode(customBackupName, LocalDateTime.now()),
                    getLevelPath(server),
                    getBackupSaveDirectory(server).getCanonicalPath(),
                    getIncrementalBackupBaseDirectory(server).getCanonicalPath()
            );

            // dispatch to operation worker
            BackupOperation operation = new BackupOperation(context, method);
            if (operation.invoke()) {
                return SUCCESS;
            } else if (operation.isBlocked()) {
                msgWarn(context, "请勿重复操作, 正在努力完整上一个存档任务.");
                return FAILED;
            }
        } catch (IOException e) {
            msgErr(context, String.format("创建存档时出现错误 (I/O): %s", e));
        }
        return FAILED;
    }

    /**
     * Restore with context parameter backupName.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int confirm(CommandContext<ServerCommandSource> context) {
        if (pendingOperation == null) {
            msgWarn(context, "当前无需要确认的请求.");
            return FAILED;
        }

        Invokable operation = pendingOperation;
        pendingOperation = null;

        boolean returnValue = operation.invoke();

        // By the way, update suggestion list.
        BackupNameSuggestionProvider.updateCandidateList();

        return returnValue ? SUCCESS : FAILED; // block compiler's complain.
    }

    /**
     * Cancel the execution to be confirmed.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int cancel(CommandContext<ServerCommandSource> context) {
        if (pendingOperation != null) {
            PrintUtil.msgInfo(context, String.format(" %s 请求已被终止.", pendingOperation.toString()), true);
            pendingOperation = null;
            return SUCCESS;
        } else {
            msgErr(context, "当前无正在处理的请求.");
            return FAILED;
        }
    }

    /**
     * Show the most recent backup.
     * If there is no available backup, print specific info.
     *
     * @param context the context.
     * @return stat code.
     */
    public static int prev(CommandContext<ServerCommandSource> context) {
        // FIXME: This breaks after adding incremental backup
        try {
            // List all backups
            updateBackupList();
//            MinecraftServer server = context.getSource().getMinecraftServer();
//            List<File> files = Arrays.asList(Objects.requireNonNull(getBackupSaveDirectory(server).listFiles()));
//            files.removeIf(f -> !f.getName().startsWith(BackupFilesystemUtil.getBackupFileNamePrefix()));
//            files.sort((x, y) -> (int) (BackupFilesystemUtil.getBackupTimeFromBackupFileName(y.getName()) - BackupFilesystemUtil.getBackupTimeFromBackupFileName(x.getName())));
//            File prevBackupFile = files.get(0);
//            String backupFileName = prevBackupFile.getName();
//            int i;
//            synchronized (backupList) {
//                i = backupList.indexOf(backupFileName);
//                if (i == -1) {
//                    backupList.add(backupFileName);
//                    i = backupList.size();
//                } else {
//                    ++i;
//                }
//            }
            synchronized (backupList) {
                if (!backupList.isEmpty()) {
                    BackupInfo info = backupList.get(0);
                    msgInfo(context, "最新节点:");
                    printBackupInfo(context, info, 0);
                } else {
                    msgInfo(context, "当前无可用时间节点.");
                }
            }
        } catch (SecurityException ignored) {
            msgErr(context, "读取文件失败.");
            return FAILED;
        }
        return SUCCESS;
    }

//    private static String getPrimitiveBackupInformationString(String backupFileName, long backupFileSizeBytes) {
//        return String.format(
//                "(ZIP) %s , size: %s",
//                PrimitiveBackupFileNameEncoder.INSTANCE.decode(backupFileName),
//                getFriendlyFileSizeString(backupFileSizeBytes)
//        );
//    }

//    private static String getIncrementalBackupInformationString(File backupFile) {
//        try {
//            SavedIncrementalBackup info = IncBackupInfoSerializer.fromFile(backupFile);
//            return "(Incremental) " + info.getBackupName()
//                    + ", " + DateUtil.getString(info.getBackupTime())
//                    + ((info.getTotalSizeBytes() > 0) ?
//                    (" size: " + BackupFilesystemUtil.getFriendlyFileSizeString(info.getTotalSizeBytes())) : "");
//        } catch (IOException e) {
//            e.printStackTrace();
//            return "(Incremental) " + backupFile.getName();
//        }
//    }

//    /**
//     * Select the backup method we use.
//     * @param context the context.
//     * @return stat code.
//     */
//    public static int setMethod(CommandContext<ServerCommandSource> context) {
//        String desiredMethodName = StringArgumentType.getString(context, "backupMethod");
//        List<BackupType> backupMethods = Arrays.asList(BackupType.PRIMITIVE_ZIP_BACKUP, BackupType.OBJECT_TREE_BACKUP);
//        for (BackupType method : backupMethods) {
//            if(method.getName().equals(desiredMethodName)) {
//                // Incremental backup
////                activatedBackupMethod =
//                msgInfo(context, String.format("Backup method is set to: %s", desiredMethodName));
//                return SUCCESS;
//            }
//        }
//
//        return SUCCESS;
//    }


    private static String parseBackupFileName(CommandContext<ServerCommandSource> context, String userInput) {
        try {
            String backupName = StringArgumentType.getString(context, "backupName");

            if (backupName.matches("[0-9]*")) {
                // treat numeric input as backup index number in list
                int index = Integer.parseInt(backupName) - 1;
                synchronized (backupList) {
                    return backupList.get(index).getBackupFileName(); // Replace input number with real backup file name.
                }
            }
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
        }
        return userInput;
    }
}
