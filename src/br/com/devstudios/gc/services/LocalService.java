package br.com.devstudios.gc.services;

import java.math.BigDecimal;

import org.json.JSONObject;
import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.sankhya.jape.vo.DynamicVO;

public class LocalService {
	public static String handle(DynamicVO localVO) throws Exception {
		DynamicVO configVO = ConfiguracaoDAO.get();
		String uri = configVO.asString("URL");
		CallService service = new CallService();
		
		JSONObject body = new JSONObject();
		body.put("id", localVO.asBigDecimal("CODLOCAL"));
		body.put("name", localVO.asString("DESCRLOCAL"));
		if(localVO.asBigDecimal("CODLOCALPAI").compareTo(BigDecimal.ZERO) == 1) {
			body.put("father_id", localVO.asBigDecimal("CODLOCALPAI"));
		}
		body.put("active", true);
		
		service.setBody(body.toString());
		
		service.setMethod("POST");
		service.setUri(uri + "/stock-locations");
		
		return service.fire();
	}
}
