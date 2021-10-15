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

import java.util.List;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Search results for data artifacts.
 */
public class DataArtifactTableSearchResultsDTO extends BaseSearchResultsDTO {

    private static final String TYPE_ID = "DATA_ARTIFACT";

    private final BlackboardArtifact.Type artifactType;

    public DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type artifactType, List<ColumnKey> columns, List<RowDTO> items) {
        super(TYPE_ID, artifactType.getDisplayName(), columns, items);
        this.artifactType = artifactType;
    }

    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }
}