package ca.bc.gov.open.pssg.rsbc.spd.notification.worker.document;

import ca.bc.gov.open.pssg.rsbc.spd.notification.worker.FigaroServiceConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 *
 * Represents the DPS Document Response
 *
 * @author carolcarpenterjustice
 *
 */
@JacksonXmlRootElement(localName = "dpsDocumentResponse")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class DpsDocumentResponse {

    private String guid;
    private int respCode;
    private String respMsg;

    private DpsDocumentResponse(int respCode, String respMsg) {
        this.respCode = respCode;
        this.respMsg = respMsg;
    }

    private DpsDocumentResponse(String guid, int respCode, String respMsg) {
        this(respCode, respMsg);
        this.guid = guid;
    }


    public String getGuid() { return guid; }

    public void setGuid(String guid) { this.guid = guid; }

    public int getRespCode() {
        return respCode;
    }

    public String getRespMsg() {
        return respMsg;
    }

    public static DpsDocumentResponse ErrorResponse(String validationResult) {
        return new DpsDocumentResponse(
                FigaroServiceConstants.FIGARO_SERVICE_FAILURE_CD,
                FigaroServiceConstants.FIGARO_SERVICE_BOOLEAN_FALSE);
    }

    public static DpsDocumentResponse SuccessResponse(String guid, String respCodeStr, String respMsg) {

        return new DpsDocumentResponse(guid, Integer.parseInt(respCodeStr), respMsg);
    }
}
