/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationParseResult;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.DataSource;

/**
 * Gathers summary data about Geolocation information for a data source.
 */
@Messages({
    "GeolocationSummary_cities_noRecordFound=Other"
})
public class WhereUsedSummary implements DefaultArtifactUpdateGovernor {

    /**
     * A count of hits for a particular city.
     */
    public class CityRecordCount {

        private final CityRecord cityRecord;
        private final int count;

        /**
         * Main constructor.
         *
         * @param cityRecord The record for the city including name, country,
         * and location.
         * @param count The number of hits in proximity to that city.
         */
        CityRecordCount(CityRecord cityRecord, int count) {
            this.cityRecord = cityRecord;
            this.count = count;
        }

        /**
         * @return The record for the city including name, country, and
         * location.
         */
        public CityRecord getCityRecord() {
            return cityRecord;
        }

        /**
         * @return The number of hits in proximity to that city.
         */
        public int getCount() {
            return count;
        }
    }

    /**
     * Returned data providing counts of most common cities seen and most recent
     * cities seen.
     */
    public static class CityData {

        private final CityCountsList mostCommon;
        private final CityCountsList mostRecent;
        private final Long mostRecentSeen;

        /**
         * Main constructor.
         *
         * @param mostCommon The list of most common cities seen.
         * @param mostRecent The list of most recent cities seen.
         * @param mostRecentSeen
         */
        CityData(CityCountsList mostCommon, CityCountsList mostRecent, Long mostRecentSeen) {
            this.mostCommon = mostCommon;
            this.mostRecent = mostRecent;
            this.mostRecentSeen = mostRecentSeen;
        }

        public CityCountsList getMostCommon() {
            return mostCommon;
        }

        public CityCountsList getMostRecent() {
            return mostRecent;
        }

        public Long getMostRecentSeen() {
            return mostRecentSeen;
        }
    }

    public static class CityCountsList {

        private final List<CityRecordCount> counts;
        private final int otherCount;

        public CityCountsList(List<CityRecordCount> counts, int otherCount) {
            this.counts = Collections.unmodifiableList(new ArrayList<>(counts));
            this.otherCount = otherCount;
        }

        public List<CityRecordCount> getCounts() {
            return counts;
        }

        public int getOtherCount() {
            return otherCount;
        }
    }

    // taken from GeoFilterPanel: all of the GPS artifact types.
    @SuppressWarnings("deprecation")
    private static final List<ARTIFACT_TYPE> GPS_ARTIFACT_TYPES = Arrays.asList(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACK,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF
    );

    // all GPS types
    private static final Set<Integer> GPS_ARTIFACT_TYPE_IDS = GPS_ARTIFACT_TYPES.stream()
            .map(artifactType -> artifactType.getTypeID())
            .collect(Collectors.toSet());

    private final SleuthkitCaseProvider provider;
    private final java.util.logging.Logger logger;
    private final SupplierWithException<ClosestCityMapper, IOException> cityMapper;

    public interface SupplierWithException<T, E extends Throwable> {

        T get() throws E;
    }

    /**
     * Main constructor.
     */
    public WhereUsedSummary() {
        this(() -> ClosestCityMapper.getInstance(), SleuthkitCaseProvider.DEFAULT, Logger.getLogger(WhereUsedSummary.class.getName()));
    }

