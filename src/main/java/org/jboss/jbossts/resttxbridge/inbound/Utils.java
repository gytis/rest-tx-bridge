package org.jboss.jbossts.resttxbridge.inbound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MultivaluedMap;

import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.spi.HttpRequest;

/**
 * 
 * @author Gytis Trikleris
 * 
 */
public final class Utils {

    public static final String LINK_REGEX = "<(.*?)>.*rel=\"(.*?)\"";

    public static final Pattern LINK_PATTERN = Pattern.compile(LINK_REGEX);
    
    private static final Logger LOG = Logger.getLogger(Utils.class);

    /**
     * Extracts participant id from http request.
     * 
     * @param request
     * @return String participant id if path parameter <code>participantId</code> is set and null otherwise.
     */
    public static String getParticipantId(HttpRequest request) {
        System.out.println("Utils.getParticipantId()");

        if (request == null) {
            return null;
        }

        return request.getUri().getPathParameters().get("participantId").get(0);
    }

    /**
     * Returns transaction id mapped with given participant.
     * 
     * @param participantId
     * @return String transaction URL if participant has transaction mapped and null otherwise.
     */
    public static String getTransactionUrl(String participantId) {
        System.out.println("Utils.getTransactionUrl(participantId=" + participantId + ")");

        if (participantId == null) {
            return null;
        }

        return InboundBridgeManager.getParticipantTransaction(participantId);
    }

    /**
     * Extracts transaction URL from HTTP request and returns it.
     * 
     * @param request
     * @return String transaction URL for participants enlistment if required link exists and null otherwise.
     */
    public static String getTransactionUrl(HttpRequest request) {
        System.out.println("Utils.getTransactionUrl()");

        if (request == null) {
            return null;
        }

        String transactionUrl = null;
        Map<String, String> links = extractLinksFromHttpHeaders(request.getHttpHeaders().getRequestHeaders());

        if (links.get(TxSupport.PARTICIPANT_LINK) != null) {
            transactionUrl = links.get(TxSupport.PARTICIPANT_LINK);
        } else if (links.get("coordinator") != null) {
            transactionUrl = getParticipantLink(links.get("coordinator"));
        }

        return transactionUrl;
    }

    /**
     * Makes HEAD request to rest-tx coordinator and extracts participant enlistment link.
     * 
     * @param coordinatorLink
     * @return String transaction URL for participants enlistment.
     */
    public static String getParticipantLink(String coordinatorLink) {
        System.out.println("Utils.getParticipantLink()");

        String participantLink = null;
        try {
            ClientResponse<?> clientResponse = new ClientRequest(coordinatorLink).head();

            participantLink = extractParticipantLinkFromHttpHeaders(clientResponse.getHeaders());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return participantLink;
    }

    /**
     * Extracts participant enlistment link from HTTP headers.
     * 
     * @param headers
     * @return String transaction URL for participants enlistment.
     */
    public static String extractParticipantLinkFromHttpHeaders(MultivaluedMap<String, String> headers) {
        System.out.println("Utils.extractParticipantLinkFromHttpHeaders()");

        String participantLink = null;
        Map<String, String> links = extractLinksFromHttpHeaders(headers);

        if (links.get(TxSupport.PARTICIPANT_LINK) != null) {
            participantLink = links.get(TxSupport.PARTICIPANT_LINK);
        }

        return participantLink;
    }

    /**
     * Extracts links from HTTP headers.
     * 
     * @param headers
     * @return Map containing links. Map's key is link's relation and map's value is link's location.
     */
    public static Map<String, String> extractLinksFromHttpHeaders(MultivaluedMap<String, String> headers) {
        System.out.println("Utils.extractLinksFromHttpHeaders()");

        Map<String, String> links = new HashMap<String, String>();
        List<String> linkHeaders = headers.get("link");

        if (linkHeaders == null) {
            linkHeaders = headers.get("Link");
        }

        if (linkHeaders != null) {
            for (String link : linkHeaders) {
                String[] lhs = link.split(","); // links are separated by a comma

                for (String lnk : lhs) {
                    Matcher m = LINK_PATTERN.matcher(lnk);
                    if (m.find() && m.groupCount() > 1) {
                        links.put(m.group(2), m.group(1));
                    }
                }
            }
        }

        return links;
    }

}
