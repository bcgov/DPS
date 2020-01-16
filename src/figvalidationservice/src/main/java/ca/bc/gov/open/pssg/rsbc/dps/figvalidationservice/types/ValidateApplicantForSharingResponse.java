package ca.bc.gov.open.pssg.rsbc.dps.figvalidationservice.types;

import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 
 *  model class for response messages for /validateApplicantForSharing requests
 * 
 * @author archana
 *
 */
@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidateApplicantForSharingResponse {

	private String validationResult;
	private int respCode;
	private String respMsg;
	
	public ValidateApplicantForSharingResponse(String validationResult,String respMsg, int respCode) {
		super();
		this.validationResult=validationResult;
		this.respMsg = respMsg;
		this.respCode = respCode;
	}

	public String getValidationResult() {
		return validationResult;
	}

	public void setValidationResult(String validationResult) {
		this.validationResult = validationResult;
	}

	public int getRespCode() {
		return respCode;
	}

	public void setRespCode(int respCode) {
		this.respCode = respCode;
	}

	public String getRespMsg() {
		return respMsg;
	}

	public void setRespMsg(String respMsg) {
		this.respMsg = respMsg;
	}

}