    public WhereUsedSummary(SupplierWithException<ClosestCityMapper, IOException> cityMapper, SleuthkitCaseProvider provider, java.util.logging.Logger logger) {
        this.cityMapper = cityMapper;
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * @return Returns all the geolocation artifact types.
     */
    public List<ARTIFACT_TYPE> getGeoTypes() {
        return GPS_ARTIFACT_TYPES;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return GPS_ARTIFACT_TYPE_IDS;
    }

    private boolean greaterThanOrEqual(Long minTime, Long time) {
        if ((minTime == null) || (time != null && time >= minTime)) {
            return true;
        } else {
            return false;
        }
    }

    private static final Pair<Integer, Integer> EMPTY_COUNT = Pair.of(0, 0);

    // left is total count, right is count within time range
    private Pair<Integer, Integer> getCounts(List<Waypoint> points, Long minTime) {

        if (points == null) {
            return EMPTY_COUNT;
        }

        return points.stream().reduce(
                EMPTY_COUNT,
                (total, w) -> Pair.of(total.getLeft() + 1, total.getRight() + (greaterThanOrEqual(minTime, w.getTimestamp()) ? 1 : 0)),
                (pair1, pair2) -> Pair.of(pair1.getLeft() + pair2.getLeft(), pair1.getRight() + pair2.getRight()));
    }

    /**
     * Get this list of hits per city where the list is sorted descending by
     * number of found hits (i.e. most hits is first index).
     *
     * @param dataSource The data source.
     * @return The sorted list.
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    public CityData getCityCounts(DataSource dataSource, int daysCount, int maxCount)
            throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException, IOException {

        ClosestCityMapper closestCityMapper = ClosestCityMapper.getInstance();

        List<Waypoint> dataSourcePoints = getPoints(dataSource);

        Map<CityRecord, List<Waypoint>> allCityPoints = new HashMap<>();
        List<Waypoint> others = new ArrayList<>();
        Long mostRecent = null;

        for (Waypoint pt : dataSourcePoints) {
            CityRecord city = closestCityMapper.findClosest(new CityRecord(null, null, pt.getLatitude(), pt.getLongitude()));
            Long curTime = pt.getTimestamp();
            if (curTime != null && (mostRecent == null || curTime > mostRecent)) {
                mostRecent = curTime;
            }

            if (city == null) {
                others.add(pt);
            } else {
                List<Waypoint> cityPoints = allCityPoints.get(city);
                if (cityPoints == null) {
                    cityPoints = new ArrayList<>();
                    allCityPoints.put(city, cityPoints);
                }

                cityPoints.add(pt);
            }
        }

        final Long mostRecentTime = mostRecent;

        // pair left is total count and right is count within range (or mostRecent is null)
        Map<CityRecord, Pair<Integer, Integer>> allCityCounts = allCityPoints.entrySet().stream()
                .collect(Collectors.toMap((e) -> e.getKey(), (e) -> getCounts(e.getValue(), mostRecentTime)));

        List<CityRecordCount> mostCommonCounts = allCityCounts.entrySet().stream()
                .map(e -> new CityRecordCount(e.getKey(), e.getValue().getLeft()))
                .sorted((a, b) -> -Integer.compare(a.getCount(), b.getCount()))
                .limit(maxCount)
                .collect(Collectors.toList());

        List<CityRecordCount> mostRecentCounts = allCityCounts.entrySet().stream()
                .map(e -> new CityRecordCount(e.getKey(), e.getValue().getRight()))
                .sorted((a, b) -> -Integer.compare(a.getCount(), b.getCount()))
                .limit(maxCount)
                .collect(Collectors.toList());

        Pair<Integer, Integer> otherCounts = getCounts(others, mostRecentTime);
        int otherMostCommonCount = otherCounts.getLeft();
        int otherMostRecentCount = otherCounts.getRight();

        return new CityData(
                new CityCountsList(mostCommonCounts, otherMostCommonCount),
                new CityCountsList(mostRecentCounts, otherMostRecentCount),
                mostRecentTime);
    }

    /**
     * Fetches all GPS data for the data source from the current case.
     *
     * @param dataSource The data source.
     * @return The GPS data pertaining to the data source.
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    private List<Waypoint> getPoints(DataSource dataSource) throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException {
        // make asynchronous callback synchronous (the callback nature will be handled in a different level)
        // see the following: https://stackoverflow.com/questions/20659961/java-synchronous-callback
        final BlockingQueue<GeoLocationParseResult<Waypoint>> asyncResult = new ArrayBlockingQueue<>(1);

        final WaypointBuilder.WaypointFilterQueryCallBack callback = (result) -> {
            try {
                asyncResult.put(result);
            } catch (InterruptedException ignored) {
                // ignored cancellations
            }
        };

        WaypointBuilder.getAllWaypoints(provider.get(),
                Arrays.asList(dataSource),
                GPS_ARTIFACT_TYPES,
                true,
                -1,
                false,
                callback);

        GeoLocationParseResult<Waypoint> result;

        result = asyncResult.take();

        if (result.isSuccessfullyParsed()) {
            return result.getItems();
        } else {
            return Collections.emptyList();
        }
    }
}
