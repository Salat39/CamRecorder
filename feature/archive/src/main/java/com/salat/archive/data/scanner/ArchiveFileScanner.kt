package com.salat.archive.data.scanner

import com.salat.commonconst.RECORDS_ROOT_DIRECTORY_NAME
import com.salat.drivestorage.domain.repository.DriveStorageRepository
import java.io.File

internal class ArchiveFileScanner(
    private val driveStorageRepository: DriveStorageRepository,
) {

    fun getRecordsRootOrNull(): File? {
        val root = driveStorageRepository.getRemovableDriveRootOrNull() ?: return null
        val recordsRoot = File(root, RECORDS_ROOT_DIRECTORY_NAME)
        return recordsRoot.takeIf { it.exists() && it.isDirectory }
    }

    fun scanFiles(recordsRoot: File): List<File> {
        if (!recordsRoot.exists() || !recordsRoot.isDirectory) return emptyList()
        return recordsRoot.walkTopDown()
            .maxDepth(4)
            .filter { it.isFile }
            .toList()
    }
}
