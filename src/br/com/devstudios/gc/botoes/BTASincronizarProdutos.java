package br.com.devstudios.gc.botoes;

import java.sql.ResultSet;
import java.util.Collection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sankhya.util.JdbcUtils;

import br.com.devstudios.gc.dao.ConfiguracaoDAO;
import br.com.devstudios.gc.services.CallService;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.bmp.PersistentLocalEntity;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.FinderWrapper;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.MGECoreParameter;


public class BTASincronizarProdutos implements AcaoRotinaJava {

	@Override
	public void doAction(ContextoAcao ctx) throws Exception {
		JdbcWrapper jdbc = null; 
		EntityFacade dwfEntityFacade = EntityFacadeFactory.getDWFFacade(); 
		jdbc = dwfEntityFacade.getJdbcWrapper();
		NativeSql sql = (NativeSql) null;
		ResultSet rs = (ResultSet) null;
		int cadEnv = 0;
		String bd="";
		try {
			
			String param = (String) MGECoreParameter.getParameter("USOPRODAPPINV");
			String[] listaUsoProd = param.split(",");
			
			for (String usoprod : listaUsoProd) {
				
				sql = new NativeSql(jdbc);
//				sql.appendSql("SELECT CODPROD, DESCRPROD, nvl(MARCA,'SEM MARCA') as MARCA, REFERENCIA, CODGRUPOPROD, CODVOL FROM TGFPRO"
//						+ " where ativo='S' and usoprod in ('R','V') and REFERENCIA is not null");
				sql.appendSql("SELECT CODPROD, DESCRPROD, MARCA, REFERENCIA, CODGRUPOPROD, CODVOL FROM("+
			    "SELECT P.CODPROD,P.DESCRPROD,UPPER(NVL(I.MARCA,'SEM MARCA')) AS MARCA,NVL( "+
				"(SELECT CODBARRA FROM TGFBAR WHERE CODVOL=P.CODVOL AND CODPROD=P.CODPROD AND ROWNUM=1),(SELECT CODBARRA FROM TGFVOA WHERE CODVOL=P.CODVOL AND CODPROD=P.CODPROD AND ROWNUM=1)) AS REFERENCIA,"+
			    "P.CODGRUPOPROD,P.CODVOL FROM TGFPRO P JOIN AD_INTMARCA I ON (I.ID=P.AD_MARCA) WHERE P.ATIVO='S' AND P.USOPROD IN ('"+usoprod+"')) X "+
				"WHERE X.REFERENCIA IS NOT NULL");
				
				rs = sql.executeQuery();
				while (rs.next()) {
					DynamicVO configVO = ConfiguracaoDAO.get();
					String uri = configVO.asString("URL");
					CallService service = new CallService();
					
					JSONObject body = new JSONObject();
					body.put("id", rs.getString("CODPROD"));
					body.put("name", rs.getString("DESCRPROD"));
					body.put("brand", rs.getString("MARCA"));
					body.put("barcode", rs.getString("REFERENCIA"));
					body.put("unit_id", rs.getString("CODVOL"));
					body.put("category_id", rs.getString("CODGRUPOPROD"));
					body.put("active", true);
					
					JSONArray unidadesAlternativasArray = new JSONArray();
					
					String queryPk = "CODPROD="+rs.getString("CODPROD").toString();
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
					
					service.fire();
					cadEnv++;
					bd=bd+body.toString();
				}
				
			}
			
			ctx.setMensagemRetorno("Processo concluído!<br>"+cadEnv+" cadastros enviado(s)!");
			
			
			//ctx.setMensagemRetorno(""+bd);
		} catch (Exception e1) {
			e1.printStackTrace();
			ctx.mostraErro(e1.getMessage());
		} finally {
			JdbcUtils.closeResultSet(rs);
			NativeSql.releaseResources(sql);
		}
		
	}
}