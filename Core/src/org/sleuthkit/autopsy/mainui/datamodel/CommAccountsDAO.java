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
import java.beans.PropertyChangeEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides information to populate the results viewer for data in the 
 * Communication Accounts section.
 */
@Messages({"CommAccountsDAO.fileColumns.originalName=Source Name",
            "CommAccountsDAO.fileColumns.noDescription=No Description"})
public class CommAccountsDAO {

    private static final int CACHE_SIZE = Account.Type.PREDEFINED_ACCOUNT_TYPES.size(); // number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.CommAccountsDAO_fileColumns_originalName()) // GVDTODO handle translation
            );

    private static CommAccountsDAO instance = null;

    synchronized static CommAccountsDAO getInstance() {
        if (instance == null) {
            instance = new CommAccountsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.CommAccountsDAO_fileColumns_noDescription());
    }
    
    public SearchResultsDTO getCommAcounts(CommAccountsSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getType() == null) {
            throw new IllegalArgumentException("Must have non-null type");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }
        
        SearchParams<CommAccountsSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchCommAccountsDTOs(searchParams));
    }   

    /**
     * Returns a list of paged artifacts.
     *
     * @param arts         The artifacts.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged artifacts.
     */
    List<BlackboardArtifact> getPaged(List<? extends BlackboardArtifact> arts, SearchParams<?> searchParams) {
        Stream<? extends BlackboardArtifact> pagedArtsStream = arts.stream()
                .sorted(Comparator.comparing((art) -> art.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedArtsStream = pagedArtsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedArtsStream.collect(Collectors.toList());
    }

    long getTotalResultsCount(SearchParams<CommAccountsSearchParams> cacheKey, long currentPageSize) throws TskCoreException, NoCurrentCaseException {
        Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = BlackboardArtifact.Type.TSK_ACCOUNT;
        
        if ( (cacheKey.getStartItem() == 0) // offset is zero AND
            && ( (cacheKey.getMaxResultsCount() != null && currentPageSize < cacheKey.getMaxResultsCount()) // number of results is less than max
                || (cacheKey.getMaxResultsCount() == null)) ) { // OR max number of results was not specified
            return currentPageSize;
        } else {
            if (dataSourceId != null) {
                return blackboard.getArtifactsCount(artType.getTypeID(), dataSourceId);
            } else {
                return blackboard.getArtifactsCount(artType.getTypeID());
            }
        }  
    }    
    
    @NbBundle.Messages({"CommAccounts.name.text=Communication Accounts"})
    private SearchResultsDTO fetchCommAccountsDTOs(SearchParams<CommAccountsSearchParams> cacheKey) throws NoCurrentCaseException, TskCoreException, SQLException {

        // get current page of communication accounts results
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        Blackboard blackboard = skCase.getBlackboard();
        Account.Type type = cacheKey.getParamData().getType();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        List<BlackboardArtifact> allArtifacts = blackboard.getArtifacts(BlackboardArtifact.Type.TSK_ACCOUNT, 
                BlackboardAttribute.Type.TSK_ACCOUNT_TYPE, type.getTypeName(), dataSourceId,
			false); // ELTODO
        
        // get current page of artifacts
        List<BlackboardArtifact> pagedArtifacts = getPaged(allArtifacts, cacheKey);
        
        // Populate the attributes for paged artifacts in the list. This is done using one database call as an efficient way to
	// load many artifacts/attributes at once.
        blackboard.loadBlackboardAttributes(pagedArtifacts);
        List<RowDTO> fileRows = new ArrayList<>();
        
        DataArtifactDAO dataArtDAO = MainDAO.getInstance().getDataArtifactsDAO();
        BlackboardArtifactDAO.TableData tableData = dataArtDAO.createTableData(BlackboardArtifact.Type.TSK_ACCOUNT, pagedArtifacts);
        return new DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type.TSK_ACCOUNT, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), allArtifacts.size());
        
        /*
        while (rs.next()) {
            tempList.add(rs.getLong("artifact_obj_id")); // NON-NLS
        }

        long totalResultsCount = getTotalResultsCount(cacheKey, numResults);


        for (DataArtifact account : arts) {
            Account blackboardTag = (Account) account;
            
            String name = blackboardTag.getContent().getName();  // As a backup.
            try {
                name = blackboardTag.getArtifact().getShortDescription();
            } catch (TskCoreException ignore) {
                // it's a WARNING, skip
            }
            
            String contentPath;
            try {
                contentPath = blackboardTag.getContent().getUniquePath();
            } catch (TskCoreException ex) {
                contentPath = NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagNode.createSheet.unavail.text");
            }

            List<Object> cellValues = Arrays.asList(name,
                    null, // GVDTODO translation column
                    contentPath,
                    blackboardTag.getArtifact().getDisplayName(),
                    blackboardTag.getComment(),
                    blackboardTag.getUserName());

            fileRows.add(new BlackboardArtifactTagsRowDTO(
                    blackboardTag,
                    cellValues,
                    blackboardTag.getId()));
        }

        return new BaseSearchResultsDTO(BlackboardArtifactTagsRowDTO.getTypeIdForClass(), Bundle.ResultTag_name_text(), RESULT_TAG_COLUMNS, fileRows, 0, allAccounts.size());*/
    }
    
    /**
     * Handles fetching and paging of data for communication accounts.
     */
    public static class CommAccountFetcher extends DAOFetcher<CommAccountsSearchParams> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public CommAccountFetcher(CommAccountsSearchParams params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getCommAccountsDAO().getCommAcounts(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            CommAccountsSearchParams params = this.getParameters();
            String eventType = evt.getPropertyName();

            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * Even with the check above, it is still possible that the
                     * case will be closed in a different thread before this
                     * code executes. If that happens, it is possible for the
                     * event to have a null oldValue.
                     */
                    ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                    if (null != eventData
                            && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_ACCOUNT.getTypeID()) {
                        return true;
                    }
                } catch (NoCurrentCaseException notUsed) {
                    // Case is closed, do nothing.
                }
            }
            return false;
        }
    }
}
