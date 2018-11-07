/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.events;

import java.io.Serializable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.TimelineEvent;

/**
 * An AutopsyEvent broadcast when a TimelineEvent is added to the case.
 */
public class EventAddedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(EventAddedEvent.class.getName());

    private transient TimelineEvent addedEvent;
    private final long addedEventID;

    public EventAddedEvent(org.sleuthkit.datamodel.TimelineManager.EventAddedEvent event) {
        super(Case.Events.EVENT_ADDED.name(), null, event.getAddedEvent().getEventID());
        addedEvent = event.getAddedEvent();
        addedEventID = addedEvent.getEventID();
    }

    /**
     * Gets the TimelineEvent that was added.
     *
     * @return The TimelineEvent or null if there is an error retrieving the
     *         TimelineEvent.
     */
    @Override
    public TimelineEvent getNewValue() {
        /**
         * The addedEvent field is set in the constructor, but it is transient
         * so it will become null when the event is serialized for publication
         * over a network. Doing a lazy load of the TimelineEvent object
         * bypasses the issues related to the serialization and de-serialization
         * of TimelineEvent objects and may also save database round trips from
         * other nodes since subscribers to this event are often not interested
         * in the event data.
         */
        if (null != addedEvent) {
            return addedEvent;
        }
        try {
            addedEvent = Case.getCurrentCaseThrows().getSleuthkitCase().getTimelineManager().getEventById(addedEventID);
            return addedEvent;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex); //NON-NLS
            return null;
        }
    }

    public TimelineEvent getAddedEvent() {
        return getNewValue();
    }
}
