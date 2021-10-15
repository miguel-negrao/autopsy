/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import static org.sleuthkit.autopsy.core.UserPreferences.hideSlackFilesInViewsTree;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
@Messages({"ThreePanelViewsDAO.fileColumns.nameColLbl=Name",
    "ThreePanelViewsDAO.fileColumns.originalName=Original Name",
    "ThreePanelViewsDAO.fileColumns.scoreName=S",
    "ThreePanelViewsDAO.fileColumns.commentName=C",
    "ThreePanelViewsDAO.fileColumns.countName=O",
    "ThreePanelViewsDAO.fileColumns.locationColLbl=Location",
    "ThreePanelViewsDAO.fileColumns.modifiedTimeColLbl=Modified Time",
    "ThreePanelViewsDAO.fileColumns.changeTimeColLbl=Change Time",
    "ThreePanelViewsDAO.fileColumns.accessTimeColLbl=Access Time",
    "ThreePanelViewsDAO.fileColumns.createdTimeColLbl=Created Time",
    "ThreePanelViewsDAO.fileColumns.sizeColLbl=Size",
    "ThreePanelViewsDAO.fileColumns.flagsDirColLbl=Flags(Dir)",
    "ThreePanelViewsDAO.fileColumns.flagsMetaColLbl=Flags(Meta)",
    "ThreePanelViewsDAO.fileColumns.modeColLbl=Mode",
    "ThreePanelViewsDAO.fileColumns.useridColLbl=UserID",
    "ThreePanelViewsDAO.fileColumns.groupidColLbl=GroupID",
    "ThreePanelViewsDAO.fileColumns.metaAddrColLbl=Meta Addr.",
    "ThreePanelViewsDAO.fileColumns.attrAddrColLbl=Attr. Addr.",
    "ThreePanelViewsDAO.fileColumns.typeDirColLbl=Type(Dir)",
    "ThreePanelViewsDAO.fileColumns.typeMetaColLbl=Type(Meta)",
    "ThreePanelViewsDAO.fileColumns.knownColLbl=Known",
    "ThreePanelViewsDAO.fileColumns.md5HashColLbl=MD5 Hash",
    "ThreePanelViewsDAO.fileColumns.sha256HashColLbl=SHA-256 Hash",
    "ThreePanelViewsDAO.fileColumns.objectId=Object ID",
    "ThreePanelViewsDAO.fileColumns.mimeType=MIME Type",
    "ThreePanelViewsDAO.fileColumns.extensionColLbl=Extension",
    "ThreePanelViewsDAO.fileColumns.noDescription=No Description"})
public class ViewsDAO {

    private static final String FILE_VIEW_EXT_TYPE_ID = "FILE_VIEW_BY_EXT";

