package com.keuin.kbackupfabric.data;

import java.io.Serializable;

public class BackupMetadata implements Serializable {
    public static final String metadataFileName = "kbackup_metadata";
    private static final long serialVersionUID = 1L;
    private final long BackupTime;
    private final String backupName;

    public BackupMetadata(long backupTime, String backupName) {
        BackupTime = backupTime;
        this.backupName = backupName;
    }

    public long getBackupTime() {
        return BackupTime;
    }

    public String getBackupName() {
        return backupName;
    }
}
