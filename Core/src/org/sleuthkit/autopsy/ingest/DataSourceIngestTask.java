/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

/**
 * A data source level ingest task that will be executed by an ingest thread
 * using a given ingest job pipeline.
 */
final class DataSourceIngestTask extends IngestTask {

    /**
     * Constructs a data source level ingest task that will be executed by an
     * ingest thread using a given ingest job pipeline.
     *
     * @param ingestJobPipeline The ingest job pipeline to use to complete the
     *                          task.
     */
    DataSourceIngestTask(IngestJobPipeline ingestJobPipeline) {
        super(ingestJobPipeline);
    }

    /**
     * Executes this task by passing it to the given ingest job pipeline.
     *
     * @param ingestJobPipeline The ingest job pipeline.
     *
     * @throws InterruptedException This exception is thrown if the thread
     *                              executing the task is interrupted while
     *                              blocked.
     */
    @Override
    void execute(IngestJobPipeline ingestJobPipeline) throws InterruptedException {
        ingestJobPipeline.process(this);
    }

}