    private static final List<ColumnKey> FILE_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_originalName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_scoreName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_commentName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_countName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_locationColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_modifiedTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_changeTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_accessTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_createdTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_sizeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_flagsDirColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_flagsMetaColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_modeColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_useridColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_groupidColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_metaAddrColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_attrAddrColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_typeDirColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_typeMetaColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_knownColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_md5HashColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_sha256HashColLbl()),
            // getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_objectId()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_mimeType()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_extensionColLbl()));

    private static ViewsDAO instance = null;

    synchronized static ViewsDAO getInstance() {
        if (instance == null) {
            instance = new ViewsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.ThreePanelViewsDAO_fileColumns_noDescription());
    }

    static ExtensionMediaType getExtensionMediaType(String ext) {
        if (StringUtils.isBlank(ext)) {
            return ExtensionMediaType.UNCATEGORIZED;
        } else {
            ext = "." + ext;
        }
        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
            return ExtensionMediaType.IMAGE;
        } else if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
            return ExtensionMediaType.VIDEO;
        } else if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
            return ExtensionMediaType.AUDIO;
        } else if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
            return ExtensionMediaType.DOC;
        } else if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
            return ExtensionMediaType.EXECUTABLE;
        } else if (FileTypeExtensions.getTextExtensions().contains(ext)) {
            return ExtensionMediaType.TEXT;
        } else if (FileTypeExtensions.getWebExtensions().contains(ext)) {
            return ExtensionMediaType.WEB;
        } else if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
            return ExtensionMediaType.PDF;
        } else if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
            return ExtensionMediaType.ARCHIVE;
        } else {
            return ExtensionMediaType.UNCATEGORIZED;
        }
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    private final Cache<FileTypeExtensionsSearchParams, SearchResultsDTO> fileTypeByExtensionCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<FileTypeMimeSearchParams, SearchResultsDTO> fileTypeByMimeCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    public SearchResultsDTO getFilesByExtension(FileTypeExtensionsSearchParams key) throws ExecutionException, IllegalArgumentException {
        if (key.getFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        return fileTypeByExtensionCache.get(key, () -> fetchExtensionSearchResultsDTOs(key.getFilter(), key.getDataSourceId()));
    }
    
    public SearchResultsDTO getFilesByMime(FileTypeMimeSearchParams key) throws ExecutionException, IllegalArgumentException {
        if (key.getMimeType() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }

        return fileTypeByMimeCache.get(key, () -> fetchMimeSearchResultsDTOs(key.getMimeType(), key.getDataSourceId()));
    }    
    

//    private ViewFileTableSearchResultsDTO fetchFilesForTable(ViewFileCacheKey cacheKey) throws NoCurrentCaseException, TskCoreException {
//
//    }
//
//    public ViewFileTableSearchResultsDTO getFilewViewForTable(BlackboardArtifact.Type artType, Long dataSourceId) throws ExecutionException, IllegalArgumentException {
//        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT) {
//            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
//                    + "Artifact type must be non-null and data artifact.  "
//                    + "Received {0}", artType));
//        }
//
//        ViewFileCacheKey cacheKey = new ViewFileCacheKey(artType, dataSourceId);
//        return dataArtifactCache.get(cacheKey, () -> fetchFilesForTable(cacheKey));
//    }
    private Map<Integer, Long> fetchFileViewCounts(List<FileExtSearchFilter> filters, Long dataSourceId) throws NoCurrentCaseException, TskCoreException {
        Map<Integer, Long> counts = new HashMap<>();
        for (FileExtSearchFilter filter : filters) {
            String whereClause = getFileExtensionWhereStatement(filter, dataSourceId);
            long count = getCase().countFilesWhere(whereClause);
            counts.put(filter.getId(), count);
        }

        return counts;
    }

    private String getFileExtensionWhereStatement(FileExtSearchFilter filter, Long dataSourceId) {
        String whereClause = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + (hideKnownFilesInViewsTree()
                        ? " "
                        : " AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")")
                + (dataSourceId != null && dataSourceId > 0
                        ? " AND data_source_obj_id = " + dataSourceId
                        : " ")
                + " AND (extension IN (" + filter.getFilter().stream()
                        .map(String::toLowerCase)
                        .map(s -> "'" + StringUtils.substringAfter(s, ".") + "'")
                        .collect(Collectors.joining(", ")) + "))";
        return whereClause;
    }
    
    private String getFileMimeWhereStatement(String mimeType, Long dataSourceId) {

        String whereClause = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + " AND (type IN ("
                + TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()
                + (hideSlackFilesInViewsTree() ? "" : ("," + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.ordinal()))
                + "))"
                + (dataSourceId != null && dataSourceId > 0 ? " AND data_source_obj_id = " + dataSourceId : " ")
                + (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "")
                + " AND mime_type = '" + mimeType + "'";
    
        return whereClause;
    }    
    
    private SearchResultsDTO fetchExtensionSearchResultsDTOs(FileExtSearchFilter filter, Long dataSourceId) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileExtensionWhereStatement(filter, dataSourceId);
        return fetchFileViewFiles(whereStatement, filter.getDisplayName());
    }

    @NbBundle.Messages({"FileTypesByMimeType.name.text=By MIME Type"})
    private SearchResultsDTO fetchMimeSearchResultsDTOs(String mimeType, Long dataSourceId) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileMimeWhereStatement(mimeType, dataSourceId);
        final String MIME_TYPE_DISPLAY_NAME = Bundle.FileTypesByMimeType_name_text();
        return fetchFileViewFiles(whereStatement, MIME_TYPE_DISPLAY_NAME);
    }

    private SearchResultsDTO fetchFileViewFiles(String whereStatement, String displayName) throws NoCurrentCaseException, TskCoreException {
        List<AbstractFile> files = getCase().findAllFilesWhere(whereStatement);

        List<RowDTO> fileRows = new ArrayList<>();
        for (AbstractFile file : files) {

            boolean isArchive = FileTypeExtensions.getArchiveExtensions().contains("." + file.getNameExtension().toLowerCase());
            boolean encryptionDetected = isArchive && file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0;
            boolean hasVisibleChildren = isArchive || file.isDir();
            
            List<Object> cellValues = Arrays.asList(
                    file.getName(), // GVDTODO handle . and .. from getContentDisplayName()
                    // GVDTODO translation column
                    null,
                    //GVDTDO replace nulls with SCO
                    null,
                    null,
                    null,
                    file.getUniquePath(),
                    TimeZoneUtils.getFormattedTime(file.getMtime()),
                    TimeZoneUtils.getFormattedTime(file.getCtime()),
                    TimeZoneUtils.getFormattedTime(file.getAtime()),
                    TimeZoneUtils.getFormattedTime(file.getCrtime()),
                    file.getSize(),
                    file.getDirFlagAsString(),
                    file.getMetaFlagsAsString(),
                    // mode,
                    // userid,
                    // groupid,
                    // metaAddr,
                    // attrAddr,
                    // typeDir,
                    // typeMeta,

                    file.getKnown().getName(),
                    StringUtils.defaultString(file.getMd5Hash()),
                    StringUtils.defaultString(file.getSha256Hash()),
                    // objectId,

                    StringUtils.defaultString(file.getMIMEType()),
                    file.getNameExtension()
            );

            fileRows.add(new FileRowDTO(
                    file,
                    file.getId(),
                    file.getName(),
                    file.getNameExtension(),
                    getExtensionMediaType(file.getNameExtension()),
                    file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                    file.getType(),
                    encryptionDetected,
                    hasVisibleChildren,
                    cellValues));
        }

        return new BaseSearchResultsDTO(FILE_VIEW_EXT_TYPE_ID, displayName, FILE_COLUMNS, fileRows);
    }

}