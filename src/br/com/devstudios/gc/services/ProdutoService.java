package br.com.devstudios.gc.services;

import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;
import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;

public class ProdutoService {
	public static String handle(DynamicVO produtoVO) throws Exception {
		DynamicVO configVO = ConfiguracaoDAO.get();
		String uri = configVO.asString("URL");
		CallService service = new CallService();
		
		JSONObject body = new JSONObject();
		body.put("id", produtoVO.asBigDecimal("CODPROD"));
		body.put("name", produtoVO.asString("DESCRPROD"));
		body.put("brand", produtoVO.asString("MARCA") == null ? "SEM MARCA" : produtoVO.asString("MARCA"));
		if(!(produtoVO.asString("REFERENCIA") == null)) {
			body.put("barcode", produtoVO.asString("REFERENCIA"));
		}
		body.put("unit_id", produtoVO.asString("CODVOL"));
		body.put("category_id", produtoVO.asBigDecimal("CODGRUPOPROD"));
		body.put("active", true);
		
		JSONArray unidadesAlternativasArray = new JSONArray();
		
		String queryPk = "CODPROD="+produtoVO.asBigDecimal("CODPROD").toString();
	    FinderWrapper f = new FinderWrapper("AD_BARINVENT", queryPk);
	       
	    EntityFacade dwf = EntityFacadeFactory.getDWFFacade();  
        Collection<PersistentLocalEntity> rPLES = dwf.findByDynamicFinder(f);
        for(PersistentLocalEntity rPLE : rPLES) {
        	
        	EntityVO rEVO = rPLE.getValueObject();
        	DynamicVO rVO = (DynamicVO) rEVO;
        	JSONObject unidadeAlternativa = new JSONObject();
        	
        	unidadeAlternativa.put("unit_id", rVO.asString("CODVOL"));
        	unidadeAlternativa.put("barcode", rVO.asString("CODBARRA"));
        	unidadeAlternativa.put("divide_or_multiply", rVO.asString("DIVIDEMULTIPLICA"));
        	unidadeAlternativa.put("quantity", rVO.asBigDecimal("QUANTIDADE"));

        	unidadesAlternativasArray.put(unidadeAlternativa);
        }
		
        body.put("units", unidadesAlternativasArray);
		
		service.setBody(body.toString());
		
		service.setMethod("POST");
		service.setUri(uri + "/products");
		
		return service.fire();
	}
}
