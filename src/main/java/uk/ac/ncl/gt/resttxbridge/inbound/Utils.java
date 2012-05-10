package uk.ac.ncl.gt.resttxbridge.inbound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.jbossts.star.util.TxSupport;
import org.jboss.resteasy.spi.HttpRequest;

public final class Utils {

    public static String LINK_REGEX = "<(.*?)>.*rel=\"(.*?)\"";

    public static Pattern LINK_PATTERN = Pattern.compile(LINK_REGEX);
    
    
    public static String getParticipantId(HttpRequest request) {
        System.out.println("Helpers.getParticipantId()");
        
        if (request == null) {
            return null;
        }
        
        return request.getUri().getPathParameters().get("participantId").get(0);
    }
    
    
    public static String getTransactionUrl(String participantId) {
        System.out.println("Helpers.getTransactionUrl(participantId=" + participantId + ")");
        
        if (participantId == null) {
            return null;
        }
        
        return InboundBridgeManager.getParticipantTransaction(participantId);
    }
    
    
    public static String getTransactionUrl(HttpRequest request) {
        System.out.println("Helpers.getTransactionUrl()");

        if (request == null) {
            return null;
        }
        
        Map<String, String> links = new HashMap<String, String>();
        List<String> linkHeaders = request.getHttpHeaders().getRequestHeader("link");

        if (linkHeaders == null) {
            linkHeaders = request.getHttpHeaders().getRequestHeader("Link");
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

        if (links.get("coordinator") != null) {
            return links.get("coordinator");
        } else if (links.get(TxSupport.PARTICIPANT_LINK) != null) {
            return links.get(TxSupport.PARTICIPANT_LINK);
        }
        
        return null;
    }
    
}
