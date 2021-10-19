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

import java.util.Objects;

/**
 * Key for accessing data about file sizeFilter from the DAO.
 */
public class FileTypeSizeSearchParams extends BaseSearchParams {

    public enum FileSizeFilter {
        SIZE_50_200(0, "SIZE_50_200", "50 - 200MB"), //NON-NLS
        SIZE_200_1000(1, "SIZE_200_1GB", "200MB - 1GB"), //NON-NLS
        SIZE_1000_(2, "SIZE_1000+", "1GB+"); //NON-NLS
        private final int id;
        private final String name;
        private final String displayName;

        private FileSizeFilter(int id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public String getName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    private final FileSizeFilter sizeFilter;
    private final Long dataSourceId;
 
    public FileTypeSizeSearchParams(FileSizeFilter sizeFilter, Long dataSourceId) {
        this.sizeFilter = sizeFilter;
        this.dataSourceId = dataSourceId;
    }

    public FileTypeSizeSearchParams(FileSizeFilter sizeFilter, Long dataSourceId, long startItem, Long maxResultsCount) {
        super(startItem, maxResultsCount);
        this.sizeFilter = sizeFilter;
        this.dataSourceId = dataSourceId;
    }

    public FileSizeFilter getSizeFilter() {
        return sizeFilter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.sizeFilter);
        hash = 23 * hash + Objects.hashCode(this.dataSourceId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileTypeSizeSearchParams other = (FileTypeSizeSearchParams) obj;
        if (!Objects.equals(this.sizeFilter, other.sizeFilter)) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }

}