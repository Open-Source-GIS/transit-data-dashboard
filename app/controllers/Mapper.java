/**
 * Attempt to automatically match loaded data sets to metro areas and feeds
 */

package controllers;

import play.*;
import play.mvc.*;
import play.db.jpa.JPA;
import javax.persistence.Query;
import models.*;
import java.util.List;
import java.util.ArrayList;
import java.math.BigInteger;

public class Mapper extends Controller {
    /**
     * Attempt to automatch feeds to agencies by URL.
     */
    public static void mapFeedsToAgenciesByUrl () {
        long feedId;
        long agencyId;
        String[] report = new String[4];
        NtdAgency agency;
        GtfsFeed feed;

        // just to give the user an idea of percentages
        long feedCount = GtfsFeed.count();

        // GtfsFeed on left of left join because we're mapping many feeds to one agency
        // many agencies to one feed is more difficult
        Query q = JPA.em().createNativeQuery(
                                       "SELECT f.id AS feedid, a.id AS agencyid, " + 
                                       "a.name AS agencyname, f.agencyName as feedname, " +
                                       "a.url AS agencyurl, f.agencyUrl AS feedurl " + 
                                       "FROM GtfsFeed f INNER JOIN NtdAgency a " +
                                       "ON (regexp_replace(f.agencyUrl, " + 
                                       // that regular expression strips the protocol, 
                                       // strips pathinfo,
                                       // and strips www. to get a hopefully LCD domain name
                                       "'(?:https?://)?(?:www\\.)?([a-zA-Z0-9\\-_\\.]*)(?:/.*)?'," +
                                       "'\\1') = " + 
                                       "regexp_replace(a.url," + 
                                       "'(?:https?://)?(?:www\\.)?([a-zA-Z0-9\\-_\\.]*)(?:/.*)?'," +
                                       "'\\1'));"
                                       );
        
        List<Object[]> results = q.getResultList();

        List<String[]> matches = new ArrayList<String[]>();

        for (Object[] result : results) {
            // postgres query results are bigintegers in JPA, but longs in Hibernate
            feedId = ((BigInteger) result[0]).longValue();
            agencyId = ((BigInteger) result[1]).longValue();

            Logger.debug("Agency: %s, feed: %s", agencyId, feedId);

            agency = NtdAgency.findById(agencyId);
            feed = GtfsFeed.findById(feedId);

            if (agency.feeds.contains(feed)) {
                // don't bother to report or add
                continue;
            }

            report = new String[4];
            report[0] = (String) result[2];
            report[1] = (String) result[3];
            report[2] = (String) result[4];
            report[3] = (String) result[5];
            matches.add(report);

            agency.feeds.add(feed);

            agency.save();
        }

        render(matches, feedCount);
    }

    /**
     * Clear ALL agency to feed mappings. This should be protected
     */
    public static void clearAllAgencyFeedMappings () {
        List<NtdAgency> agencies = NtdAgency.findAll();
        for (NtdAgency agency : agencies) {
            // remove every feed from this agency
            agency.feeds.removeAll(agency.feeds);
            agency.save();
        }
    }

    /**
     * Attempt to spatially assign metro areas to agencies
     */
    public static void mapAgenciesToMetroAreasSpatially () {
        String[] report;
        NtdAgency agency;
        MetroArea metro;
        long agencyId;
        long metroId;
        long agencyCount = NtdAgency.count();

        // TODO: how does this query behave when an agency touches multiple metros?
        String qs =
            "SELECT a.id AS agencyid, m.id AS metroid, a.name AS agencyname, " + 
            "       a.url AS agencyurl, m.name AS metroname " +
            "  FROM (NtdAgency a " +
            // use inner join; agencies with no feeds should not be considered
            // TODO: this shouldn't depend on how hibernate structures its tables
            "  INNER JOIN (SELECT nf.ntdagency_id AS id, ST_Union(f.the_geom) AS the_geom " +
            "             FROM NtdAgency_GtfsFeed nf " +
            "             LEFT JOIN GtfsFeed f ON (nf.feeds_id = f.id) " +
            "             GROUP BY nf.ntdagency_id) g" +
            "  USING (id)) " +
            // agencies outside a metro area cannot be mapped: use inner join
            "  INNER JOIN MetroArea m " +
            "  ON (ST_Intersects(g.the_geom, m.the_geom))";

        Query q = JPA.em().createNativeQuery(qs);
        List<Object[]> results = q.getResultList();
        List<String[]> matches = new ArrayList<String[]>();

        for (Object[] result : results) {
            report = new String[3];
     
            agencyId = ((BigInteger) result[0]).longValue();
            metroId = ((BigInteger) result[1]).longValue();

            Logger.debug("agency: %s, metro: %s", agencyId, metroId);
            agency = NtdAgency.findById(agencyId);
            metro = MetroArea.findById(metroId);

            // don't break ones that have already been fixed
            if (agency.metroArea != null)
                continue;

            report[0] = (String) result[2];
            report[1] = (String) result[3];
            report[2] = (String) result[4];

            matches.add(report);

            agency.metroArea = metro;
            agency.save();
        }

        render(matches, agencyCount);
    }   

    /**
     * DANGER: clear all agency to metro area mappings
     */
    public static void clearAllAgencyMetroAreaMappings () {
        List<NtdAgency> agencies = NtdAgency.findAll();
        for (NtdAgency agency : agencies) {
            agency.metroArea = null;
            agency.save();
        }
    }
}
            
            
            
            